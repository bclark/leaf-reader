package net.digitalbc.leafreader.data.library

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import net.digitalbc.leafreader.data.archive.ComicArchiveFactory
import net.digitalbc.leafreader.data.archive.ComicFormat
import net.digitalbc.leafreader.data.archive.ComicMetadata
import net.digitalbc.leafreader.data.archive.ComicMetadataParser
import net.digitalbc.leafreader.data.archive.UnsupportedComicException
import net.digitalbc.leafreader.data.backup.BackupComic
import net.digitalbc.leafreader.data.backup.BackupConflictStrategy
import net.digitalbc.leafreader.data.backup.BackupManager
import net.digitalbc.leafreader.data.backup.BackupPreview
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListDao
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.db.RemoteComicLinkDao
import net.digitalbc.leafreader.data.db.RemoteComicLinkEntity
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinClient
import net.digitalbc.leafreader.data.jellyfin.JellyfinCredentialStore
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemsResult
import net.digitalbc.leafreader.data.jellyfin.JellyfinServerConfig
import net.digitalbc.leafreader.data.jellyfin.JellyfinTransferProgress
import net.digitalbc.leafreader.data.jellyfin.JellyfinView
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.data.smb.SmbClient
import net.digitalbc.leafreader.data.smb.SmbConnectionConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import net.digitalbc.leafreader.data.smb.SmbEntry
import net.digitalbc.leafreader.data.smb.SmbTransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the comic library: importing files, listing them, tracking progress, and deleting them.
 *
 * Import copies the picked file into app-private storage ([Context.getFilesDir]) so the app has a
 * stable, randomly-accessible copy regardless of the original's lifetime or URI permissions. A
 * cover thumbnail is generated from the first page at import time so the library grid is cheap to
 * render.
 */
class LibraryRepository(
    private val context: Context,
    private val dao: ComicDao,
    private val settings: AppSettings,
    private val readingListDao: ReadingListDao? = null,
    private val db: AppDatabase? = null,
    val remoteComicLinkDao: RemoteComicLinkDao? = db?.remoteComicLinkDao(),
    val jellyfinClient: JellyfinClient = JellyfinClient(settings.jellyfinDeviceId),
    val jellyfinCredentialStore: JellyfinCredentialStore = JellyfinCredentialStore(context),
) {
    val comics: Flow<List<ComicEntity>> = dao.observeAll()

    val readingLists: Flow<List<ReadingListEntity>> =
        readingListDao?.observeAll() ?: emptyFlow()

    val allReadingListItems: Flow<List<ReadingListItemEntity>> =
        readingListDao?.observeAllItems() ?: emptyFlow()

    fun observeReadingList(listId: Long): Flow<ReadingListEntity?> =
        readingListDao?.observeById(listId) ?: emptyFlow()

    fun observeComicsForList(listId: Long): Flow<List<ComicEntity>> =
        readingListDao?.observeComicsForList(listId) ?: emptyFlow()

    fun observeListCount(listId: Long): Flow<Int> =
        readingListDao?.observeItemCount(listId) ?: emptyFlow()

    suspend fun createReadingList(name: String): Long = withContext(Dispatchers.IO) {
        readingListDao?.insertList(ReadingListEntity(name = name.trim())) ?: -1L
    }

    suspend fun renameReadingList(id: Long, name: String) = withContext(Dispatchers.IO) {
        readingListDao?.updateName(id, name.trim())
    }

    suspend fun deleteReadingList(id: Long) = withContext(Dispatchers.IO) {
        readingListDao?.clearItems(id)
        readingListDao?.deleteList(id)
    }

    suspend fun addComicToReadingList(listId: Long, comicId: Long, sortOrder: Int = 0) = withContext(Dispatchers.IO) {
        readingListDao?.insertItem(ReadingListItemEntity(listId, comicId, sortOrder))
    }

    suspend fun removeComicFromReadingList(listId: Long, comicId: Long) = withContext(Dispatchers.IO) {
        readingListDao?.removeItem(listId, comicId)
    }

    suspend fun reorderReadingList(listId: Long, comicIdsInOrder: List<Long>) = withContext(Dispatchers.IO) {
        readingListDao?.reorderItems(listId, comicIdsInOrder)
    }

    suspend fun moveReadingListItem(listId: Long, comicId: Long, direction: Int) = withContext(Dispatchers.IO) {
        val items = readingListDao?.getAllItems()?.filter { it.listId == listId }?.sortedBy { it.sortOrder } ?: return@withContext
        val currentIndex = items.indexOfFirst { it.comicId == comicId }
        if (currentIndex == -1) return@withContext
        val newIndex = (currentIndex + direction).coerceIn(0, items.size - 1)
        if (newIndex == currentIndex) return@withContext
        val mutable = items.map { it.comicId }.toMutableList()
        mutable.removeAt(currentIndex)
        mutable.add(newIndex, comicId)
        readingListDao.reorderItems(listId, mutable)
    }

    suspend fun updateComicMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?) = withContext(Dispatchers.IO) {
        dao.updateMetadata(id, series, volume, issueNumber)
    }

    suspend fun backfillMetadataIfNeeded() = withContext(Dispatchers.IO) {
        val missing = dao.getComicsWithoutSeries()
        for (comic in missing) {
            var meta = ComicMetadataParser.parseFilename(comic.title)
            val archiveFile = File(comic.filePath)
            if ((meta.series == null || meta.issueNumber == null) && archiveFile.exists()) {
                val xmlMeta = runCatching {
                    ComicArchiveFactory.open(archiveFile).use { archive ->
                        archive.readComicInfoXml()?.let { xml ->
                            ComicMetadataParser.parseComicInfoXml(xml)
                        }
                    }
                }.getOrNull()
                if (xmlMeta != null) {
                    meta = ComicMetadata(
                        series = xmlMeta.series ?: meta.series,
                        volume = xmlMeta.volume ?: meta.volume,
                        issueNumber = xmlMeta.issueNumber ?: meta.issueNumber,
                    )
                }
            }
            if (meta.series != null || meta.issueNumber != null || meta.volume != null) {
                dao.updateMetadata(comic.id, meta.series, meta.volume, meta.issueNumber)
            }
        }
    }

    suspend fun getComic(id: Long): ComicEntity? = dao.getById(id)

    suspend fun saveProgress(id: Long, page: Int, slot: Int): Int =
        withContext(NonCancellable + Dispatchers.IO) {
            dao.updateProgress(id, page, slot, System.currentTimeMillis())
        }

    suspend fun updateComicPageCount(id: Long, pageCount: Int): Int =
        withContext(NonCancellable + Dispatchers.IO) {
            dao.updatePageCount(id, pageCount)
        }

    suspend fun setReadingDirection(id: Long, rightToLeft: Boolean) =
        dao.updateReadingDirection(id, rightToLeft)

    suspend fun exportBackup(appVersion: String = "0.3.4"): String = withContext(Dispatchers.IO) {
        val allComics = dao.getAll()
        val allLists = readingListDao?.getAllLists() ?: emptyList()
        val allItems = readingListDao?.getAllItems() ?: emptyList()
        val comicsById = allComics.associateBy { it.id }
        BackupManager.exportToJson(
            comics = allComics,
            readingLists = allLists,
            readingListItems = allItems,
            comicsById = comicsById,
            settings = settings,
            appVersion = appVersion,
        )
    }

    suspend fun previewBackup(jsonString: String): Result<BackupPreview> = withContext(Dispatchers.IO) {
        BackupManager.parseAndValidate(jsonString).map { payload ->
            val allComics = dao.getAll()
            BackupManager.createPreview(payload, allComics)
        }
    }

    suspend fun applyBackup(
        jsonString: String,
        strategy: BackupConflictStrategy = BackupConflictStrategy.MOST_RECENT,
    ): Result<Int> = withContext(NonCancellable + Dispatchers.IO) {
        val payload = BackupManager.parseAndValidate(jsonString).getOrElse { return@withContext Result.failure(it) }

        suspend fun executeApply(): Int {
            payload.settings?.let { s ->
                settings.defaultRightToLeft = s.defaultRightToLeft
                settings.amoledTheme = s.amoledTheme
                settings.pageSlideEnabled = s.pageSlideEnabled
                settings.pageFitMode = runCatching {
                    net.digitalbc.leafreader.ui.reader.PageFitMode.valueOf(s.pageFitMode)
                }.getOrDefault(net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE)
                settings.portraitSpread = s.portraitSpread
                settings.landscapeSpread = s.landscapeSpread
                settings.libraryColumns = s.libraryColumns
            }

            val currentComics = dao.getAll()
            var updatedCount = 0

            for (backup in payload.comics) {
                val matched = BackupManager.findMatchingComic(backup, currentComics) ?: continue
                val shouldUpdate = when (strategy) {
                    BackupConflictStrategy.OVERWRITE -> true
                    BackupConflictStrategy.KEEP_LOCAL -> false
                    BackupConflictStrategy.MOST_RECENT -> backup.lastOpened > matched.lastOpened
                }
                if (shouldUpdate) {
                    val targetPage = backup.lastPage.coerceIn(0, maxOf(0, matched.pageCount - 1))
                    val targetSlot = if (targetPage == backup.lastPage) backup.lastSlot else 0
                    dao.updateProgress(matched.id, targetPage, targetSlot, backup.lastOpened)
                    dao.updateReadingDirection(matched.id, backup.rightToLeft)
                    updatedCount++
                }
            }

            if (readingListDao != null && payload.readingLists.isNotEmpty()) {
                val existingLists = readingListDao.getAllLists().associateBy { it.name.lowercase() }
                for (backupList in payload.readingLists) {
                    val listId = existingLists[backupList.name.lowercase()]?.id
                        ?: readingListDao.insertList(
                            ReadingListEntity(
                                name = backupList.name,
                                createdAt = backupList.createdAt,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    for (item in backupList.items) {
                        val dummy = BackupComic(
                            title = item.comicTitle,
                            series = item.series,
                            issueNumber = item.issueNumber,
                            format = "",
                            pageCount = 0,
                        )
                        val target = BackupManager.findMatchingComic(dummy, currentComics) ?: continue
                        readingListDao.insertItem(
                            ReadingListItemEntity(
                                listId = listId,
                                comicId = target.id,
                                sortOrder = item.sortOrder,
                            )
                        )
                    }
                }
            }
            return updatedCount
        }

        try {
            val result = if (db != null) {
                db.withTransaction { executeApply() }
            } else {
                executeApply()
            }
            Result.success(result)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    /** Finds all supported comic files within a selected folder URI. */
    fun getUrisFromFolder(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val comicExtensions = setOf("cbz", "cbr", "pdf", "zip", "rar")
        return root.listFiles()
            .filter { doc ->
                !doc.isDirectory && doc.name?.substringAfterLast('.', "")?.lowercase() in comicExtensions
            }
            .map { it.uri }
    }

    /** Copies, validates, and indexes a picked comic. Returns the new row id, or an error. */
    suspend fun importComic(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        val comicsDir = File(context.filesDir, "comics").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

        val (displayName, totalBytes) = queryFileMeta(uri)
        val name = displayName ?: "Comic"
        val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { "cbz" }
        val title = name.substringBeforeLast('.', name)
        val dest = File(comicsDir, "$token.$ext")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    var read = 0
                    while (!isCancelled() && input.read(buf).also { read = it } >= 0) {
                        if (isCancelled()) break
                        output.write(buf, 0, read)
                        copied += read
                        if (totalBytes > 0L) {
                            onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            } ?: return@withContext Result.failure(
                UnsupportedComicException("Could not open the selected file."),
            )
            if (isCancelled()) {
                dest.delete()
                return@withContext Result.failure(CancellationException("Import cancelled"))
            }
            indexCopiedFile(dest, coversDir, token, title, source = "local")
        } catch (t: Throwable) {
            dest.delete()
            if (t is CancellationException) throw t
            Log.e(TAG, "Import failed", t)
            Result.failure(t)
        }
    }

    suspend fun listSmbDirectory(relativePath: String): Result<List<SmbEntry>> =
        withContext(Dispatchers.IO) {
            val operation = currentCoroutineContext()
            try {
                Result.success(SmbClient.listDirectory(settings, relativePath) { !operation.isActive })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    suspend fun importSmbComic(
        entry: SmbEntry,
        onProgress: (SmbTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        config: SmbConnectionConfig = SmbClient.configuration(settings),
        password: String = settings.smbPassword,
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        val comicsDir = File(context.filesDir, "comics").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val ext = entry.name.substringAfterLast('.', "").lowercase().ifEmpty { "cbz" }
        val title = entry.name.substringBeforeLast('.', entry.name)
        val dest = File(comicsDir, "$token.$ext")

        try {
            SmbClient.downloadToFile(config, password, entry, dest, onProgress, isCancelled)
            if (isCancelled()) {
                dest.delete()
                throw CancellationException("SMB import cancelled")
            }
            indexCopiedFile(dest, coversDir, token, title, source = "smb")
        } catch (t: Throwable) {
            dest.delete()
            if (t is CancellationException) throw t
            Log.e(TAG, "SMB import failed")
            Result.failure(t)
        }
    }

    private val activeJellyfinDownloads = ConcurrentHashMap.newKeySet<String>()

    fun getJellyfinAuth(): JellyfinAuth? = jellyfinCredentialStore.loadAuth()

    suspend fun connectJellyfin(
        serverUrl: String,
        allowInsecureHttp: Boolean,
        username: String,
        password: String,
    ): Result<JellyfinAuth> = withContext(Dispatchers.IO) {
        try {
            val config = JellyfinServerConfig(serverUrl, allowInsecureHttp)
            val auth = jellyfinClient.authenticate(config, username, password)
            jellyfinCredentialStore.saveAuth(auth)
            settings.jellyfinServerUrl = config.baseUrl
            settings.jellyfinAllowInsecureHttp = config.allowInsecureHttp
            settings.jellyfinUsername = auth.username
            settings.jellyfinUserId = auth.userId
            settings.jellyfinServerId = auth.serverId
            settings.jellyfinServerName = auth.serverName.orEmpty()
            Result.success(auth)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "Failed to connect to Jellyfin: ${t.message}")
            Result.failure(t)
        }
    }

    suspend fun disconnectJellyfin(): Unit = withContext(Dispatchers.IO) {
        jellyfinCredentialStore.clear()
        settings.clearJellyfinConfig()
    }

    suspend fun listJellyfinViews(): Result<List<JellyfinView>> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(IllegalStateException("Not logged in to Jellyfin"))
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) return@withContext Result.failure(IllegalStateException("No server configured"))

        try {
            val config = JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
            Result.success(jellyfinClient.getViews(config, auth))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    suspend fun listJellyfinItems(
        parentId: String?,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<JellyfinItemsResult> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(IllegalStateException("Not logged in to Jellyfin"))
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) return@withContext Result.failure(IllegalStateException("No server configured"))

        try {
            val config = JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
            Result.success(jellyfinClient.getItems(config, auth, parentId, startIndex, limit))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    fun observeDownloadedJellyfinItemIds(): Flow<Set<String>> {
        val auth = jellyfinCredentialStore.loadAuth() ?: return emptyFlow()
        val dao = remoteComicLinkDao ?: return emptyFlow()
        return dao.observeLinksForUser("jellyfin", auth.serverId, auth.userId)
            .map { links -> links.map { it.itemId }.toSet() }
    }

    suspend fun loadJellyfinCoverBitmap(itemId: String, imageTag: String?, maxWidth: Int = 400): Bitmap? =
        withContext(Dispatchers.IO) {
            val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
            val serverUrl = settings.jellyfinServerUrl
            if (serverUrl.isBlank()) return@withContext null
            val config = runCatching { JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp) }.getOrNull()
                ?: return@withContext null
            jellyfinClient.loadCoverBitmap(config, auth, itemId, imageTag, maxWidth)
        }

    suspend fun importJellyfinComic(
        item: JellyfinItem,
        onProgress: (JellyfinTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<Long> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(IllegalStateException("Not logged in to Jellyfin"))
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Jellyfin server configured"))
        }

        if (!item.isImportable) {
            return@withContext Result.failure(
                UnsupportedComicException("Format not supported for Leaf reading (e.g. EPUB or video)."),
            )
        }

        // Concurrency guard: prevent duplicate concurrent downloads of the same item
        if (!activeJellyfinDownloads.add(item.id)) {
            return@withContext Result.failure(IllegalStateException("Item is already being downloaded"))
        }

        val config = try {
            JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
        } catch (e: Exception) {
            activeJellyfinDownloads.remove(item.id)
            return@withContext Result.failure(e)
        }

        // Check if already downloaded and file exists
        val existingLink = remoteComicLinkDao?.findLink("jellyfin", auth.serverId, auth.userId, item.id)
        if (existingLink != null) {
            val existingComic = dao.getById(existingLink.comicId)
            if (existingComic != null && File(existingComic.filePath).exists()) {
                activeJellyfinDownloads.remove(item.id)
                return@withContext Result.success(existingComic.id)
            }
        }

        val token = UUID.randomUUID().toString()
        val comicsDir = File(context.filesDir, "comics").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val ext = item.fileExtension
        val title = item.name.substringBeforeLast('.', item.name)
        val partFile = File(comicsDir, "$token.part")
        val dest = File(comicsDir, "$token.$ext")

        try {
            jellyfinClient.downloadToFile(
                config = config,
                auth = auth,
                itemId = item.id,
                partFile = partFile,
                onProgress = onProgress,
                isCancelled = isCancelled,
            )

            if (isCancelled()) {
                partFile.delete()
                throw CancellationException("Jellyfin import cancelled")
            }

            // Verify downloaded archive before promoting
            val detectedFormat = ComicArchiveFactory.detectFormat(partFile)
            if (detectedFormat == ComicFormat.UNKNOWN) {
                partFile.delete()
                throw UnsupportedComicException("Downloaded file is not a supported comic or PDF archive")
            }

            // Atomically rename .part file to final destination
            if (!partFile.renameTo(dest)) {
                partFile.copyTo(dest, overwrite = true)
                partFile.delete()
            }

            val result = indexCopiedFile(dest, coversDir, token, title, source = "jellyfin")
            result.onSuccess { comicId ->
                remoteComicLinkDao?.insert(
                    RemoteComicLinkEntity(
                        comicId = comicId,
                        provider = "jellyfin",
                        serverId = auth.serverId,
                        userId = auth.userId,
                        itemId = item.id,
                        parentId = item.parentId,
                        downloadedAt = System.currentTimeMillis(),
                        remoteVersionTag = item.imageTag,
                        remoteName = item.name,
                    )
                )
            }.onFailure {
                dest.delete()
            }
            result
        } catch (t: Throwable) {
            partFile.delete()
            dest.delete()
            if (t is CancellationException) throw t
            Log.e(TAG, "Jellyfin download/import failed: ${t.message}")
            Result.failure(t)
        } finally {
            activeJellyfinDownloads.remove(item.id)
        }
    }

    private suspend fun indexCopiedFile(
        dest: File,
        coversDir: File,
        token: String,
        title: String,
        source: String = "local",
    ): Result<Long> {
        val format = ComicArchiveFactory.detectFormat(dest)
        if (format == ComicFormat.UNKNOWN) {
            dest.delete()
            return Result.failure(
                UnsupportedComicException("Not a CBZ, CBR, or PDF file."),
            )
        }

        val (pageCount, coverPath, parsedMeta) = ComicArchiveFactory.open(dest).use { archive ->
            val count = archive.pageCount
            if (count == 0) return@use Triple(0, null, null)
            val coverFile = File(coversDir, "$token.jpg")
            val saved = runCatching { saveCover(archive.readPage(0), coverFile) }
                .getOrDefault(false)
            val xml = archive.readComicInfoXml()
            val meta = if (xml != null) {
                ComicMetadataParser.parseComicInfoXml(xml)
            } else {
                ComicMetadataParser.parseFilename(title)
            }
            Triple(count, coverFile.absolutePath.takeIf { saved }, meta)
        }

        if (pageCount == 0) {
            dest.delete()
            return Result.failure(
                UnsupportedComicException("This archive has no readable pages."),
            )
        }

        val now = System.currentTimeMillis()
        val id = dao.insert(
            ComicEntity(
                title = title,
                filePath = dest.absolutePath,
                coverPath = coverPath,
                format = format.name,
                pageCount = pageCount,
                rightToLeft = settings.defaultRightToLeft,
                series = parsedMeta?.series,
                volume = parsedMeta?.volume,
                issueNumber = parsedMeta?.issueNumber,
                dateAdded = now,
                lastOpened = now,
                source = source,
            ),
        )
        return Result.success(id)
    }

    suspend fun deleteComic(id: Long) = withContext(Dispatchers.IO) {
        val comic = dao.getById(id) ?: return@withContext
        runCatching { File(comic.filePath).delete() }
        comic.coverPath?.let { runCatching { File(it).delete() } }
        dao.delete(id)
    }

    private fun saveCover(pageBytes: ByteArray, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge > 0 && longEdge / (sample * 2) >= COVER_MAX_DIMENSION) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, opts)
            ?: return false
        return try {
            dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } finally {
            bitmap.recycle()
        }
    }

    private fun queryFileMeta(uri: Uri): Pair<String?, Long> {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                name to size
            } else null to 0L
        } catch (t: Throwable) {
            null to 0L
        } finally {
            cursor?.close()
        }
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private const val COVER_MAX_DIMENSION = 600
    }
}
