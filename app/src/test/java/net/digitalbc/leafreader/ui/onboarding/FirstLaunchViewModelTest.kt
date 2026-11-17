package net.digitalbc.leafreader.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import net.digitalbc.leafreader.data.settings.FirstLaunchState
import net.digitalbc.leafreader.data.settings.FirstLaunchStateStore
import net.digitalbc.leafreader.ui.library.FileImportState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class FirstLaunchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val owners = mutableListOf<ViewModelStore>()
    private val outcomes = MutableStateFlow<FileImportState?>(null)
    private var retained = setOf<Long>()
    private var auth = false
    private var calls = 0
    private var writesFail = false
    private lateinit var preferences: SharedPreferences
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val context = RuntimeEnvironment.getApplication()
        context.deleteSharedPreferences("onboarding-test")
        preferences = context.getSharedPreferences("onboarding-test", Context.MODE_PRIVATE)
    }
    @After fun teardown() { owners.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun model(saved: SavedStateHandle = SavedStateHandle(), existing: Boolean = false,
        io: kotlinx.coroutines.CoroutineDispatcher = dispatcher,
        connector: suspend (String, Boolean, String, String) -> Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth> =
            { _, _, _, _ -> Result.failure(java.io.IOException()) }): FirstLaunchViewModel {
        val wrapped = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun putStringSet(key: String, value: Set<String>?): SharedPreferences.Editor { editor.putStringSet(key, value); return this }
                    override fun remove(key: String): SharedPreferences.Editor { editor.remove(key); return this }
                    override fun commit(): Boolean = if (writesFail) false else editor.commit()
                }
            }
        }
        return FirstLaunchViewModel(saved, FirstLaunchStateStore(wrapped) { existing },
            { retained }, { auth }, { calls++; calls.toLong() }, outcomes, io, connector)
            .also { vm -> owners += ViewModelStore().apply { put("onboarding", vm) } }
    }
    private fun pick(vm: FirstLaunchViewModel) {
        vm.chooseFiles(); assertTrue(vm.claimPicker()); vm.filesSelected(listOf(Uri.parse("content://fixture/one")))
    }
    @Test fun pickerSelectionAndFailedImportNeverCompleteAndCanRetry() {
        val vm = model(); pick(vm)
        assertEquals(FirstLaunchState.WELCOME, vm.state.value.destination)
        outcomes.value = FileImportState(1, 1, finished = true, failedCount = 1)
        assertEquals(FirstLaunchError.IMPORT, vm.state.value.error)
        pick(vm)
        outcomes.value = FileImportState(2, 1, finished = true, importedComicIds = listOf(8))
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
        assertEquals(FirstLaunchState.COMPLETE, model().state.value.destination)
    }
    @Test fun duplicateTapsAndStaleResultsCannotLaunchOrCompleteAnotherBatch() {
        val vm = model(); vm.chooseFiles(); vm.chooseFiles()
        assertTrue(vm.claimPicker()); assertFalse(vm.claimPicker())
        vm.filesSelected(listOf(Uri.EMPTY)); vm.filesSelected(listOf(Uri.EMPTY)); assertEquals(1, calls)
        outcomes.value = FileImportState(99, 1, finished = true, importedComicIds = listOf(8))
        assertEquals(FirstLaunchPresentation.IMPORT, vm.state.value.presentation)
        outcomes.value = FileImportState(1, 1, finished = true, cancelled = true)
        assertEquals(FirstLaunchPresentation.NONE, vm.state.value.presentation)
        assertNull(vm.state.value.error)
    }
    @Test fun cancelPickerReturnsToWelcomeWithoutImporting() {
        val vm = model(); vm.chooseFiles(); vm.filesSelected(emptyList())
        assertEquals(0, calls); assertEquals(FirstLaunchState.WELCOME, vm.state.value.destination)
        assertEquals(FirstLaunchPresentation.NONE, vm.state.value.presentation)
    }
    @Test fun cancelledPartialSuccessCompletesAndRetainedComicIsNotRemoved() {
        val vm = model(); pick(vm); retained = setOf(8)
        outcomes.value = FileImportState(1, 2, finished = true, importedComicIds = listOf(8), cancelled = true)
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
        assertEquals(setOf(8L), retained)
    }
    @Test fun optionalNavigationAndEitherSkipPersistWithoutCallingImportOrAuth() {
        val vm = model(); vm.optionalJellyfin()
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, model().state.value.destination)
        vm.back(); assertEquals(FirstLaunchState.WELCOME, vm.state.value.destination)
        vm.skip(); vm.back(); assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
        assertEquals(0, calls); assertFalse(auth)
    }
    @Test fun setupRequiresStoredConfirmedConnectionAndRejectsStaleCallbacks() {
        val vm = model(); vm.optionalJellyfin(); vm.openSetup(); val first = vm.state.value.setupId
        vm.closeSetup(first)
        assertEquals(FirstLaunchState.OPTIONAL_JELLYFIN, vm.state.value.destination)
        vm.openSetup(); val second = vm.state.value.setupId; auth = true
        vm.closeSetup(first); assertEquals(FirstLaunchPresentation.SETUP, vm.state.value.presentation)
        vm.closeSetup(second); assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
    }
    @Test fun failedCompletionWriteIsVisibleAndRetriesWithoutReimporting() {
        val vm = model(); pick(vm); writesFail = true
        outcomes.value = FileImportState(1, 1, finished = true, importedComicIds = listOf(8))
        dispatcher.scheduler.runCurrent()
        assertEquals(vm.state.value.toString(), FirstLaunchError.SAVE, vm.state.value.error)
        assertEquals(FirstLaunchState.WELCOME, vm.state.value.destination)
        vm.chooseFiles(); vm.skip(); assertEquals(1, calls)
        writesFail = false; vm.retrySave()
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination); assertEquals(1, calls)
    }
    @Test fun processRecreationReconcilesNewReadableRowsButNeverPreexistingOrAbsentRows() {
        retained = setOf(3)
        val saved = SavedStateHandle(); val vm = model(saved); pick(vm)
        // Rebuild the saved-state payload, as a new process would; no live import result exists.
        val payload = saved.keys().associateWith { saved.get<Any?>(it) }
        retained = setOf(3, 8)
        val recovered = model(SavedStateHandle(payload))
        assertEquals(FirstLaunchState.COMPLETE, recovered.state.value.destination)
    }
    @Test fun processRecreationWithNoNewReadableComicReturnsRetryState() {
        retained = setOf(3)
        val saved = SavedStateHandle(); val vm = model(saved); pick(vm)
        val payload = saved.keys().associateWith { saved.get<Any?>(it) }
        val recovered = model(SavedStateHandle(payload))
        assertEquals(FirstLaunchState.WELCOME, recovered.state.value.destination)
        assertEquals(FirstLaunchError.INTERRUPTED, recovered.state.value.error)
        assertEquals(setOf(3L), retained)
    }
    @Test fun pickerRecreationDoesNotLaunchAgainAndCancellationStillReturnsToCard() {
        val saved = SavedStateHandle(); val vm = model(saved); vm.chooseFiles(); assertTrue(vm.claimPicker())
        val recovered = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertFalse(recovered.claimPicker()); recovered.filesSelected(emptyList())
        assertEquals(FirstLaunchPresentation.NONE, recovered.state.value.presentation)
    }
    @Test fun processRecreationAfterConnectionUsesLocalAuthWithoutAnotherRequest() {
        val saved = SavedStateHandle(); val vm = model(saved); vm.optionalJellyfin(); vm.openSetup(); auth = true
        val recovered = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals(FirstLaunchState.COMPLETE, recovered.state.value.destination)
        assertEquals(0, calls)
    }
    @Test fun importRecoveryDoesNotRequireAnActivitySavedStateSnapshot() {
        retained = setOf(3)
        val vm = model(); pick(vm); retained = setOf(3, 8)
        assertEquals(FirstLaunchState.COMPLETE, model(SavedStateHandle()).state.value.destination)
    }
    @Test fun setupRecoveryDoesNotRequireAnActivitySavedStateSnapshot() {
        val vm = model(); vm.optionalJellyfin(); vm.openSetup(); auth = true
        assertEquals(FirstLaunchState.COMPLETE, model(SavedStateHandle()).state.value.destination)
    }
    @Test fun failedIntentWriteDoesNotStartImportOrSetup() {
        val vm = model(); vm.chooseFiles(); writesFail = true; vm.filesSelected(listOf(Uri.EMPTY))
        assertEquals(0, calls); assertEquals(FirstLaunchError.PREPARE, vm.state.value.error)
        writesFail = false; vm.optionalJellyfin(); writesFail = true; vm.openSetup()
        assertEquals(FirstLaunchPresentation.NONE, vm.state.value.presentation)
        assertEquals(FirstLaunchError.PREPARE, vm.state.value.error)
    }
    @Test fun connectionHasOneOwnerAcrossUiWaiterCancellationAndRepeatedConnectTaps() {
        val gate = CompletableDeferred<Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth>>()
        var requests = 0
        val vm = model(connector = { _, _, _, _ -> requests++; gate.await() })
        vm.optionalJellyfin(); vm.openSetup()
        val firstUi = CoroutineScope(dispatcher).launch { vm.connect("fixture", false, "fixture", "synthetic") }
        val secondUi = CoroutineScope(dispatcher).launch { vm.connect("fixture", false, "fixture", "synthetic") }
        assertEquals(1, requests); firstUi.cancel(); assertTrue(vm.connecting.value)
        auth = true
        gate.complete(Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinAuth(
            accessToken = "synthetic", userId = "fixture", serverId = "fixture", username = "fixture")))
        dispatcher.scheduler.runCurrent()
        assertFalse(vm.connecting.value); assertTrue(secondUi.isCompleted)
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
    }
    @Test fun backCancelsOwnedConnectionBeforeReturningAndFailureSurvivesUiReattachment() {
        val gate = CompletableDeferred<Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth>>()
        var cancelled = false
        val vm = model(connector = { _, _, _, _ -> try { gate.await() } finally { cancelled = true } })
        vm.optionalJellyfin(); vm.openSetup()
        CoroutineScope(dispatcher).launch { vm.connect("fixture", false, "fixture", "synthetic") }
        vm.closeSetup(vm.state.value.setupId); dispatcher.scheduler.runCurrent()
        assertTrue(cancelled); assertFalse(vm.connecting.value)
        assertEquals(FirstLaunchPresentation.NONE, vm.state.value.presentation)
        vm.openSetup()
        gate.complete(Result.failure(java.io.IOException()))
        CoroutineScope(dispatcher).launch { vm.connect("fixture", false, "fixture", "synthetic") }
        assertTrue(vm.connectionFailed.value)
        assertEquals(FirstLaunchPresentation.SETUP, vm.state.value.presentation)
    }
    @Test fun delayedInitializationNeverPresentsStaleSetupOverDurableComplete() {
        model().skip()
        val saved = SavedStateHandle(mapOf(FirstLaunchViewModel.PHASE to FirstLaunchPresentation.SETUP.name))
        val vm = model(saved, io = StandardTestDispatcher(dispatcher.scheduler))
        assertNull(vm.state.value.destination); assertTrue(vm.state.value.busy); assertFalse(vm.state.value.showSetup)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination)
        assertFalse(vm.state.value.showSetup)
    }
    @Test fun retiredDurableIntentCannotBeReopenedByAnOlderActivitySnapshot() {
        val saved = SavedStateHandle(mapOf(FirstLaunchViewModel.PHASE to FirstLaunchPresentation.SETUP.name))
        val vm = model(saved)
        assertEquals(FirstLaunchState.WELCOME, vm.state.value.destination)
        assertEquals(FirstLaunchPresentation.NONE, vm.state.value.presentation)
        vm.chooseFiles(); assertTrue(vm.claimPicker())
    }
    @Test fun existingInstallNeverEntersOnboarding() {
        val vm = model(existing = true); vm.chooseFiles(); vm.openSetup(); vm.optionalJellyfin()
        assertEquals(FirstLaunchState.COMPLETE, vm.state.value.destination); assertEquals(0, calls)
    }
}
