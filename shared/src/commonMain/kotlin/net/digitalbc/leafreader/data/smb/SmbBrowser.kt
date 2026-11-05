package net.digitalbc.leafreader.data.smb

/** Immutable state; task ownership and publication remain in the native ViewModel/ObservableObject. */
data class SmbBrowserState(
    val path: String,
    val entries: List<SmbEntry>,
    val selectedPaths: Set<String>,
    val loading: Boolean,
    val failure: SmbFailureKind?,
    val generation: Long,
    val pendingPath: String?,
) {
    companion object {
        fun empty(): SmbBrowserState = SmbBrowserState("", emptyList(), emptySet(), false, null, 0L, null)
    }
}

object SmbBrowserLogic {
    @Throws(IllegalArgumentException::class)
    fun begin(state: SmbBrowserState, path: String): SmbBrowserState =
        state.copy(loading = true, failure = null, generation = state.generation + 1,
            pendingPath = SmbPaths.normalize(path))

    fun complete(state: SmbBrowserState, generation: Long, entries: List<SmbEntry>): SmbBrowserState {
        if (generation != state.generation || !state.loading) return state
        val path = state.pendingPath ?: return state
        return state.copy(path = path, entries = SmbListing.prepare(path, entries),
            selectedPaths = emptySet(), loading = false, failure = null, pendingPath = null)
    }

    fun fail(state: SmbBrowserState, generation: Long, failure: SmbFailureKind): SmbBrowserState =
        if (generation != state.generation || !state.loading) state
        else state.copy(loading = false, failure = failure, pendingPath = null)

    /** Forget a connection's listing/selection without reusing request tokens from that connection. */
    fun reset(state: SmbBrowserState): SmbBrowserState =
        SmbBrowserState.empty().copy(generation = state.generation + 1)

    /** Invalidates late callbacks while retaining the last successful directory and selection. */
    fun cancel(state: SmbBrowserState): SmbBrowserState =
        state.copy(loading = false, generation = state.generation + 1, pendingPath = null)

    fun toggle(state: SmbBrowserState, path: String): SmbBrowserState {
        if (state.loading || state.entries.none { it.path == path && !it.isDirectory && SmbListing.isImportable(it.name) }) return state
        return state.copy(selectedPaths = if (path in state.selectedPaths) state.selectedPaths - path else state.selectedPaths + path)
    }

    fun selectAll(state: SmbBrowserState): SmbBrowserState =
        if (state.loading) state else state.copy(selectedPaths = state.entries
            .filter { !it.isDirectory && SmbListing.isImportable(it.name) }.map { it.path }.toSet())

    fun clearSelection(state: SmbBrowserState): SmbBrowserState = state.copy(selectedPaths = emptySet())
}
