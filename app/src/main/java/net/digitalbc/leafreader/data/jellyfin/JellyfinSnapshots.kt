package net.digitalbc.leafreader.data.jellyfin

import org.json.JSONArray
import org.json.JSONObject

data class JellyfinSavedCatalogue(
    val libraries: List<JellyfinView>, val library: JellyfinView?, val index: JellyfinCatalogueIndex?,
)

/** Lightweight private metadata. Never contains credentials, server URLs or filesystem paths. */
object JellyfinSnapshots {
    fun encode(entry: JellyfinCatalogueEntry): String = JSONObject().apply {
        val item = entry.item
        put("id", item.id); put("name", item.name); put("type", entry.type)
        put("folder", item.isFolder); put("parent", item.parentId)
        put("container", item.container ?: item.path?.substringAfterLast('.', "") ?: JSONObject.NULL)
        put("size", item.sizeBytes); put("image", item.imageTag)
        put("series", item.seriesName); put("year", item.productionYear)
        put("ticks", item.playbackPositionTicks); put("played", entry.played ?: JSONObject.NULL)
        put("seriesId", entry.seriesId); put("volume", entry.volumeNumber); put("issue", entry.issueNumber)
        entry.folder?.let { put("folderId", it.id); put("folderName", it.name); put("folderLibrary", it.libraryId) }
        put("people", JSONArray(entry.people.map { person -> JSONObject().apply {
            put("name", person.name); put("type", person.type); put("role", person.role); put("id", person.id)
        } }))
    }.toString()

    fun decode(value: String): JellyfinCatalogueEntry {
        val obj = JSONObject(value)
        fun text(key: String) = if (obj.isNull(key)) null else obj.getString(key)
        val id = obj.getString("id").also { require(it.isNotBlank()) }
        val played = if (obj.isNull("played")) null else obj.getBoolean("played")
        return JellyfinCatalogueEntry(JellyfinItem(id, obj.getString("name"), obj.getBoolean("folder"),
            container = text("container"), sizeBytes = obj.getLong("size"), parentId = text("parent"),
            imageTag = text("image"), seriesName = text("series"),
            productionYear = if (obj.isNull("year")) null else obj.getInt("year"),
            playbackPositionTicks = obj.getLong("ticks"), played = played == true),
            obj.getString("type"), played, text("seriesId"),
            if (obj.isNull("volume")) null else obj.getInt("volume"),
            if (obj.isNull("issue")) null else obj.getDouble("issue"),
            text("folderId")?.let { JellyfinFolderIdentity(it, obj.getString("folderName"), obj.getString("folderLibrary")) },
            buildList {
                val people = obj.optJSONArray("people") ?: return@buildList
                for (i in 0 until people.length()) {
                    val person = people.getJSONObject(i)
                    fun field(key: String) = if (person.isNull(key)) null else person.getString(key)
                    add(JellyfinPerson(person.getString("name"), field("type"), field("role"), field("id")))
                }
            })
    }

    fun page(page: JellyfinCataloguePage): String = JSONObject().apply {
        put("entries", JSONArray(page.entries.map { JSONObject(encode(it)) }))
        put("next", page.nextIndex); put("total", page.total); put("more", page.hasMore)
    }.toString()

    fun page(value: String): JellyfinCataloguePage {
        val obj = JSONObject(value)
        val entries = obj.getJSONArray("entries")
        return JellyfinCataloguePage((0 until entries.length()).map { decode(entries.getJSONObject(it).toString()) },
            obj.getInt("next"), obj.getInt("total"), obj.getBoolean("more"), fromCache = true)
    }

    fun views(views: List<JellyfinView>): String = JSONArray(views.map {
        JSONObject().put("id", it.id).put("name", it.name).put("type", it.collectionType)
    }).toString()

    fun views(value: String): List<JellyfinView> {
        val array = JSONArray(value)
        return (0 until array.length()).map {
            val obj = array.getJSONObject(it)
            JellyfinView(obj.getString("id"), obj.getString("name"),
                if (obj.isNull("type")) null else obj.getString("type"))
        }
    }

    fun index(index: JellyfinCatalogueIndex): String = JSONObject().apply {
        put("entries", JSONArray(index.entries.map { JSONObject(encode(it)) }))
        put("complete", index.complete); put("scanned", index.scanned); put("total", index.total)
        put("updated", index.updatedAt); put("completed", index.lastCompletedAt)
    }.toString()

    fun index(value: String): JellyfinCatalogueIndex {
        val obj = JSONObject(value)
        val entries = obj.getJSONArray("entries")
        return JellyfinCatalogueIndex((0 until entries.length()).map { decode(entries.getJSONObject(it).toString()) },
            obj.getBoolean("complete"), obj.getInt("scanned"), obj.getInt("total"),
            obj.getLong("updated"), obj.getLong("completed"))
    }

    fun details(value: JellyfinItemDetails): String = JSONObject().apply {
        put("entry", JSONObject(encode(JellyfinCatalogueEntry(value.item, "Book", value.item.played,
            people = value.people))))
        put("overview", value.overview); put("genres", JSONArray(value.genres))
        put("index", value.indexNumber); put("volume", value.parentIndexNumber)
        put("percent", value.playedPercentage); put("lastPlayed", value.lastPlayedDate)
    }.toString()

    fun details(value: String, identity: JellyfinItemIdentity): JellyfinItemDetails {
        val obj = JSONObject(value)
        val entry = decode(obj.getJSONObject("entry").toString())
        val genres = obj.getJSONArray("genres")
        return JellyfinItemDetails(identity, entry.item,
            overview = if (obj.isNull("overview")) null else obj.getString("overview"),
            people = entry.people, genres = (0 until genres.length()).map { genres.getString(it) },
            indexNumber = if (obj.isNull("index")) null else obj.getInt("index"),
            parentIndexNumber = if (obj.isNull("volume")) null else obj.getInt("volume"),
            playedPercentage = if (obj.isNull("percent")) null else obj.getDouble("percent"),
            lastPlayedDate = if (obj.isNull("lastPlayed")) null else obj.getString("lastPlayed"),
            fromCache = true)
    }

    fun reference(server: String, user: String, item: String): String {
        require(listOf(server, user, item).all { it.isNotBlank() })
        return "remote:" + listOf(server, user, item).joinToString("") { "${it.length}:$it" }
    }
}
