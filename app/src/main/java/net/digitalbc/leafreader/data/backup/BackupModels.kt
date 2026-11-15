package net.digitalbc.leafreader.data.backup

enum class BackupConflictStrategy {
    OVERWRITE,
    KEEP_LOCAL,
    MOST_RECENT,
}

data class BackupSettings(
    val defaultRightToLeft: Boolean,
    val amoledTheme: Boolean,
    val pageSlideEnabled: Boolean,
    val pageFitMode: String,
    val portraitSpread: Boolean,
    val landscapeSpread: Boolean,
    val libraryColumns: Int,
)

data class BackupComic(
    val title: String,
    val series: String? = null,
    val volume: String? = null,
    val issueNumber: Float? = null,
    val format: String,
    val pageCount: Int,
    val rightToLeft: Boolean = false,
    val lastPage: Int = 0,
    val lastSlot: Int = 0,
    val dateAdded: Long = 0,
    val lastOpened: Long = 0,
    val completed: Boolean? = null,
)

data class BackupReadingListItem(
    val comicTitle: String,
    val series: String? = null,
    val issueNumber: Float? = null,
    val sortOrder: Int = 0,
    val serverId: String? = null,
    val userId: String? = null,
    val itemId: String? = null,
    val libraryId: String? = null,
    val remoteSnapshot: String? = null,
)

data class BackupReadingList(
    val name: String,
    val createdAt: Long,
    val items: List<BackupReadingListItem> = emptyList(),
)

data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String,
    val settings: BackupSettings?,
    val comics: List<BackupComic>,
    val readingLists: List<BackupReadingList>,
)

data class BackupPreview(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String,
    val totalComicsInBackup: Int,
    val matchingComicsCount: Int,
    val skippedReadingListItemsCount: Int,
    val readingListsCount: Int,
    val hasSettings: Boolean,
)
