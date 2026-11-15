package net.digitalbc.leafreader.data.jellyfin

/** A remote comic is scoped to its server AND account; titles are never identifiers. */
data class JellyfinItemIdentity(
    val serverId: String,
    val userId: String,
    val itemId: String,
) {
    init {
        require(serverId.isNotBlank() && userId.isNotBlank() && itemId.isNotBlank())
    }
}

data class JellyfinPerson(
    val name: String,
    val type: String? = null,
    val role: String? = null,
    val id: String? = null,
) {
    constructor(name: String, type: String?, role: String?) : this(name, type, role, null)

    /** IDs are source-scoped by the enclosing catalogue; names match conservatively. */
    fun matches(other: JellyfinPerson): Boolean =
        if (!id.isNullOrBlank()) id.equals(other.id, true)
        else name.trim().replace(Regex("\\s+"), " ").equals(
            other.name.trim().replace(Regex("\\s+"), " "), true)
}

/** Optional server metadata, held separately so existing native JellyfinItem callers stay compatible. */
data class JellyfinItemDetails(
    val identity: JellyfinItemIdentity,
    val item: JellyfinItem,
    val overview: String? = null,
    val people: List<JellyfinPerson> = emptyList(),
    val genres: List<String> = emptyList(),
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val etag: String? = null,
    val playedPercentage: Double? = null,
    val lastPlayedDate: String? = null,
    val fromCache: Boolean = false,
) {
    constructor(identity: JellyfinItemIdentity, item: JellyfinItem, overview: String?,
        people: List<JellyfinPerson>, genres: List<String>, indexNumber: Int?,
        parentIndexNumber: Int?, etag: String?, playedPercentage: Double?, lastPlayedDate: String?) :
        this(identity, item, overview, people, genres, indexNumber, parentIndexNumber, etag,
            playedPercentage, lastPlayedDate, false)

    init {
        require(identity.itemId == item.id)
    }
}
