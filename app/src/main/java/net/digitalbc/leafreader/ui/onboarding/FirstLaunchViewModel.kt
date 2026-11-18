package net.digitalbc.leafreader.ui.onboarding

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.digitalbc.leafreader.data.settings.FirstLaunchState
import net.digitalbc.leafreader.data.settings.FirstLaunchStateStore
import net.digitalbc.leafreader.ui.library.FileImportState

internal enum class FirstLaunchPresentation { NONE, PICKER, IMPORT, SETUP }
internal enum class FirstLaunchError { SAVE, PREPARE, IMPORT, INTERRUPTED }
internal data class FirstLaunchUi(
    val destination: FirstLaunchState? = null,
    val busy: Boolean = true,
    val presentation: FirstLaunchPresentation = FirstLaunchPresentation.NONE,
    val error: FirstLaunchError? = null,
    val setupId: Long = 0,
) {
    val showSetup: Boolean get() = destination == FirstLaunchState.OPTIONAL_JELLYFIN &&
        !busy && presentation == FirstLaunchPresentation.SETUP
}

/** Owns only onboarding intent. Archive import and authentication stay in their existing owners. */
internal class FirstLaunchViewModel(
    private val saved: SavedStateHandle,
    private val store: FirstLaunchStateStore,
    private val readableIds: suspend () -> Set<Long>,
    private val connected: () -> Boolean,
    private val startImport: (List<Uri>) -> Long?,
    private val outcomes: StateFlow<FileImportState?>,
    private val io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val serverConnector: suspend (String, Boolean, String, String) -> Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth> =
        { _, _, _, _ -> Result.failure(IllegalStateException("Setup is unavailable")) },
) : ViewModel() {
    private val _state = MutableStateFlow(FirstLaunchUi(presentation = saved.get<String>(PHASE)
        ?.let(FirstLaunchPresentation::valueOf) ?: FirstLaunchPresentation.NONE))
    val state = _state.asStateFlow()
    private var connection: Deferred<Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth>>? = null
    private val _connecting = MutableStateFlow(false)
    val connecting = _connecting.asStateFlow()
    private val _connectionFailed = MutableStateFlow(false)
    val connectionFailed = _connectionFailed.asStateFlow()

    init {
        viewModelScope.launch {
            val destination = try { withContext(io) { store.initialize() } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                // Startup already recorded the error. An ambiguous marker never blocks reading.
                FirstLaunchState.COMPLETE
            }
            _state.value = FirstLaunchUi(destination, busy = true,
                presentation = saved.get<String>(PHASE)?.let { FirstLaunchPresentation.valueOf(it) }
                    ?: FirstLaunchPresentation.NONE,
                setupId = saved[SETUP_ID] ?: 0L)
            val pending = withContext(io) { store.pendingOperation() }
            if (destination != FirstLaunchState.COMPLETE && pending != null) {
                if (pending.first == "IMPORT") {
                    saved[BASELINE] = pending.second.toLongArray()
                    _state.value = _state.value.copy(presentation = FirstLaunchPresentation.IMPORT)
                } else if (pending.first == "SETUP") {
                    _state.value = _state.value.copy(presentation = FirstLaunchPresentation.SETUP)
                }
            }
            if (pending == null && _state.value.presentation in
                setOf(FirstLaunchPresentation.SETUP, FirstLaunchPresentation.IMPORT)) {
                // A saved Activity can predate a durable cancellation/Back/completion write.
                clearIntent()
                _state.value = _state.value.copy(presentation = FirstLaunchPresentation.NONE)
            }
            _state.value = _state.value.copy(busy = false)
            if (destination == FirstLaunchState.COMPLETE) clearIntent()
            else when (_state.value.presentation) {
                FirstLaunchPresentation.IMPORT -> {
                    // A live ViewModel still owns this batch after rotation. With a new process,
                    // reconcile readable retained rows locally; never infer success from selection.
                    if (outcomes.value == null || outcomes.value?.operationId != saved.get<Long>(BATCH)) recoverImport()
                }
                FirstLaunchPresentation.SETUP -> if (withContext(io) { connected() }) complete()
                else -> Unit
            }
            saved.get<String>(TARGET)?.let { save(FirstLaunchState.valueOf(it)) }
            outcomes.collect(::acceptOutcome)
        }
    }

    fun chooseFiles() {
        if (!available()) return
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                saved[BASELINE] = withContext(io) { readableIds().toLongArray() }
                saved[LAUNCHED] = false
                present(FirstLaunchPresentation.PICKER)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { returnToCard(FirstLaunchError.IMPORT) }
        }
    }

    fun claimPicker(): Boolean {
        if (_state.value.presentation != FirstLaunchPresentation.PICKER || saved.get<Boolean>(LAUNCHED) == true) return false
        saved[LAUNCHED] = true
        return true
    }

    fun filesSelected(uris: List<Uri>) {
        if (_state.value.presentation != FirstLaunchPresentation.PICKER) return
        if (uris.isEmpty()) { returnToCard(); return }
        // Store intent before dispatch. No URIs, paths, hosts, passwords or tokens enter saved state.
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val baseline = (saved.get<LongArray>(BASELINE) ?: longArrayOf()).toSet()
                withContext(io) { store.beginImport(baseline) }
                present(FirstLaunchPresentation.IMPORT)
                saved[BATCH] = startImport(uris)
                acceptOutcome(outcomes.value)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { returnToCard(FirstLaunchError.PREPARE) }
        }
    }

    private fun acceptOutcome(result: FileImportState?) {
        if (result == null) return
        if (!_state.value.busy && _state.value.presentation == FirstLaunchPresentation.IMPORT &&
            result.operationId == saved.get<Long>(BATCH) && result.finished) {
            if (result.importedComicIds.isNotEmpty()) complete()
            else returnToCard(if (result.cancelled) null else FirstLaunchError.IMPORT)
        }
    }

    fun pickerUnavailable() = returnToCard(FirstLaunchError.IMPORT)

    fun optionalJellyfin() = transition(FirstLaunchState.OPTIONAL_JELLYFIN)
    fun back() = transition(FirstLaunchState.WELCOME)
    fun skip() = transition(FirstLaunchState.COMPLETE)

    fun openSetup() {
        if (!available() || _state.value.destination != FirstLaunchState.OPTIONAL_JELLYFIN) return
        val id = (saved.get<Long>(SETUP_ID) ?: 0L) + 1
        saved[SETUP_ID] = id
        _state.value = _state.value.copy(setupId = id)
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(io) { store.beginSetup() }
                present(FirstLaunchPresentation.SETUP)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { returnToCard(FirstLaunchError.PREPARE) }
        }
    }

    /** The ViewModel owns authentication across rotation; UI awaits never own/cancel this job. */
    suspend fun connect(server: String, allowHttp: Boolean, username: String, password: String): Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth> {
        connection?.takeIf { it.isActive }?.let { return it.await() }
        if (_state.value.busy || _state.value.presentation != FirstLaunchPresentation.SETUP)
            return Result.failure(IllegalStateException("Setup is no longer active"))
        val id = _state.value.setupId
        _connecting.value = true
        _connectionFailed.value = false
        val job = viewModelScope.async {
            serverConnector(server, allowHttp, username, password).also { _connectionFailed.value = it.isFailure }
        }
        connection = job
        job.invokeOnCompletion { error ->
            _connecting.value = false
            if (error == null) closeSetupIfConnected(id)
        }
        return job.await()
    }

    private fun closeSetupIfConnected(id: Long) {
        viewModelScope.launch {
            if (_state.value.presentation == FirstLaunchPresentation.SETUP && id == _state.value.setupId &&
                withContext(io) { connected() }) closeSetup(id)
        }
    }

    fun closeSetup(id: Long) {
        if (_state.value.busy || _state.value.presentation != FirstLaunchPresentation.SETUP || id != _state.value.setupId) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            connection?.cancelAndJoin()
            if (withContext(io) { connected() }) complete() else returnToCard()
        }
    }

    fun retrySave() {
        if (_state.value.error != FirstLaunchError.SAVE || _state.value.busy) return
        val target = saved.get<String>(TARGET)?.let(FirstLaunchState::valueOf) ?: return
        save(target)
    }

    private fun transition(next: FirstLaunchState) {
        if (available()) save(next)
    }

    private fun save(next: FirstLaunchState) {
        saved[TARGET] = next.name
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val confirmed = withContext(io) { store.transitionTo(next) }
                clearIntent()
                _state.value = _state.value.copy(destination = confirmed, busy = false, error = null,
                    presentation = FirstLaunchPresentation.NONE)
                saved.remove<String>(TARGET)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                _state.value = _state.value.copy(busy = false, presentation = FirstLaunchPresentation.NONE,
                    error = FirstLaunchError.SAVE)
            }
        }
    }

    private suspend fun recoverImport() {
        try {
            val before = saved.get<LongArray>(BASELINE)?.toSet() ?: run {
                returnToCard(FirstLaunchError.INTERRUPTED); return
            }
            val retained = withContext(io) { readableIds() }.any { it !in before }
            if (retained) complete() else returnToCard(FirstLaunchError.INTERRUPTED)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { returnToCard(FirstLaunchError.INTERRUPTED) }
    }

    private fun complete() = save(FirstLaunchState.COMPLETE)
    private fun available() = !_state.value.busy && _state.value.destination != FirstLaunchState.COMPLETE &&
        _state.value.presentation == FirstLaunchPresentation.NONE && _state.value.error != FirstLaunchError.SAVE
    private fun present(phase: FirstLaunchPresentation) {
        saved[PHASE] = phase.name
        _state.value = _state.value.copy(presentation = phase, busy = false, error = null)
    }
    private fun returnToCard(error: FirstLaunchError? = null) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            var resultError = error
            try { withContext(io) { store.clearPending() } }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { resultError = FirstLaunchError.PREPARE }
            clearIntent()
            _state.value = _state.value.copy(busy = false, presentation = FirstLaunchPresentation.NONE, error = resultError)
        }
    }
    private fun clearIntent() {
        listOf(PHASE, LAUNCHED, BASELINE, BATCH).forEach { saved.remove<Any>(it) }
    }
    internal companion object {
        const val PHASE = "onboarding.phase"
        const val LAUNCHED = "onboarding.pickerLaunched"
        const val BASELINE = "onboarding.existingIds"
        const val BATCH = "onboarding.batch"
        const val SETUP_ID = "onboarding.setupId"
        const val TARGET = "onboarding.saveTarget"
    }
}
