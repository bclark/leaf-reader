package net.digitalbc.leafreader.data.backup

import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages portable, versioned serialization and deserialization of Leaf library data.
 *
 * Guarantees:
 * - Credentials and tokens (SMB credentials) are never exported.
 * - Ephemeral device absolute paths (storage sandbox paths) are excluded;
 *   comics are identified portably by title, series, volume, issue number, and format.
 * - Imports are pre-validated before any state changes are applied.
 */
object BackupManager {

    const val CURRENT_VERSION = 1

    fun exportToJson(
        comics: List<ComicEntity>,
        readingLists: List<ReadingListEntity>,
        readingListItems: List<ReadingListItemEntity>,
        comicsById: Map<Long, ComicEntity>,
        settings: AppSettings,
        appVersion: String = "0.3.4",
    ): String {
        val root = JSONObject()
        root.put("version", CURRENT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appVersion", appVersion)

        // Settings (non-sensitive preferences only; NO SMB passwords or server hosts)
        val settingsObj = JSONObject()
        settingsObj.put("defaultRightToLeft", settings.defaultRightToLeft)
        settingsObj.put("amoledTheme", settings.amoledTheme)
        settingsObj.put("pageSlideEnabled", settings.pageSlideEnabled)
        settingsObj.put("pageFitMode", settings.pageFitMode.name)
        settingsObj.put("portraitSpread", settings.portraitSpread)
        settingsObj.put("landscapeSpread", settings.landscapeSpread)
        settingsObj.put("libraryColumns", settings.libraryColumns)
        root.put("settings", settingsObj)

        // Comics list
        val comicsArray = JSONArray()
        for (comic in comics) {
            val cObj = JSONObject()
            cObj.put("title", comic.title)
            if (comic.series != null) cObj.put("series", comic.series)
            if (comic.volume != null) cObj.put("volume", comic.volume)
            if (comic.issueNumber != null) cObj.put("issueNumber", comic.issueNumber.toDouble())
            cObj.put("format", comic.format)
            cObj.put("pageCount", comic.pageCount)
            cObj.put("rightToLeft", comic.rightToLeft)
            cObj.put("lastPage", comic.lastPage)
            cObj.put("lastSlot", comic.lastSlot)
            cObj.put("dateAdded", comic.dateAdded)
            cObj.put("lastOpened", comic.lastOpened)
            comic.completionOverride?.let { cObj.put("completed", it) }
            comicsArray.put(cObj)
        }
        root.put("comics", comicsArray)

        // Reading lists
        val itemsByListId = readingListItems.groupBy { it.listId }
        val listsArray = JSONArray()
        for (list in readingLists) {
            val lObj = JSONObject()
            lObj.put("name", list.name)
            lObj.put("createdAt", list.createdAt)
            val itemsArray = JSONArray()
            val items = itemsByListId[list.id] ?: emptyList()
            for (item in items) {
                if (item.itemId != null && item.serverId != null && item.userId != null) {
                    itemsArray.put(JSONObject().apply {
                        put("comicTitle", item.title ?: "Jellyfin item")
                        put("sortOrder", item.sortOrder)
                        put("serverId", item.serverId); put("userId", item.userId); put("itemId", item.itemId)
                        put("libraryId", item.libraryId); put("remoteSnapshot", item.snapshot)
                    })
                    continue
                }
                val targetComic = comicsById[item.comicId] ?: continue
                val iObj = JSONObject()
                iObj.put("comicTitle", targetComic.title)
                if (targetComic.series != null) iObj.put("series", targetComic.series)
                if (targetComic.issueNumber != null) iObj.put("issueNumber", targetComic.issueNumber.toDouble())
                iObj.put("sortOrder", item.sortOrder)
                itemsArray.put(iObj)
            }
            lObj.put("items", itemsArray)
            listsArray.put(lObj)
        }
        root.put("readingLists", listsArray)

        return root.toString(2)
    }

    fun parseAndValidate(jsonString: String): Result<BackupPayload> = runCatching {
        val root = JSONObject(jsonString)
        if (!root.has("version")) {
            throw IllegalArgumentException("Invalid backup format: missing version")
        }
        val version = root.getInt("version")
        if (version != CURRENT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version (expected $CURRENT_VERSION)")
        }

        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
        val appVersion = root.optString("appVersion", "unknown")

        // Parse settings
        val settings = if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")
            BackupSettings(
                defaultRightToLeft = sObj.optBoolean("defaultRightToLeft", false),
                amoledTheme = sObj.optBoolean("amoledTheme", false),
                pageSlideEnabled = sObj.optBoolean("pageSlideEnabled", true),
                pageFitMode = sObj.optString("pageFitMode", "FIT_PAGE"),
                portraitSpread = sObj.optBoolean("portraitSpread", false),
                landscapeSpread = sObj.optBoolean("landscapeSpread", false),
                libraryColumns = sObj.optInt("libraryColumns", 2),
            )
        } else null

        // Parse comics
        val comics = mutableListOf<BackupComic>()
        if (root.has("comics")) {
            val arr = root.getJSONArray("comics")
            for (i in 0 until arr.length()) {
                val cObj = arr.getJSONObject(i)
                val title = cObj.getString("title")
                val series = if (cObj.has("series") && !cObj.isNull("series")) cObj.getString("series") else null
                val volume = if (cObj.has("volume") && !cObj.isNull("volume")) cObj.getString("volume") else null
                val issueNumber = if (cObj.has("issueNumber") && !cObj.isNull("issueNumber")) {
                    cObj.getDouble("issueNumber").toFloat()
                } else null
                val format = cObj.optString("format", "cbz")
                val pageCount = cObj.getInt("pageCount")
                val rightToLeft = cObj.optBoolean("rightToLeft", false)
                val lastPage = cObj.optInt("lastPage", 0)
                val lastSlot = cObj.optInt("lastSlot", 0)
                val dateAdded = cObj.optLong("dateAdded", 0)
                val lastOpened = cObj.optLong("lastOpened", 0)

                comics += BackupComic(
                    title = title,
                    series = series,
                    volume = volume,
                    issueNumber = issueNumber,
                    format = format,
                    pageCount = pageCount,
                    rightToLeft = rightToLeft,
                    lastPage = lastPage,
                    lastSlot = lastSlot,
                    dateAdded = dateAdded,
                    lastOpened = lastOpened,
                    completed = if (cObj.isNull("completed")) null else cObj.getBoolean("completed"),
                )
            }
        }

        // Parse reading lists
        val readingLists = mutableListOf<BackupReadingList>()
        if (root.has("readingLists")) {
            val arr = root.getJSONArray("readingLists")
            for (i in 0 until arr.length()) {
                val lObj = arr.getJSONObject(i)
                val name = lObj.getString("name")
                val createdAt = lObj.optLong("createdAt", System.currentTimeMillis())
                val items = mutableListOf<BackupReadingListItem>()
                if (lObj.has("items")) {
                    val itemsArr = lObj.getJSONArray("items")
                    for (j in 0 until itemsArr.length()) {
                        val iObj = itemsArr.getJSONObject(j)
                        val comicTitle = iObj.getString("comicTitle")
                        val itemSeries = if (iObj.has("series") && !iObj.isNull("series")) iObj.getString("series") else null
                        val itemIssue = if (iObj.has("issueNumber") && !iObj.isNull("issueNumber")) {
                            iObj.getDouble("issueNumber").toFloat()
                        } else null
                        val sortOrder = iObj.optInt("sortOrder", j)
                        val remoteItem = iObj.optString("itemId").takeIf { it.isNotBlank() && it != "null" }
                        val server = iObj.optString("serverId").takeIf { it.isNotBlank() && it != "null" }
                        val user = iObj.optString("userId").takeIf { it.isNotBlank() && it != "null" }
                        if (remoteItem != null || server != null || user != null) {
                            require(remoteItem != null && server != null && user != null) { "Incomplete remote list identity" }
                        }
                        val snapshot = if (iObj.isNull("remoteSnapshot")) null else iObj.getString("remoteSnapshot")
                        snapshot?.let {
                            require(net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.decode(it).item.id == remoteItem) {
                                "Remote list snapshot identity mismatch"
                            }
                        }
                        items += BackupReadingListItem(comicTitle, itemSeries, itemIssue, sortOrder,
                            server, user, remoteItem, if (iObj.isNull("libraryId")) null else iObj.getString("libraryId"), snapshot)
                    }
                }
                readingLists += BackupReadingList(name, createdAt, items)
            }
        }

        BackupPayload(
            version = version,
            exportedAt = exportedAt,
            appVersion = appVersion,
            settings = settings,
            comics = comics,
            readingLists = readingLists,
        )
    }

    /** Returns only a unique match; an ambiguous stronger match must not fall back to a guess. */
    fun findMatchingComic(backup: BackupComic, localComics: List<ComicEntity>): ComicEntity? {
        val byTitleAndPages = localComics.filter {
            it.title.equals(backup.title, ignoreCase = true) && it.pageCount == backup.pageCount
        }
        if (byTitleAndPages.isNotEmpty()) return byTitleAndPages.singleOrNull()

        val byTitle = localComics.filter {
            it.title.equals(backup.title, ignoreCase = true)
        }
        if (byTitle.isNotEmpty()) return byTitle.singleOrNull()

        if (!backup.series.isNullOrBlank() && backup.issueNumber != null) {
            return localComics.filter {
                it.series.equals(backup.series, ignoreCase = true) &&
                    (backup.volume.isNullOrBlank() || it.volume.equals(backup.volume, ignoreCase = true)) &&
                    it.issueNumber == backup.issueNumber
            }.singleOrNull()
        }
        return null
    }

    /** Reject every colliding payload record, independent of payload order or conflict strategy. */
    fun matchComics(
        backups: List<BackupComic>,
        localComics: List<ComicEntity>,
    ): List<Pair<BackupComic, ComicEntity>> {
        val matches = backups.mapNotNull { backup ->
            findMatchingComic(backup, localComics)?.let { backup to it }
        }
        val targetCounts = matches.groupingBy { it.second.id }.eachCount()
        return matches.filter { targetCounts[it.second.id] == 1 }
    }

    fun findMatchingListComic(item: BackupReadingListItem, localComics: List<ComicEntity>): ComicEntity? =
        findMatchingComic(
            BackupComic(
                title = item.comicTitle,
                series = item.series,
                issueNumber = item.issueNumber,
                format = "",
                pageCount = 0,
            ),
            localComics,
        )

    fun createPreview(payload: BackupPayload, currentComics: List<ComicEntity>): BackupPreview {
        val matching = matchComics(payload.comics, currentComics).size
        val skippedListItems = payload.readingLists.sumOf { list ->
            list.items.count { it.itemId == null && findMatchingListComic(it, currentComics) == null }
        }
        return BackupPreview(
            version = payload.version,
            exportedAt = payload.exportedAt,
            appVersion = payload.appVersion,
            totalComicsInBackup = payload.comics.size,
            matchingComicsCount = matching,
            skippedReadingListItemsCount = skippedListItems,
            readingListsCount = payload.readingLists.size,
            hasSettings = payload.settings != null,
        )
    }
}
