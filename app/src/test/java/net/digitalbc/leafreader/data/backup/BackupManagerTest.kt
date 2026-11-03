package net.digitalbc.leafreader.data.backup

import android.content.Context
import androidx.room.Room
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
