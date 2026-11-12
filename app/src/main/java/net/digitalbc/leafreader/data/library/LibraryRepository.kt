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
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxDao
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxEntity
import java.util.concurrent.atomic.AtomicLong
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinClient
import net.digitalbc.leafreader.data.jellyfin.JellyfinCredentialStore
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemsResult
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgress
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val jellyfinSyncOutboxDao: JellyfinSyncOutboxDao? = db?.jellyfinSyncOutboxDao(),
    val jellyfinClient: JellyfinClient = JellyfinClient(settings.jellyfinDeviceId),
    val jellyfinCredentialStore: JellyfinCredentialStore = JellyfinCredentialStore(context),
    val jellyfinSyncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val observationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val backupRestoreMutex = db?.backupRestoreMutex ?: Mutex()
    private val metadataMutex = Mutex()
    private val fileAvailability = ComicFileAvailability()
    private val jellyfinCoverCache = CoverBitmapCache(jellyfinSyncScope)
    private val progressSyncJobs = ConcurrentHashMap<Long, Job>()
    private val activeSessionJobs = ConcurrentHashMap.newKeySet<Job>()
    private val sessionGeneration = AtomicLong(0L)
    private val comicSyncMutexes = ConcurrentHashMap<Long, Mutex>()

    private fun getComicSyncMutex(comicId: Long): Mutex =
        comicSyncMutexes.computeIfAbsent(comicId) { Mutex() }

    private fun trackSessionJob(job: Job) {
        activeSessionJobs.add(job)
        job.invokeOnCompletion { activeSessionJobs.remove(job) }
    }

    val comics: Flow<List<ComicEntity>> = dao.observeAll()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val readableComics by lazy {
        comics.mapLatest { rows ->
            fileAvailability.retain(rows.map { it.id }.toSet())
            rows.filter { fileAvailability.isReadable(it) }
        }.stateIn(observationScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

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

    suspend fun addComicToReadingList(listId: Long, comicId: Long, sortOrder: Int = -1) = withContext(Dispatchers.IO) {
        val listDao = readingListDao ?: return@withContext
        val comic = dao.getById(comicId) ?: return@withContext
        val link = remoteComicLinkDao?.findByComicId(comicId)
        if (link?.provider == "jellyfin") {
            addRemoteToReadingList(listId,
                net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
                    JellyfinItem(link.itemId, comic.title, false, container = comic.format.lowercase(),
                        seriesName = comic.series), "Book", null),
                net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity(link.serverId, link.userId, link.itemId))
        } else {
            val items = listDao.getAllItems().filter { it.listId == listId }
            if (items.none { it.comicId == comicId }) listDao.insertItem(ReadingListItemEntity(
                listId, comicId, if (sortOrder >= 0) sortOrder else (items.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                title = comic.title))
        }
    }

    suspend fun addRemoteToReadingList(
        listId: Long, entry: net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry,
        identity: net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity,
        libraryId: String? = null,
    ) = withContext(Dispatchers.IO) {
        require(identity.itemId == entry.item.id && !entry.item.isFolder)
        val listDao = readingListDao ?: return@withContext
        val key = net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.reference(identity.serverId, identity.userId, identity.itemId)
        suspend fun add() {
            require(listDao.getById(listId) != null) { "Reading list no longer exists" }
            val items = listDao.getAllItems().filter { it.listId == listId }
            if (items.any { it.referenceKey == key }) return
            val link = remoteComicLinkDao?.findLink("jellyfin", identity.serverId, identity.userId, identity.itemId)
            val old = link?.let { found -> items.firstOrNull { it.comicId == found.comicId } }
            old?.let { listDao.removeItem(listId, it.comicId) }
            listDao.insertItem(ReadingListItemEntity(listId, link?.comicId ?: 0,
                old?.sortOrder ?: ((items.maxOfOrNull { it.sortOrder } ?: -1) + 1),
                addedAt = old?.addedAt ?: System.currentTimeMillis(), referenceKey = key,
                serverId = identity.serverId, userId = identity.userId, itemId = identity.itemId,
                libraryId = libraryId, title = entry.item.name,
                snapshot = net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.encode(entry)))
        }
        if (db != null) db.withTransaction { add() } else add()
    }

    fun observeListReferences(listId: Long): Flow<List<ReadingListItemEntity>> =
        allReadingListItems.map { rows -> rows.filter { it.listId == listId }
            .sortedWith(compareBy<ReadingListItemEntity> { it.sortOrder }.thenBy { it.addedAt }.thenBy { it.referenceKey }) }

    suspend fun editListReference(listId: Long, key: String, direction: Int? = null) = withContext(Dispatchers.IO) {
        val listDao = readingListDao ?: return@withContext
        suspend fun edit() {
            val items = listDao.getAllItems().filter { it.listId == listId }.sortedBy { it.sortOrder }.toMutableList()
            val index = items.indexOfFirst { it.referenceKey == key }
            if (index < 0) return
            val member = items.removeAt(index)
            if (direction != null) items.add((index + direction).coerceIn(0, items.size), member)
            listDao.clearItems(listId)
            items.forEachIndexed { order, item -> listDao.insertItem(item.copy(sortOrder = order)) }
        }
        if (db != null) db.withTransaction { edit() } else edit()
    }

    suspend fun removeComicFromReadingList(listId: Long, comicId: Long) = withContext(Dispatchers.IO) {
        readingListDao?.removeItem(listId, comicId)
    }

    suspend fun reorderReadingList(listId: Long, comicIdsInOrder: List<Long>) = withContext(Dispatchers.IO) {
        readingListDao?.reorderItems(listId, comicIdsInOrder)
    }

    suspend fun moveReadingListItem(listId: Long, comicId: Long, direction: Int) = withContext(Dispatchers.IO) {
        val items = readingListDao?.getAllItems()?.filter { it.listId == listId }?.sortedBy { it.sortOrder } ?: return@withContext
        val item = items.firstOrNull { it.comicId == comicId } ?: return@withContext
        editListReference(listId, item.referenceKey, direction)
    }

    suspend fun updateComicMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?) = withContext(Dispatchers.IO) {
        metadataMutex.withLock { dao.updateMetadata(id, series, volume, issueNumber) }
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
                metadataMutex.withLock {
                    val current = dao.getById(comic.id)
                    // Archive inspection may finish after the user has grouped this comic.
                    if (current != null && current.series == comic.series && current.volume == comic.volume &&
                        current.issueNumber == comic.issueNumber) {
                        dao.updateMetadata(comic.id, meta.series, meta.volume, meta.issueNumber)
                    }
                }
            }
        }
    }

    suspend fun getComic(id: Long): ComicEntity? = dao.getById(id)

    suspend fun saveProgress(id: Long, page: Int, slot: Int, explicitPlayed: Boolean? = null): Int =
        withContext(NonCancellable + Dispatchers.IO) {
            val updated = if (db != null) {
                db.withTransaction {
                    val count = dao.updateProgress(id, page, slot, System.currentTimeMillis())
                    recordJellyfinOutboxInTransaction(id, page, explicitPlayed)
                    count
                }
            } else {
                val count = dao.updateProgress(id, page, slot, System.currentTimeMillis())
                recordJellyfinOutboxInTransaction(id, page, explicitPlayed)
                count
            }
            triggerJellyfinProgressSync(id, page, explicitPlayed = explicitPlayed)
            updated
        }

    private suspend fun recordJellyfinOutboxInTransaction(
        comicId: Long,
        page: Int,
        explicitPlayed: Boolean? = null,
    ) {
        val linkDao = remoteComicLinkDao ?: return
        val outboxDao = jellyfinSyncOutboxDao ?: return
        val auth = jellyfinCredentialStore.loadAuth() ?: return
        val link = linkDao.findByComicId(comicId) ?: return
        if (link.provider == "jellyfin" && link.serverId == auth.serverId && link.userId == auth.userId) {
            val comic = dao.getById(comicId) ?: return
            val existing = outboxDao.getPendingForComic(comicId)
            val isCompleted = comic.pageCount > 0 && page >= comic.pageCount - 1
            val markPlayed = when {
                explicitPlayed != null -> explicitPlayed
                isCompleted -> true
                else -> existing?.markPlayed
            }
            val nextRevision = (existing?.revision ?: 0L) + 1L
            val now = System.currentTimeMillis()
            val nextUpdatedAt = if (existing != null && now <= existing.updatedAt) existing.updatedAt + 1L else now

            outboxDao.upsert(
                JellyfinSyncOutboxEntity(
                    id = existing?.id ?: 0L,
                    comicId = comicId,
                    serverId = auth.serverId,
                    userId = auth.userId,
                    itemId = link.itemId,
                    pageIndex = page,
                    markPlayed = markPlayed,
                    updatedAt = nextUpdatedAt,
                    revision = nextRevision,
                ),
            )
        }
    }

    fun triggerJellyfinProgressSync(
        comicId: Long,
        page: Int,
        debounceMs: Long = 1500L,
        explicitPlayed: Boolean? = null,
    ): Job? {
        if (remoteComicLinkDao == null) return null
        val auth = jellyfinCredentialStore.loadAuth() ?: return null
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) return null
        val config = runCatching { JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp) }.getOrNull() ?: return null

        val currentGen = sessionGeneration.get()

        // Cancel prior pending debounced job for this comic
        progressSyncJobs[comicId]?.cancel()

        // Debounced network flush
        val job = jellyfinSyncScope.launch {
            try {
                // Ensure outbox entry exists if triggerJellyfinProgressSync is called directly
                if (jellyfinSyncOutboxDao?.getPendingForComic(comicId) == null) {
                    recordJellyfinOutboxInTransaction(comicId, page, explicitPlayed)
                }

                if (debounceMs > 0L) {
                    delay(debounceMs)
                }
                // Check session generation (J-07): abort if logged out or switched account during debounce
                if (sessionGeneration.get() != currentGen) {
                    return@launch
                }
                val currentAuth = jellyfinCredentialStore.loadAuth()
                if (currentAuth == null || currentAuth.serverId != auth.serverId || currentAuth.userId != auth.userId) {
                    return@launch
                }

                flushJellyfinOutboxForComic(config, currentAuth, comicId, expectedGen = currentGen)
            } catch (c: CancellationException) {
                // Debounced or cancelled
            } catch (e: Exception) {
                Log.d(TAG, "Background Jellyfin progress sync skipped/failed for comic $comicId: ${e.message}")
            } finally {
                progressSyncJobs.remove(comicId, coroutineContext[Job])
            }
        }
        progressSyncJobs[comicId] = job
        trackSessionJob(job)
        return job
    }

    private suspend fun flushJellyfinOutboxForComic(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        comicId: Long,
        expectedGen: Long = sessionGeneration.get(),
    ) {
        val mutex = getComicSyncMutex(comicId)
        mutex.withLock {
            if (sessionGeneration.get() != expectedGen) return
            val outboxDao = jellyfinSyncOutboxDao ?: return
            val entry = outboxDao.getPendingForComic(comicId) ?: return
            if (entry.serverId != auth.serverId || entry.userId != auth.userId) return

            jellyfinClient.reportProgress(
                config = config,
                auth = auth,
                itemId = entry.itemId,
                pageIndex = entry.pageIndex,
            )
            if (sessionGeneration.get() != expectedGen) return
            val currentAuth = jellyfinCredentialStore.loadAuth()
            if (currentAuth == null || currentAuth.serverId != auth.serverId || currentAuth.userId != auth.userId) {
                return
            }

            if (entry.markPlayed != null) {
                jellyfinClient.markPlayedStatus(
                    config = config,
                    auth = auth,
                    itemId = entry.itemId,
                    played = entry.markPlayed,
                )
            }
            if (sessionGeneration.get() != expectedGen) return
            outboxDao.deleteIfRevisionMatches(entry.id, entry.revision)
        }
    }

    suspend fun flushJellyfinOutbox(
        config: JellyfinServerConfig? = null,
        auth: JellyfinAuth? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val activeAuth = auth ?: jellyfinCredentialStore.loadAuth() ?: return@withContext
        val activeConfig = config ?: runCatching {
            val url = settings.jellyfinServerUrl
            if (url.isBlank()) null else JellyfinServerConfig(url, settings.jellyfinAllowInsecureHttp)
        }.getOrNull() ?: return@withContext

        val outboxDao = jellyfinSyncOutboxDao ?: return@withContext
        val pending = outboxDao.getPendingForUser(activeAuth.serverId, activeAuth.userId)
        val currentGen = sessionGeneration.get()

        for (pendingItem in pending) {
            if (!coroutineContext.isActive || sessionGeneration.get() != currentGen) break
            val mutex = getComicSyncMutex(pendingItem.comicId)
            mutex.withLock {
                if (!coroutineContext.isActive || sessionGeneration.get() != currentGen) return@withLock
                // Re-fetch fresh snapshot under lock: if another sync already flushed or updated it, we get current truth
                val entry = outboxDao.getPendingForComic(pendingItem.comicId) ?: return@withLock
                if (entry.serverId != activeAuth.serverId || entry.userId != activeAuth.userId) return@withLock
                try {
                    jellyfinClient.reportProgress(
                        config = activeConfig,
                        auth = activeAuth,
                        itemId = entry.itemId,
                        pageIndex = entry.pageIndex,
                    )
                    if (!coroutineContext.isActive || sessionGeneration.get() != currentGen) return@withLock
                    val currentAuth = jellyfinCredentialStore.loadAuth()
                    if (currentAuth == null || currentAuth.serverId != activeAuth.serverId || currentAuth.userId != activeAuth.userId) {
                        return@withContext
                    }
                    if (entry.markPlayed != null) {
                        jellyfinClient.markPlayedStatus(
                            config = activeConfig,
                            auth = activeAuth,
                            itemId = entry.itemId,
                            played = entry.markPlayed,
                        )
                    }
                    if (!coroutineContext.isActive || sessionGeneration.get() != currentGen) return@withLock
                    outboxDao.deleteIfRevisionMatches(entry.id, entry.revision)
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to flush outbox item ${entry.itemId}: ${e.message}")
                    return@withContext
                }
            }
        }
    }

    suspend fun reconcileItemProgressFromRemote(item: JellyfinItem): Unit = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext
        val linkDao = remoteComicLinkDao ?: return@withContext
        val outboxDao = jellyfinSyncOutboxDao
        val link = linkDao.findLink("jellyfin", auth.serverId, auth.userId, item.id) ?: return@withContext

        val executeReconcile: suspend () -> Unit = {
            // If local user has an un-synced outbox entry, local reading state takes precedence
            if (outboxDao?.getPendingForComic(link.comicId) == null) {
                val comic = dao.getById(link.comicId)
                if (comic != null) {
                    val remotePage = if (item.played) {
                        (comic.pageCount - 1).coerceAtLeast(0)
                    } else {
                        JellyfinProgress.ticksToPage(item.playbackPositionTicks, comic.pageCount)
                    }
                    if (remotePage > comic.lastPage) {
                        dao.updateProgress(comic.id, remotePage, 0, System.currentTimeMillis())
                    }
                }
            }
        }

        if (db != null) {
            db.withTransaction { executeReconcile() }
        } else {
            executeReconcile()
        }
    }

    suspend fun syncJellyfinProgressDirect(comicId: Long, page: Int, explicitPlayed: Boolean? = null) {
        recordJellyfinOutboxInTransaction(comicId, page, explicitPlayed)
        triggerJellyfinProgressSync(comicId, page, debounceMs = 0L, explicitPlayed = explicitPlayed)?.join()
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
        try { backupRestoreMutex.withLock { applyBackupLocked(jsonString, strategy) } }
        finally { fileAvailability.invalidate(); jellyfinCoverCache.clear() }
    }

    /** Startup must complete this before readers or preference controls become available. */
    suspend fun recoverPendingBackupPreferences(): Result<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        backupRestoreMutex.withLock {
            runCatching { finishPendingBackupPreferences() }
        }
    }

    private suspend fun finishPendingBackupPreferences() {
        val pendingDao = db?.pendingBackupPreferencesDao() ?: return
        val pending = pendingDao.get() ?: return
        settings.restoreBackupPreferences(pending.settings)
        // If killed before this delete, replaying the same preference write is harmless.
        pendingDao.clear()
    }

    private suspend fun applyBackupLocked(
        jsonString: String,
        strategy: BackupConflictStrategy,
    ): Result<Int> {
        val payload = BackupManager.parseAndValidate(jsonString).getOrElse { return Result.failure(it) }

        suspend fun executeApply(): Int {
            val currentComics = dao.getAll()
            var updatedCount = 0

            for ((backup, matched) in BackupManager.matchComics(payload.comics, currentComics)) {
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
                        if (item.serverId != null && item.userId != null && item.itemId != null) {
                            val key = net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.reference(item.serverId, item.userId, item.itemId)
                            val existing = readingListDao.getAllItems().firstOrNull { it.listId == listId && it.referenceKey == key }
                            if (existing == null) {
                                val linked = remoteComicLinkDao?.findLink("jellyfin", item.serverId, item.userId, item.itemId)
                                readingListDao.insertItem(ReadingListItemEntity(listId, linked?.comicId ?: 0, item.sortOrder,
                                    referenceKey = key, serverId = item.serverId, userId = item.userId, itemId = item.itemId,
                                    libraryId = item.libraryId, title = item.comicTitle, snapshot = item.remoteSnapshot))
                            }
                            continue
                        }
                        val target = BackupManager.findMatchingListComic(item, currentComics) ?: continue
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
            payload.settings?.let {
                db?.pendingBackupPreferencesDao()?.save(
                    net.digitalbc.leafreader.data.db.PendingBackupPreferences(settings = it)
                )
            }
            return updatedCount
        }

        return try {
            // Do not let a newer restore replace an unfinished preference write.
            finishPendingBackupPreferences()
            val result = if (db != null) {
                db.withTransaction { executeApply() }
            } else {
                executeApply()
            }
            if (db != null) finishPendingBackupPreferences()
            else payload.settings?.let { settings.restoreBackupPreferences(it) }
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
            sessionGeneration.incrementAndGet()
            jellyfinCoverCache.clear()
            progressSyncJobs.values.forEach { it.cancel() }
            progressSyncJobs.clear()
            activeSessionJobs.forEach { it.cancel() }
            activeSessionJobs.clear()
            // Flush any offline pending outbox items for this user
            val bulkJob = jellyfinSyncScope.launch { flushJellyfinOutbox(config, auth) }
            trackSessionJob(bulkJob)
            Result.success(auth)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "Failed to connect to Jellyfin: ${t.message}")
            Result.failure(t)
        }
    }

    suspend fun disconnectJellyfin(): Unit = withContext(Dispatchers.IO) {
        sessionGeneration.incrementAndGet()
        jellyfinCoverCache.clear()
        progressSyncJobs.values.forEach { it.cancel() }
        progressSyncJobs.clear()
        activeSessionJobs.forEach { it.cancel() }
        activeSessionJobs.clear()
        comicSyncMutexes.clear()
        jellyfinCredentialStore.clear()
        settings.clearJellyfinConfig()
    }

    suspend fun listJellyfinViews(): Result<List<JellyfinView>> = loadJellyfinViews(allowCached = true)

    /** Launch must distinguish a reachable server from saved metadata. */
    suspend fun listJellyfinViewsForLaunch(): Result<List<JellyfinView>> = loadJellyfinViews(allowCached = false)

    private suspend fun loadJellyfinViews(allowCached: Boolean): Result<List<JellyfinView>> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(net.digitalbc.leafreader.data.jellyfin.JellyfinException(
                net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.AUTHENTICATION, "Sign in to Jellyfin"))
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) return@withContext Result.failure(IllegalStateException("No server configured"))

        try {
            val config = JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
            val views = jellyfinClient.getViews(config, auth)
            saveMetadata(auth, serverUrl, "views", net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.views(views))
            if (auth != jellyfinCredentialStore.loadAuth() || serverUrl != settings.jellyfinServerUrl)
                throw CancellationException("Jellyfin account changed")
            Result.success(views)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val cached = if (allowCached && canUseMetadataCache(t) && auth == jellyfinCredentialStore.loadAuth() &&
                serverUrl == settings.jellyfinServerUrl)
                db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, serverUrl, "views") else null
            cached?.let { runCatching { net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.views(it) } }
                ?: Result.failure(t)
        }
    }

    suspend fun listJellyfinItems(
        parentId: String?,
        startIndex: Int = 0,
        limit: Int = 100,
        syncReadingProgress: Boolean = true,
    ): Result<JellyfinItemsResult> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(IllegalStateException("Not logged in to Jellyfin"))
        val serverUrl = settings.jellyfinServerUrl
        if (serverUrl.isBlank()) return@withContext Result.failure(IllegalStateException("No server configured"))

        try {
            val config = JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
            val result = jellyfinClient.getItems(config, auth, parentId, startIndex, limit)
            if (syncReadingProgress) {
                for (item in result.items) {
                    if (!item.isFolder) {
                        reconcileItemProgressFromRemote(item)
                    }
                }
                val bulkJob = jellyfinSyncScope.launch { flushJellyfinOutbox(config, auth) }
                trackSessionJob(bulkJob)
            }
            Result.success(result)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    /** Catalogue reads never reconcile progress, flush pending writes or download files. */
    suspend fun listJellyfinCatalogue(
        query: net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery,
    ): Result<net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage> = withContext(Dispatchers.IO) {
        val generation = sessionGeneration.get()
        val auth = jellyfinCredentialStore.loadAuth()
            ?: return@withContext Result.failure(IllegalStateException("Sign in to Jellyfin"))
        val url = settings.jellyfinServerUrl
        try {
            val config = JellyfinServerConfig(url, settings.jellyfinAllowInsecureHttp)
            val page = jellyfinClient.getCatalogue(config, auth, query)
            saveMetadata(auth, url, query.url(url, auth.userId), net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.page(page))
            if (generation != sessionGeneration.get() || auth != jellyfinCredentialStore.loadAuth() ||
                url != settings.jellyfinServerUrl) throw CancellationException("Jellyfin account changed")
            Result.success(page)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val cached = if (canUseMetadataCache(error) && generation == sessionGeneration.get() &&
                auth == jellyfinCredentialStore.loadAuth() && url == settings.jellyfinServerUrl)
                db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, url, query.url(url, auth.userId)) else null
            cached?.let { runCatching { net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.page(it) } }
                ?: Result.failure(error)
        }
    }

    suspend fun cachedJellyfinCatalogue(query: net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery):
        net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage? = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
        val url = settings.jellyfinServerUrl
        val payload = db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, url, query.url(url, auth.userId))
            ?: return@withContext null
        if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl) return@withContext null
        runCatching { net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.page(payload) }.getOrNull()
    }

    suspend fun cachedJellyfinLibraries(): List<JellyfinView> = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext emptyList()
        val url = settings.jellyfinServerUrl
        val payload = db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, url, "views")
            ?: return@withContext emptyList()
        if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl)
            return@withContext emptyList()
        runCatching { net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.views(payload) }.getOrDefault(emptyList())
    }

    suspend fun restoreJellyfinCatalogue(): net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue? =
        withContext(Dispatchers.IO) {
            val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
            val url = settings.jellyfinServerUrl
            val libraries = cachedJellyfinLibraries()
            val selected = db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, url, "selected-library")
            val library = libraries.firstOrNull { it.id == selected } ?: net.digitalbc.leafreader.data.jellyfin.JellyfinLibraryEntry.automaticLibrary(libraries)
            val index = library?.let { readJellyfinIndex(it.id) }
            if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl)
                return@withContext null
            net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(libraries, library, index)
        }

    suspend fun readJellyfinIndex(libraryId: String): net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex? =
        withContext(Dispatchers.IO) {
            val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
            val url = settings.jellyfinServerUrl
            val payload = db?.jellyfinMetadataCacheDao()?.get(auth.serverId, auth.userId, url, "index:$libraryId")
                ?: return@withContext null
            if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl)
                return@withContext null
            runCatching { net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.index(payload) }.getOrNull()
        }

    suspend fun writeJellyfinIndex(libraryId: String, index: net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex,
        expectedScope: List<String>) = withContext(Dispatchers.IO) {
        val auth = jellyfinCredentialStore.loadAuth() ?: throw CancellationException("Jellyfin disconnected")
        val url = settings.jellyfinServerUrl
        if (expectedScope != listOf(url, auth.serverId, auth.userId)) throw CancellationException("Jellyfin account changed")
        // A whole snapshot replaces one private Room row atomically. No comic/list tables are modified.
        val database = db ?: return@withContext
        database.withTransaction {
            database.jellyfinMetadataCacheDao().put(net.digitalbc.leafreader.data.db.JellyfinMetadataCache(
                auth.serverId, auth.userId, url, "index:$libraryId",
                net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.index(index)))
            database.jellyfinMetadataCacheDao().put(net.digitalbc.leafreader.data.db.JellyfinMetadataCache(
                auth.serverId, auth.userId, url, "selected-library", libraryId))
        }
    }

    suspend fun resolveJellyfinFolder(libraryId: String, parentId: String): net.digitalbc.leafreader.data.jellyfin.JellyfinFolderIdentity? =
        withContext(Dispatchers.IO) {
            val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
            val url = settings.jellyfinServerUrl
            val folder = jellyfinClient.getCatalogueFolder(
                JellyfinServerConfig(url, settings.jellyfinAllowInsecureHttp), auth, libraryId, parentId)
            if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl)
                throw CancellationException("Jellyfin account changed")
            folder
        }

    private fun canUseMetadataCache(error: Throwable): Boolean {
        if (error is net.digitalbc.leafreader.data.jellyfin.JellyfinException &&
            error.kind in setOf(net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.AUTHENTICATION,
                net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.ACCESS_DENIED))
            jellyfinCoverCache.clear()
        return when (error) {
        is net.digitalbc.leafreader.data.jellyfin.JellyfinException -> error.kind in setOf(
            net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.UNAVAILABLE,
            net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.SERVER_ERROR,
            net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.TIMEOUT,
            net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.IO)
        is java.io.IOException -> true
        else -> false
        }
    }

    private suspend fun saveMetadata(auth: JellyfinAuth, url: String, key: String, payload: String) {
        if (auth != jellyfinCredentialStore.loadAuth() || url != settings.jellyfinServerUrl) return
        try {
            db?.jellyfinMetadataCacheDao()?.put(net.digitalbc.leafreader.data.db.JellyfinMetadataCache(
                auth.serverId, auth.userId, url, key, payload))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: java.lang.Exception) { /* Reading can continue if metadata caching has no storage. */ }
    }

    /** Detail reads never reconcile progress or flush the outbox. */
    suspend fun getJellyfinItemDetails(identity: JellyfinItemIdentity): Result<JellyfinItemDetails> =
        withContext(Dispatchers.IO) {
            val generation = sessionGeneration.get()
            val auth = jellyfinCredentialStore.loadAuth()
                ?: return@withContext Result.failure(IllegalStateException("Not logged in to Jellyfin"))
            if (identity.serverId != auth.serverId || identity.userId != auth.userId) {
                return@withContext Result.failure(IllegalStateException("Jellyfin account has changed"))
            }
            val serverUrl = settings.jellyfinServerUrl
            if (serverUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No server configured"))
            }
            try {
                val config = JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
                val details = jellyfinClient.getItemDetails(config, auth, identity.itemId)
                val currentAuth = jellyfinCredentialStore.loadAuth()
                if (generation != sessionGeneration.get() ||
                    currentAuth != auth || settings.jellyfinServerUrl != serverUrl
                ) {
                    throw CancellationException("Jellyfin account changed while loading details")
                }
                saveMetadata(auth, serverUrl, "detail:${identity.itemId}",
                    net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.details(details))
                if (generation != sessionGeneration.get() || auth != jellyfinCredentialStore.loadAuth() ||
                    settings.jellyfinServerUrl != serverUrl) throw CancellationException("Jellyfin account changed")
                Result.success(details)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (canUseMetadataCache(t) && generation == sessionGeneration.get() &&
                    auth == jellyfinCredentialStore.loadAuth() && serverUrl == settings.jellyfinServerUrl) {
                    val payload = db?.jellyfinMetadataCacheDao()?.get(
                        auth.serverId, auth.userId, serverUrl, "detail:${identity.itemId}")
                    if (generation != sessionGeneration.get() || auth != jellyfinCredentialStore.loadAuth() ||
                        settings.jellyfinServerUrl != serverUrl) throw CancellationException("Jellyfin account changed")
                    val saved = payload?.let { runCatching {
                        net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.details(it, identity)
                    }.getOrNull() }
                    if (saved != null) return@withContext Result.success(saved)
                    val entry = restoreJellyfinCatalogue()?.index?.entries?.firstOrNull { it.item.id == identity.itemId }
                    if (generation != sessionGeneration.get() || auth != jellyfinCredentialStore.loadAuth() ||
                        settings.jellyfinServerUrl != serverUrl) throw CancellationException("Jellyfin account changed")
                    if (entry != null) return@withContext Result.success(JellyfinItemDetails(
                        identity, entry.item, people = entry.people, fromCache = true))
                }
                Result.failure(t)
            }
        }

    fun observeDownloadedJellyfinCopies(): Flow<Map<String, ComicEntity>> {
        val auth = jellyfinCredentialStore.loadAuth() ?: return emptyFlow()
        val linkDao = remoteComicLinkDao ?: return emptyFlow()
        return kotlinx.coroutines.flow.combine(
            linkDao.observeLinksForUser("jellyfin", auth.serverId, auth.userId), comics,
        ) { links, rows ->
            withContext(Dispatchers.IO) {
                val byId = rows.associateBy { it.id }
                links.mapNotNull { link ->
                    byId[link.comicId]?.takeIf { fileAvailability.isReadable(it) }
                        ?.let { link.itemId to it }
                }.toMap()
            }
        }
    }

    fun observeDownloadedJellyfinItemIds(): Flow<Set<String>> =
        observeDownloadedJellyfinCopies().map { it.keys }

    suspend fun loadJellyfinCoverBitmap(itemId: String, imageTag: String?, maxWidth: Int = 400): Bitmap? =
        withContext(Dispatchers.IO) {
            val auth = jellyfinCredentialStore.loadAuth() ?: return@withContext null
            val serverUrl = settings.jellyfinServerUrl
            val generation = sessionGeneration.get()
            fun current() = generation == sessionGeneration.get() &&
                auth == jellyfinCredentialStore.loadAuth() && serverUrl == settings.jellyfinServerUrl
            val key = CoverBitmapCache.Key(serverUrl, auth.serverId, auth.userId, generation,
                itemId, imageTag, maxWidth)
            val bitmap = jellyfinCoverCache.get(key) {
                val linked = remoteComicLinkDao?.findLink("jellyfin", auth.serverId, auth.userId, itemId)
                val localCover = linked?.let { dao.getById(it.comicId)?.coverPath }
                val local = localCover?.let {
                    CoverPerformanceTrace.event("retained")
                    runCatching { CoverPerformanceTrace.decode { CoverImageDecoder.decodeFile(it, maxWidth) } }.getOrNull()
                        .also { bitmap -> CoverPerformanceTrace.decoded(maxWidth, bitmap) }
                }
                if (!current()) throw CancellationException("Jellyfin account changed")
                val loaded = local ?: if (serverUrl.isNotBlank()) {
                    val config = runCatching {
                        JellyfinServerConfig(serverUrl, settings.jellyfinAllowInsecureHttp)
                    }.getOrNull()
                    config?.let { jellyfinClient.loadCoverBitmap(it, auth, itemId, imageTag, maxWidth) }
                } else null
                if (!current()) throw CancellationException("Jellyfin account changed")
                loaded
            }
            if (current()) bitmap else null
        }

    /** Checks a retained copy without credentials, network traffic or progress reconciliation. */
    suspend fun getReadableJellyfinComic(identity: JellyfinItemIdentity): Result<ComicEntity?> =
        withContext(Dispatchers.IO) {
            try {
                val link = remoteComicLinkDao?.findLink(
                    "jellyfin", identity.serverId, identity.userId, identity.itemId,
                ) ?: return@withContext Result.success(null)
                val comic = dao.getById(link.comicId)
                    ?: throw java.io.IOException("Downloaded comic record is unavailable")
                val file = File(comic.filePath)
                if (!file.isFile || file.length() == 0L) {
                    throw java.io.IOException("Downloaded comic file is unavailable")
                }
                if (!fileAvailability.isReadable(comic, force = true))
                    throw java.io.IOException("Downloaded comic cannot be opened")
                Result.success(comic)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
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
        val title = if (item.name.endsWith(".$ext", ignoreCase = true)) {
            item.name.dropLast(ext.length + 1)
        } else {
            item.name
        }
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

            // Verify downloaded archive integrity before promoting
            val detectedFormat = ComicArchiveFactory.detectFormat(partFile)
            if (detectedFormat == ComicFormat.UNKNOWN) {
                partFile.delete()
                throw UnsupportedComicException("Downloaded file is not a supported comic or PDF archive")
            }
            try {
                ComicArchiveFactory.open(partFile).use { archive ->
                    if (archive.pageCount <= 0) {
                        throw UnsupportedComicException("Downloaded archive contains no readable pages")
                    }
                    // Test reading the first page to ensure archive is not truncated
                    archive.readPage(0)
                }
            } catch (e: Exception) {
                partFile.delete()
                throw UnsupportedComicException("Downloaded comic archive is corrupt or truncated: ${e.message}")
            }

            // Atomically rename .part file to final destination
            if (!partFile.renameTo(dest)) {
                partFile.copyTo(dest, overwrite = true)
                partFile.delete()
            }

            val result = indexCopiedFile(dest, coversDir, token, title, source = "jellyfin")
            if (result.isSuccess) {
                val comicId = result.getOrThrow()
                try {
                    suspend fun linkAndAttach() {
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

                    readingListDao?.getAllItems()?.filter {
                        it.serverId == auth.serverId && it.userId == auth.userId && it.itemId == item.id
                    }?.forEach { readingListDao.insertItem(it.copy(comicId = comicId)) }

                    // Seed initial progress from Jellyfin if remote position or played status exists
                    val importedComic = dao.getById(comicId)
                    if (importedComic != null && (item.playbackPositionTicks > 0L || item.played)) {
                        val initialPage = if (item.played) {
                            (importedComic.pageCount - 1).coerceAtLeast(0)
                        } else {
                            JellyfinProgress.ticksToPage(item.playbackPositionTicks, importedComic.pageCount)
                        }
                        if (initialPage > 0) {
                            dao.updateProgress(comicId, initialPage, 0, System.currentTimeMillis())
                        }
                    }
                    }
                    if (db != null) db.withTransaction { linkAndAttach() } else linkAndAttach()
                    jellyfinCoverCache.clear()
                } catch (linkEx: Throwable) {
                    // Complete rollback: remove comic from DB, remove cover, remove downloaded file
                    dao.delete(comicId)
                    dest.delete()
                    File(coversDir, "$token.jpg").delete()
                    throw linkEx
                }
            } else {
                dest.delete()
                File(coversDir, "$token.jpg").delete()
            }
            result
        } catch (t: Throwable) {
            partFile.delete()
            dest.delete()
            File(coversDir, "$token.jpg").delete()
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

        val coverFile = File(coversDir, "$token.jpg")
        val (pageCount, coverPath, parsedMeta) = try {
            ComicArchiveFactory.open(dest).use { archive ->
                val count = archive.pageCount
                if (count == 0) return@use Triple(0, null, null)
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
        } catch (e: Exception) {
            coverFile.delete()
            dest.delete()
            return Result.failure(e)
        }

        if (pageCount == 0) {
            coverFile.delete()
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
        fileAvailability.invalidate()
        jellyfinCoverCache.clear()
        return Result.success(id)
    }

    suspend fun deleteComic(id: Long) = withContext(Dispatchers.IO) {
        val comic = dao.getById(id) ?: return@withContext
        readingListDao?.getAllItems()?.filter { it.comicId == id && it.itemId != null }?.forEach {
            val snapshot = it.snapshot ?: net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.encode(
                net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
                    JellyfinItem(it.itemId!!, comic.title, false, container = comic.format.lowercase(),
                        seriesName = comic.series), "Book", null))
            readingListDao.insertItem(it.copy(comicId = 0, snapshot = snapshot, title = comic.title))
        }
        runCatching { File(comic.filePath).delete() }
        comic.coverPath?.let { runCatching { File(it).delete() } }
        dao.delete(id)
        fileAvailability.invalidate()
        jellyfinCoverCache.clear()
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
