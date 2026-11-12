package net.digitalbc.leafreader.data.jellyfin

enum class JellyfinCatalogueView { ALL, SERIES, LISTS, UNREAD, DOWNLOADED }

/** Separate wire metadata preserves the existing Swift-exported JellyfinItem constructor. */
data class JellyfinCatalogueEntry(
    val item: JellyfinItem, val type: String, val played: Boolean?,
    val seriesId: String? = null,
    val volumeNumber: Int? = null,
    val issueNumber: Double? = null,
    val folder: JellyfinFolderIdentity? = null,
    val people: List<JellyfinPerson> = emptyList(),
) {
    constructor(item: JellyfinItem, type: String, played: Boolean?) :
        this(item, type, played, null, null, null, null)

    constructor(item: JellyfinItem, type: String, played: Boolean?, seriesId: String?,
        volumeNumber: Int?, issueNumber: Double?) :
        this(item, type, played, seriesId, volumeNumber, issueNumber, null)

    constructor(item: JellyfinItem, type: String, played: Boolean?, seriesId: String?,
        volumeNumber: Int?, issueNumber: Double?, folder: JellyfinFolderIdentity?) :
        this(item, type, played, seriesId, volumeNumber, issueNumber, folder, emptyList())
}

/** Only construct after an authenticated folder/selected-library ancestry check. */
data class JellyfinFolderIdentity(val id: String, val name: String, val libraryId: String) {
    init { require(id.isNotBlank() && name.isNotBlank() && libraryId.isNotBlank()) }
}

data class JellyfinCataloguePage(
    val entries: List<JellyfinCatalogueEntry>,
    val nextIndex: Int,
    val total: Int,
    val hasMore: Boolean,
    val fromCache: Boolean = false,
) {
    constructor(entries: List<JellyfinCatalogueEntry>, nextIndex: Int, total: Int, hasMore: Boolean) :
        this(entries, nextIndex, total, hasMore, false)
}

data class JellyfinCatalogueQuery(
    val libraryId: String,
    val view: JellyfinCatalogueView = JellyfinCatalogueView.ALL,
    val collectionId: String? = null,
    val search: String = "",
    val startIndex: Int = 0,
    val limit: Int = 100,
    val creator: JellyfinPerson? = null,
) {
    constructor(libraryId: String, view: JellyfinCatalogueView, collectionId: String?,
        search: String, startIndex: Int, limit: Int) :
        this(libraryId, view, collectionId, search, startIndex, limit, null)

    init {
        require(libraryId.isNotBlank())
        require(startIndex >= 0 && limit in 1..200)
        require(collectionId == null || collectionId.isNotBlank())
    }

    fun url(baseUrl: String, userId: String): String {
        val collections = view == JellyfinCatalogueView.LISTS && collectionId == null
        val params = linkedMapOf(
            "UserId" to userId,
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
            "Recursive" to "true",
            "IncludeItemTypes" to if (collections) "BoxSet" else "Book",
            "Fields" to "Overview,Path,MediaSources,ParentId,People",
            "EnableUserData" to "true",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
            "CollapseBoxSetItems" to "false",
        )
        // Jellyfin clears ParentId on BoxSet-only discovery. Collections are explicitly
        // account-wide; members are separately checked against the selected Books library.
        if (!collections) params["ParentId"] = collectionId ?: libraryId
        if (view == JellyfinCatalogueView.UNREAD) params["IsPlayed"] = "false"
        if (search.isNotBlank()) params["SearchTerm"] = search.trim()
        creator?.let {
            if (!it.id.isNullOrBlank()) params["PersonIds"] = it.id
            else params["Person"] = it.name.trim()
        }
        return "${baseUrl.trimEnd('/')}/Items?" + params.entries.joinToString("&") {
            "${it.key}=${encode(it.value)}"
        }
    }

    companion object {
        fun ancestorsUrl(baseUrl: String, userId: String, itemId: String): String =
            "${baseUrl.trimEnd('/')}/Items/${encode(itemId)}/Ancestors?UserId=${encode(userId)}"

        private fun encode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val n = byte.toInt() and 255
                if (n in 65..90 || n in 97..122 || n in 48..57 || n == 45 || n == 46 || n == 95 || n == 126)
                    append(n.toChar())
                else { append('%'); append("0123456789ABCDEF"[n shr 4]); append("0123456789ABCDEF"[n and 15]) }
            }
        }
    }
}

object JellyfinCatalogueLogic {
    fun visible(entries: List<JellyfinCatalogueEntry>, view: JellyfinCatalogueView): List<JellyfinCatalogueEntry> =
        entries.filter {
            if (view == JellyfinCatalogueView.LISTS) it.type.equals("BoxSet", true)
            else it.type.equals("Book", true) && !it.item.isFolder &&
                (view != JellyfinCatalogueView.UNREAD || it.played == false)
        }.distinctBy { it.item.id }

    /** Missing series stays in an explicit ungrouped section; titles never imply a series. */
    fun series(entries: List<JellyfinCatalogueEntry>): Map<String?, List<JellyfinCatalogueEntry>> =
        entries.groupBy { it.item.seriesName?.trim()?.takeIf(String::isNotEmpty) }
}
