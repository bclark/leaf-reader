package net.digitalbc.leafreader.data.jellyfin

/** Persistent metadata coverage, independent of retained archives and reading progress. */
data class JellyfinCatalogueIndex(
    val entries: List<JellyfinCatalogueEntry>,
    val complete: Boolean,
    val scanned: Int,
    val total: Int,
    val updatedAt: Long,
    val lastCompletedAt: Long,
)

/** A failed/interrupted pass can add metadata, but only a complete fresh pass removes members. */
class JellyfinIndexUpdate(private val previous: JellyfinCatalogueIndex?) {
    private val seen = linkedMapOf<String, JellyfinCatalogueEntry>()
    private var position = 0
    private var total: Int? = null

    fun append(page: JellyfinCataloguePage, now: Long): JellyfinCatalogueIndex {
        require(!page.fromCache) { "Saved query pages are not a fresh inventory" }
        require(page.total >= 0 && (total == null || total == page.total)) { "Inventory changed during paging; retry" }
        require(page.nextIndex in position..page.total && (!page.hasMore || page.nextIndex > position))
        require(page.hasMore == (page.nextIndex < page.total))
        require(page.entries.map { it.item.id }.distinct().size == page.entries.size)
        require(page.entries.none { it.item.id in seen }) { "Repeated inventory item; retry" }
        total = page.total
        position = page.nextIndex
        page.entries.forEach { seen[it.item.id] = it }
        val complete = !page.hasMore
        val entries = if (complete) seen.values.toList()
            else (previous?.entries.orEmpty().associateBy { it.item.id } + seen).values.toList()
        return JellyfinCatalogueIndex(entries, complete, position, page.total, now,
            if (complete) now else previous?.lastCompletedAt ?: 0)
    }
}
