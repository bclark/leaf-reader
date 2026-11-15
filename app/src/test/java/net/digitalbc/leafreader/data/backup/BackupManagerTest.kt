package net.digitalbc.leafreader.data.backup

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = context.getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        settings = AppSettings(prefs)
        repository = LibraryRepository(context, db.comicDao(), settings, db.readingListDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun completionRoundTripsSeparatelyFromPositionAndOldBackupsKeepLegacyDefaults() = runBlocking {
        val states = listOf(true, false, null)
        for ((index, completed) in states.withIndex()) {
            db.comicDao().insert(ComicEntity(id = index + 1L, title = "Issue $index",
                filePath = "/fixture/$index.cbz", coverPath = null, format = "CBZ", pageCount = 20,
                lastPage = if (index == 0) 0 else 19, dateAdded = 1, lastOpened = 2,
                completionOverride = completed))
        }
        val json = repository.exportBackup("0.3.7")
        assertEquals(states, BackupManager.parseAndValidate(json).getOrThrow().comics.map { it.completed })
        for (id in 1L..3L) db.comicDao().updateCompletion(id, null)
        repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).getOrThrow()
        val restored = db.comicDao().getAll().sortedBy { it.id }
        assertEquals(states, restored.map { it.completionOverride })
        assertEquals(listOf(0, 19, 19), restored.map { it.lastPage })
        assertTrue(restored[0].isCompleted)
        assertFalse(restored[0].isUnread)
        assertFalse(restored[1].isCompleted)
        assertTrue(restored[2].isCompleted)

        val legacy = org.json.JSONObject(json)
        for (i in 0 until legacy.getJSONArray("comics").length())
            legacy.getJSONArray("comics").getJSONObject(i).remove("completed")
        repository.applyBackup(legacy.toString(), BackupConflictStrategy.OVERWRITE).getOrThrow()
        assertTrue(db.comicDao().getAll().all { it.completionOverride == null })
        assertTrue(db.comicDao().getById(1)!!.isUnread)
        legacy.getJSONArray("comics").getJSONObject(0).put("completed", "invalid")
        assertTrue(BackupManager.parseAndValidate(legacy.toString()).isFailure)
    }

    @Test
    fun remoteMembersRoundTripWithoutDownloadsAndPreserveScopedIdentityAndOrder() = runBlocking {
        val list = repository.createReadingList("Remote reading order")
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            net.digitalbc.leafreader.data.jellyfin.JellyfinItem("remote", "Comic", false,
                container = "cbz", path = "/server/private/book.cbz"), "Book", null, "run", 1, 2.0)
        val identity = net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity("srv", "user", "remote")
        repository.addRemoteToReadingList(list, entry, identity, "library")
        val original = db.readingListDao().getAllItems().single()
        val json = repository.exportBackup()
        assertFalse(json.contains("/server/private"))
        assertFalse(json.contains("accessToken"))
        assertEquals(0, repository.previewBackup(json).getOrThrow().skippedReadingListItemsCount)
        val parsed = BackupManager.parseAndValidate(json).getOrThrow().readingLists.single().items.single()
        assertEquals("srv", parsed.serverId)
        assertEquals("user", parsed.userId)
        assertEquals("remote", parsed.itemId)
        repository.deleteReadingList(list)
        repository.applyBackup(json, BackupConflictStrategy.KEEP_LOCAL).getOrThrow()
        val restored = db.readingListDao().getAllItems().single()
        assertEquals(original.referenceKey, restored.referenceKey)
        assertEquals(original.sortOrder, restored.sortOrder)
        assertEquals(0L, restored.comicId)
        assertTrue(db.comicDao().getAll().isEmpty())

        val root = org.json.JSONObject(json)
        val member = root.getJSONArray("readingLists").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        member.remove("userId")
        assertTrue(BackupManager.parseAndValidate(root.toString()).isFailure)
        member.put("userId", "user").put("itemId", "mismatch")
        assertTrue(BackupManager.parseAndValidate(root.toString()).isFailure)
    }

    @Test
    fun roundTripSupportedVersionExportsAndParsesSuccessfully() = runBlocking {
        val comic1 = ComicEntity(
            id = 1,
            title = "Spider-Man #1",
            filePath = "/private/data/app/comics/spidey.cbz",
            coverPath = "/private/data/app/covers/spidey.jpg",
            format = "CBZ",
            pageCount = 30,
            rightToLeft = false,
            lastPage = 12,
            lastSlot = 0,
            series = "Spider-Man",
            volume = "1",
            issueNumber = 1f,
            dateAdded = 1000L,
            lastOpened = 2000L,
        )
        db.comicDao().insert(comic1)

        val listId = db.readingListDao().insertList(ReadingListEntity(name = "Favorites", createdAt = 500L, updatedAt = 500L))
        db.readingListDao().insertItem(ReadingListItemEntity(listId = listId, comicId = 1, sortOrder = 0, addedAt = 600L))

        settings.amoledTheme = true
        settings.pageSlideEnabled = true
        settings.libraryColumns = 3

        val json = repository.exportBackup("0.3.4")
        assertNotNull(json)

        val payloadResult = BackupManager.parseAndValidate(json)
        assertTrue(payloadResult.isSuccess)
        val payload = payloadResult.getOrThrow()

        assertEquals(1, payload.version)
        assertEquals("0.3.4", payload.appVersion)
        assertEquals(true, payload.settings?.amoledTheme)
        assertEquals(3, payload.settings?.libraryColumns)

        assertEquals(1, payload.comics.size)
        val exportedComic = payload.comics.first()
        assertEquals("Spider-Man #1", exportedComic.title)
        assertEquals("Spider-Man", exportedComic.series)
        assertEquals(1f, exportedComic.issueNumber)
        assertEquals(12, exportedComic.lastPage)
        assertEquals(30, exportedComic.pageCount)

        assertEquals(1, payload.readingLists.size)
        val exportedList = payload.readingLists.first()
        assertEquals("Favorites", exportedList.name)
        assertEquals(1, exportedList.items.size)
        assertEquals("Spider-Man #1", exportedList.items.first().comicTitle)
    }

    @Test
    fun rejectMalformedJson() {
        val malformed = "{ not a valid json at all }"
        val result = BackupManager.parseAndValidate(malformed)
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectUnsupportedVersion() {
        val jsonVersion2 = """
            {
                "version": 2,
                "exportedAt": 1000,
                "appVersion": "0.4.0",
                "comics": []
            }
        """.trimIndent()
        val result = BackupManager.parseAndValidate(jsonVersion2)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported backup version: 2") == true)
    }

    @Test
    fun rejectMissingVersion() {
        val jsonNoVersion = """
            {
                "exportedAt": 1000,
                "comics": []
            }
        """.trimIndent()
        val result = BackupManager.parseAndValidate(jsonNoVersion)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing version") == true)
    }

    @Test
    fun proveExcludedSecretsAndPathsStayExcluded() = runBlocking {
        settings.smbHost = "192.168.1.99"
        settings.smbShare = "private_share"
        settings.smbUsername = "secret_user"
        settings.smbPassword = "super_secret_password_123"

        val comic = ComicEntity(
            id = 1,
            title = "Private Comic",
            filePath = "/data/user/0/net.digitalbc.leafreader/files/comics/private_token.cbz",
            coverPath = "/data/user/0/net.digitalbc.leafreader/files/covers/private_token.jpg",
            format = "CBZ",
            pageCount = 20,
            lastPage = 5,
            dateAdded = 1000L,
            lastOpened = 2000L,
        )
        db.comicDao().insert(comic)

        val json = repository.exportBackup("0.3.4")

        // Assert strictly excluded secrets
        assertFalse("Export must not leak SMB password", json.contains("super_secret_password_123"))
        assertFalse("Export must not leak SMB username", json.contains("secret_user"))
        assertFalse("Export must not leak SMB host", json.contains("192.168.1.99"))
        assertFalse("Export must not leak SMB share", json.contains("private_share"))

        // Assert strictly excluded private paths
        assertFalse("Export must not leak app-private storage paths", json.contains("/data/user/0"))
        assertFalse("Export must not leak internal filePath keys", json.contains("filePath"))
        assertFalse("Export must not leak internal coverPath keys", json.contains("coverPath"))
        assertFalse("Export must not leak private file tokens", json.contains("private_token"))
    }

    @Test
    fun conflictStrategyOverwriteUpdatesLocalComics() = runBlocking {
        val local = ComicEntity(
            id = 1,
            title = "Batman #1",
            filePath = "/comics/batman.cbz",
            coverPath = null,
            format = "CBZ",
            pageCount = 24,
            lastPage = 2,
            lastSlot = 0,
            dateAdded = 1000L,
            lastOpened = 5000L,
        )
        db.comicDao().insert(local)

        val backupJson = """
            {
                "version": 1,
                "exportedAt": 6000,
                "appVersion": "0.3.4",
                "comics": [
                    {
                        "title": "Batman #1",
                        "format": "CBZ",
                        "pageCount": 24,
                        "lastPage": 15,
                        "lastSlot": 2,
                        "lastOpened": 4000
                    }
                ]
            }
        """.trimIndent()

        val result = repository.applyBackup(backupJson, BackupConflictStrategy.OVERWRITE)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())

        val updated = db.comicDao().getById(1)
        assertNotNull(updated)
        assertEquals(15, updated!!.lastPage)
        assertEquals(2, updated.lastSlot)
        assertEquals(4000L, updated.lastOpened)
    }

    @Test
    fun conflictStrategyKeepLocalPreservesLocalComics() = runBlocking {
        val local = ComicEntity(
            id = 1,
            title = "Batman #1",
            filePath = "/comics/batman.cbz",
            coverPath = null,
            format = "CBZ",
            pageCount = 24,
            lastPage = 2,
            lastSlot = 0,
            dateAdded = 1000L,
            lastOpened = 5000L,
        )
        db.comicDao().insert(local)

        val backupJson = """
            {
                "version": 1,
                "exportedAt": 6000,
                "appVersion": "0.3.4",
                "comics": [
                    {
                        "title": "Batman #1",
                        "format": "CBZ",
                        "pageCount": 24,
                        "lastPage": 15,
                        "lastSlot": 2,
                        "lastOpened": 4000
                    }
                ]
            }
        """.trimIndent()

        val result = repository.applyBackup(backupJson, BackupConflictStrategy.KEEP_LOCAL)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())

        val untouched = db.comicDao().getById(1)
        assertNotNull(untouched)
        assertEquals(2, untouched!!.lastPage)
        assertEquals(0, untouched.lastSlot)
        assertEquals(5000L, untouched.lastOpened)
    }

    @Test
    fun conflictStrategyMostRecentAppliesOnlyWhenBackupIsNewer() = runBlocking {
        // Comic 1: backup is newer (backup: 8000, local: 5000) -> should update
        val local1 = ComicEntity(
            id = 1,
            title = "Comic Newer in Backup",
            filePath = "/comics/c1.cbz",
            coverPath = null,
            format = "CBZ",
            pageCount = 20,
            lastPage = 3,
            dateAdded = 1000L,
            lastOpened = 5000L,
        )
        // Comic 2: local is newer (backup: 4000, local: 7000) -> should keep local
        val local2 = ComicEntity(
            id = 2,
            title = "Comic Newer Locally",
            filePath = "/comics/c2.cbz",
            coverPath = null,
            format = "CBZ",
            pageCount = 20,
            lastPage = 8,
            dateAdded = 1000L,
            lastOpened = 7000L,
        )
        db.comicDao().insert(local1)
        db.comicDao().insert(local2)

        val backupJson = """
            {
                "version": 1,
                "exportedAt": 9000,
                "appVersion": "0.3.4",
                "comics": [
                    {
                        "title": "Comic Newer in Backup",
                        "format": "CBZ",
                        "pageCount": 20,
                        "lastPage": 12,
                        "lastOpened": 8000
                    },
                    {
                        "title": "Comic Newer Locally",
                        "format": "CBZ",
                        "pageCount": 20,
                        "lastPage": 2,
                        "lastOpened": 4000
                    }
                ]
            }
        """.trimIndent()

        val result = repository.applyBackup(backupJson, BackupConflictStrategy.MOST_RECENT)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())

        val updated1 = db.comicDao().getById(1)
        assertEquals(12, updated1!!.lastPage)

        val untouched2 = db.comicDao().getById(2)
        assertEquals(8, untouched2!!.lastPage)
    }

    @Test
    fun clampingProtectsAgainstOutOfBoundsPagesInBackup() = runBlocking {
        val local = ComicEntity(
            id = 1,
            title = "Short Comic",
            filePath = "/comics/short.cbz",
            coverPath = null,
            format = "CBZ",
            pageCount = 5,
            lastPage = 1,
            dateAdded = 1000L,
            lastOpened = 2000L,
        )
        db.comicDao().insert(local)

        val backupJson = """
            {
                "version": 1,
                "exportedAt": 6000,
                "appVersion": "0.3.4",
                "comics": [
                    {
                        "title": "Short Comic",
                        "format": "CBZ",
                        "pageCount": 5,
                        "lastPage": 99,
                        "lastOpened": 5000
                    }
                ]
            }
        """.trimIndent()

        val result = repository.applyBackup(backupJson, BackupConflictStrategy.OVERWRITE)
        assertTrue(result.isSuccess)

        val clamped = db.comicDao().getById(1)
        assertEquals(4, clamped!!.lastPage) // Coerced to pageCount - 1 (5 - 1 = 4)
    }

    @Test
    fun duplicateTitlesAndPageCountsPreserveBothComicsAndSkipAmbiguousListItems() = runBlocking {
        val first = ComicEntity(
            id = 1, title = "Blue Book - Vol", filePath = "/comics/first.pdf",
            coverPath = null, format = "PDF", pageCount = 140,
            lastPage = 139, lastSlot = 2, rightToLeft = true,
            dateAdded = 1000L, lastOpened = 2000L,
        )
        val second = first.copy(
            id = 2, filePath = "/comics/second.pdf", lastPage = 138,
            lastSlot = 0, rightToLeft = false, dateAdded = 3000L, lastOpened = 4000L,
        )
        val unique = first.copy(
            id = 3, title = "Unique", filePath = "/comics/unique.pdf", pageCount = 20,
            lastPage = 5,
        )
        listOf(first, second, unique).forEach { db.comicDao().insert(it) }
        val listId = db.readingListDao().insertList(ReadingListEntity(name = "Favorites"))
        db.readingListDao().insertItem(ReadingListItemEntity(listId, first.id, 0))
        db.readingListDao().insertItem(ReadingListItemEntity(listId, unique.id, 1))
        val json = repository.exportBackup()
        db.readingListDao().clearItems(listId)

        val preview = repository.previewBackup(json).getOrThrow()
        assertEquals(1, preview.matchingComicsCount)
        assertEquals(1, preview.skippedReadingListItemsCount)
        for (strategy in BackupConflictStrategy.entries) {
            repository.applyBackup(json, strategy).getOrThrow()
            assertEquals(first, db.comicDao().getById(first.id))
            assertEquals(second, db.comicDao().getById(second.id))
        }
        assertEquals(listOf(unique.id), db.readingListDao().getAllItems().map { it.comicId })
    }

    @Test
    fun collidingBackupRecordsAreAllSkippedRegardlessOfOrder() = runBlocking {
        val comic = ComicEntity(
            id = 1, title = "Unique", filePath = "/comics/unique.cbz",
            coverPath = null, format = "CBZ", pageCount = 20,
            lastPage = 3, lastSlot = 2, dateAdded = 1000L, lastOpened = 2000L,
        )
        db.comicDao().insert(comic)
        val root = org.json.JSONObject(repository.exportBackup())
        val original = root.getJSONArray("comics").getJSONObject(0)
        val collision = org.json.JSONObject(original.toString())
            .put("title", "UNIQUE").put("pageCount", 99).put("lastPage", 15)
            .put("lastOpened", 9000L).put("rightToLeft", true)
        for (records in listOf(listOf(original, collision), listOf(collision, original))) {
            val json = root.put("comics", org.json.JSONArray(records)).toString()
            assertEquals(0, repository.previewBackup(json).getOrThrow().matchingComicsCount)
            for (strategy in BackupConflictStrategy.entries) {
                assertEquals(0, repository.applyBackup(json, strategy).getOrThrow())
                assertEquals(comic, db.comicDao().getById(comic.id))
            }
        }
    }

    @Test
    fun matchingFallsBackOnlyToUniqueCandidates() {
        val first = ComicEntity(
            id = 1, title = "Same", filePath = "/first.cbz", coverPath = null,
            format = "CBZ", pageCount = 20, series = "Series", volume = "1",
            issueNumber = 1f, dateAdded = 1000L, lastOpened = 2000L,
        )
        val second = first.copy(id = 2, filePath = "/second.cbz", pageCount = 30)
        val backup = BackupComic(title = "same", format = "CBZ", pageCount = 20)
        assertEquals(first, BackupManager.findMatchingComic(backup, listOf(first, second)))
        assertNull(BackupManager.findMatchingComic(backup.copy(pageCount = 99), listOf(first, second)))
        assertNull(BackupManager.findMatchingComic(backup, listOf(first, second.copy(pageCount = 20))))
        val renamed = backup.copy(title = "Renamed", series = "Series", volume = "1", issueNumber = 1f)
        assertEquals(first, BackupManager.findMatchingComic(renamed, listOf(first)))
        assertNull(BackupManager.findMatchingComic(renamed, listOf(first, second)))
        assertNull(BackupManager.findMatchingComic(renamed.copy(volume = "2"), listOf(first)))
        assertEquals(first, BackupManager.findMatchingListComic(
            BackupReadingListItem(comicTitle = "Same"), listOf(first),
        ))
        assertNull(BackupManager.findMatchingListComic(
            BackupReadingListItem(comicTitle = "Same"), listOf(first, second),
        ))
    }

    @Test
    fun failedRestoreRollsBackProgressListsAndPreferencesAndCanBeRetriedAfterReopen() = runBlocking {
        val databaseName = "backup-recovery-${java.util.UUID.randomUUID()}"
        db.close()
        fun openDatabase() {
            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .allowMainThreadQueries().build()
            settings = AppSettings(context.getSharedPreferences("test_settings", Context.MODE_PRIVATE))
            repository = LibraryRepository(context, db.comicDao(), settings, db.readingListDao(), db)
        }
        openDatabase()
        try {
            val comic = ComicEntity(
                id = 1, title = "Recovery", filePath = "/comics/recovery.cbz",
                coverPath = null, format = "CBZ", pageCount = 20,
                lastPage = 2, lastSlot = 1, dateAdded = 1000L, lastOpened = 2000L,
            )
            db.comicDao().insert(comic)
            val existingList = ReadingListEntity(id = 1, name = "Existing", createdAt = 100L, updatedAt = 100L)
            db.readingListDao().insertList(existingList)
            val existingItem = ReadingListItemEntity(1, 1, 0, addedAt = 100L)
            db.readingListDao().insertItem(existingItem)
            val baselineSettings = BackupManager.parseAndValidate(repository.exportBackup()).getOrThrow().settings
            val root = org.json.JSONObject(repository.exportBackup())
            root.getJSONObject("settings").put("libraryColumns", 6).put("amoledTheme", true)
            root.getJSONArray("comics").getJSONObject(0)
                .put("lastPage", 15).put("lastSlot", 3).put("lastOpened", 9000L).put("rightToLeft", true)
            root.getJSONArray("readingLists").put(org.json.JSONObject(
                """{"name":"Restored","createdAt":500,"items":[{"comicTitle":"Recovery","sortOrder":0}]}"""
            ))
            val json = root.toString()
            // Fail after progress and the new list have been written inside the transaction.
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_restore BEFORE INSERT ON reading_list_items " +
                    "WHEN NEW.listId != 1 BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END"
            )
            assertTrue(repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).isFailure)
            db.close()
            openDatabase()
            assertEquals(comic, db.comicDao().getById(1))
            assertEquals(listOf(existingList), db.readingListDao().getAllLists())
            assertEquals(listOf(existingItem), db.readingListDao().getAllItems())
            assertEquals(baselineSettings, BackupManager.parseAndValidate(repository.exportBackup()).getOrThrow().settings)

            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore")
            assertEquals(1, repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).getOrThrow())
            db.close()
            openDatabase()
            assertEquals(15, db.comicDao().getById(1)!!.lastPage)
            assertEquals(3, db.comicDao().getById(1)!!.lastSlot)
            assertTrue(db.comicDao().getById(1)!!.rightToLeft)
            assertEquals(setOf("Existing", "Restored"), db.readingListDao().getAllLists().map { it.name }.toSet())
            assertEquals(2, db.readingListDao().getAllItems().size)
            assertEquals(6, settings.libraryColumns)
            assertTrue(settings.amoledTheme)
        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun missingComicsAreSkippedWithoutCreatingFilesOrPhantomRows() = runBlocking {
        val comic = ComicEntity(
            id = 1, title = "Indexed but missing archive",
            filePath = java.io.File(context.filesDir, "absent-archive.cbz").absolutePath,
            coverPath = null, format = "CBZ", pageCount = 20,
            lastPage = 2, dateAdded = 1000L, lastOpened = 2000L,
        )
        assertFalse(java.io.File(comic.filePath).exists())
        db.comicDao().insert(comic)
        val json = """
            {"version":1,"comics":[
                {"title":"Indexed but missing archive","pageCount":20,"lastPage":8,"lastOpened":9000},
                {"title":"Not imported on this device","pageCount":30,"lastPage":12}
            ],"readingLists":[{"name":"Portable","createdAt":100,"items":[
                {"comicTitle":"Indexed but missing archive","sortOrder":0},
                {"comicTitle":"Not imported on this device","sortOrder":1}
            ]}]}
        """.trimIndent()
        val preview = repository.previewBackup(json).getOrThrow()
        assertEquals(1, preview.matchingComicsCount)
        assertEquals(1, preview.skippedReadingListItemsCount)
        assertEquals(1, repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).getOrThrow())
        assertEquals(1, db.comicDao().getAll().size)
        assertEquals(8, db.comicDao().getById(1)!!.lastPage)
        assertEquals(comic.filePath, db.comicDao().getById(1)!!.filePath)
        assertFalse(java.io.File(comic.filePath).exists())
        assertEquals(listOf(1L), db.readingListDao().getAllItems().map { it.comicId })
    }

    @Test
    fun cancellingCallerDuringRestoreStillFinishesTheTransactionAndPreferences() = runBlocking {
        val databaseName = "backup-cancellation-${java.util.UUID.randomUUID()}"
        val reachedUpdate = java.util.concurrent.CountDownLatch(1)
        val releaseUpdate = java.util.concurrent.CountDownLatch(1)
        val holdOnce = java.util.concurrent.atomic.AtomicBoolean(true)
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .setQueryCallback(object : androidx.room.RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    if (sqlQuery.startsWith("UPDATE comics SET lastPage") && holdOnce.compareAndSet(true, false)) {
                        reachedUpdate.countDown()
                        check(releaseUpdate.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    }
                }
            }, java.util.concurrent.Executor { it.run() })
            .build()
        repository = LibraryRepository(context, db.comicDao(), settings, db.readingListDao(), db)
        var restore: kotlinx.coroutines.Job? = null
        try {
            val first = ComicEntity(
                id = 1, title = "First", filePath = "/comics/first.cbz", coverPath = null,
                format = "CBZ", pageCount = 20, lastPage = 2, dateAdded = 1000L, lastOpened = 2000L,
            )
            db.comicDao().insert(first)
            db.comicDao().insert(first.copy(id = 2, title = "Second", filePath = "/comics/second.cbz"))
            val json = """
                {"version":1,"settings":{"libraryColumns":6,"amoledTheme":true},
                "comics":[
                    {"title":"First","pageCount":20,"lastPage":12,"lastOpened":9000},
                    {"title":"Second","pageCount":20,"lastPage":15,"lastOpened":9000}
                ],"readingLists":[{"name":"Restored","createdAt":100,"items":[
                    {"comicTitle":"First","sortOrder":0},{"comicTitle":"Second","sortOrder":1}
                ]}]}
            """.trimIndent()
            val operation = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).getOrThrow()
            }
            restore = operation
            assertTrue("Restore must reach its first write", reachedUpdate.await(10, java.util.concurrent.TimeUnit.SECONDS))
            operation.cancel()
            releaseUpdate.countDown()
            kotlinx.coroutines.withTimeout(10_000) { operation.join() }
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
                .allowMainThreadQueries().build()
            settings = AppSettings(context.getSharedPreferences("test_settings", Context.MODE_PRIVATE))
            assertEquals(12, db.comicDao().getById(1)!!.lastPage)
            assertEquals(15, db.comicDao().getById(2)!!.lastPage)
            assertEquals(listOf("Restored"), db.readingListDao().getAllLists().map { it.name })
            assertEquals(listOf(1L, 2L), db.readingListDao().getAllItems().sortedBy { it.sortOrder }.map { it.comicId })
            assertEquals(6, settings.libraryColumns)
            assertTrue(settings.amoledTheme)
        } finally {
            releaseUpdate.countDown()
            restore?.join()
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun preferenceSaveFailureReportsPartialRestoreAndAllowsRetry() = runBlocking {
        val prefs = context.getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        val failingPrefs = object : android.content.SharedPreferences by prefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : android.content.SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
                    override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
                    override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
                    override fun commit(): Boolean = false
                }
            }
        }
        val comic = ComicEntity(
            id = 1, title = "Retry", filePath = "/comics/retry.cbz", coverPath = null,
            format = "CBZ", pageCount = 20, lastPage = 2, dateAdded = 1000L, lastOpened = 2000L,
        )
        db.comicDao().insert(comic)
        val json = """
            {"version":1,"settings":{"libraryColumns":6},
             "comics":[{"title":"Retry","pageCount":20,"lastPage":12,"lastOpened":9000}]}
        """.trimIndent()
        val failingRepository = LibraryRepository(
            context, db.comicDao(), AppSettings(failingPrefs), db.readingListDao(), db,
        )
        val failure = failingRepository.applyBackup(json, BackupConflictStrategy.OVERWRITE)
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("Library restored, but preferences could not be saved"))
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
        assertEquals(2, settings.libraryColumns)
        repository.applyBackup(json, BackupConflictStrategy.OVERWRITE).getOrThrow()
        assertEquals(6, settings.libraryColumns)
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
    }

    @Test
    fun truncatedBackupDoesNotApplyEvenItsValidSettingsPrefix() = runBlocking {
        settings.libraryColumns = 3
        val before = BackupManager.parseAndValidate(repository.exportBackup()).getOrThrow()
        val truncated = """{"version":1,"settings":{"libraryColumns":6},"comics":["""
        assertTrue(repository.previewBackup(truncated).isFailure)
        assertTrue(repository.applyBackup(truncated, BackupConflictStrategy.OVERWRITE).isFailure)
        val after = BackupManager.parseAndValidate(repository.exportBackup()).getOrThrow()
        assertEquals(before.settings, after.settings)
        assertEquals(before.comics, after.comics)
        assertEquals(before.readingLists, after.readingLists)
    }

    @Test
    fun backupPreviewMatchesExistingComics() = runBlocking {
        db.comicDao().insert(
            ComicEntity(
                id = 1,
                title = "Existing Comic",
                filePath = "/comics/e.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 10,
                dateAdded = 1000L,
                lastOpened = 2000L,
            )
        )

        val backupJson = """
            {
                "version": 1,
                "exportedAt": 5000,
                "appVersion": "0.3.4",
                "settings": {
                    "amoledTheme": true
                },
                "comics": [
                    { "title": "Existing Comic", "format": "CBZ", "pageCount": 10 },
                    { "title": "Missing Comic", "format": "CBZ", "pageCount": 15 }
                ],
                "readingLists": [
                    { "name": "List 1", "createdAt": 1000, "items": [] }
                ]
            }
        """.trimIndent()

        val previewResult = repository.previewBackup(backupJson)
        assertTrue(previewResult.isSuccess)
        val preview = previewResult.getOrThrow()

        assertEquals(1, preview.version)
        assertEquals(2, preview.totalComicsInBackup)
        assertEquals(1, preview.matchingComicsCount)
        assertEquals(1, preview.readingListsCount)
        assertTrue(preview.hasSettings)
    }
}
