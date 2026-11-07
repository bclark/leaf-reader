package net.digitalbc.leafreader.data.jellyfin

data class JellyfinBreadcrumb(
    val id: String?,
    val name: String,
)

data class JellyfinBrowserState(
    val currentParentId: String?,
    val currentTitle: String,
    val breadcrumbs: List<JellyfinBreadcrumb>,
    val views: List<JellyfinView>,
    val items: List<JellyfinItem>,
    val downloadedItemIds: Set<String>,
    val selectedItemIds: Set<String>,
    val loading: Boolean,
    val failure: JellyfinFailureKind?,
    val generation: Long,
) {
    val isRoot: Boolean get() = currentParentId == null

    companion object {
        fun empty(): JellyfinBrowserState = JellyfinBrowserState(
            currentParentId = null,
            currentTitle = "Jellyfin Libraries",
            breadcrumbs = emptyList(),
            views = emptyList(),
            items = emptyList(),
            downloadedItemIds = emptySet(),
            selectedItemIds = emptySet(),
            loading = false,
            failure = null,
            generation = 0L,
        )
    }
}

object JellyfinBrowserLogic {
    fun begin(state: JellyfinBrowserState): JellyfinBrowserState =
        state.copy(
            loading = true,
            failure = null,
            generation = state.generation + 1,
        )

    fun completeViews(state: JellyfinBrowserState, generation: Long, views: List<JellyfinView>): JellyfinBrowserState {
        if (generation != state.generation || !state.loading) return state
        return state.copy(
            currentParentId = null,
            currentTitle = "Jellyfin Libraries",
            breadcrumbs = emptyList(),
            views = views,
            items = emptyList(),
            selectedItemIds = emptySet(),
            loading = false,
            failure = null,
        )
    }

    fun completeItems(
        state: JellyfinBrowserState,
        generation: Long,
        parentId: String,
        title: String,
        items: List<JellyfinItem>,
        breadcrumbs: List<JellyfinBreadcrumb>,
    ): JellyfinBrowserState {
        if (generation != state.generation || !state.loading) return state
        return state.copy(
            currentParentId = parentId,
            currentTitle = title,
            breadcrumbs = breadcrumbs,
            items = items,
            selectedItemIds = emptySet(),
            loading = false,
            failure = null,
        )
    }

    fun fail(state: JellyfinBrowserState, generation: Long, failure: JellyfinFailureKind): JellyfinBrowserState =
        if (generation != state.generation || !state.loading) state
        else state.copy(loading = false, failure = failure)

    fun reset(): JellyfinBrowserState = JellyfinBrowserState.empty()

    fun toggleSelection(state: JellyfinBrowserState, itemId: String): JellyfinBrowserState {
        val item = state.items.find { it.id == itemId } ?: return state
        if (!item.isImportable || itemId in state.downloadedItemIds) return state
        val updated = if (itemId in state.selectedItemIds) {
            state.selectedItemIds - itemId
        } else {
            state.selectedItemIds + itemId
        }
        return state.copy(selectedItemIds = updated)
    }

    fun selectAllImportable(state: JellyfinBrowserState): JellyfinBrowserState {
        val selectable = state.items
            .filter { it.isImportable && it.id !in state.downloadedItemIds }
            .map { it.id }
            .toSet()
        return state.copy(selectedItemIds = selectable)
    }

    fun clearSelection(state: JellyfinBrowserState): JellyfinBrowserState =
        state.copy(selectedItemIds = emptySet())

    fun updateDownloadedIds(state: JellyfinBrowserState, downloaded: Set<String>): JellyfinBrowserState =
        state.copy(
            downloadedItemIds = downloaded,
            selectedItemIds = state.selectedItemIds - downloaded,
        )
}
