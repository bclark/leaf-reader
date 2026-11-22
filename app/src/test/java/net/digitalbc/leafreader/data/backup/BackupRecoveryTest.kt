package net.digitalbc.leafreader.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicEntity
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
class BackupRecoveryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var settings: AppSettings
    private lateinit var databaseName: String
    private lateinit var prefsName: String

    private val backup = """
        {"version":1,"settings":{"libraryColumns":6,"amoledTheme":true,
        "defaultRightToLeft":true,"pageSlideEnabled":false,"pageFitMode":"FIT_WIDTH",
        "portraitSpread":true,"landscapeSpread":true,
        "libraryShowTitles":false,"libraryShowDetails":true,"libraryShowReadingIndicators":false},
        "comics":[{"title":"Recovery","pageCount":20,"lastPage":12,"lastSlot":2,"lastOpened":9000}],
        "readingLists":[{"name":"Recovered","createdAt":100,"items":[
        {"comicTitle":"Recovery","sortOrder":0}]}]}
    """.trimIndent()

    @Before
    fun setup(): Unit = runBlocking {
        context = RuntimeEnvironment.getApplication()
        databaseName = "restore-${java.util.UUID.randomUUID()}.db"
        prefsName = "restore-${java.util.UUID.randomUUID()}"
        prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        reopen()
        db.comicDao().insert(ComicEntity(
            id = 1, title = "Recovery", filePath = "/comics/recovery.cbz",
            coverPath = null, format = "CBZ", pageCount = 20,
            lastPage = 2, dateAdded = 1000, lastOpened = 2000,
        ))
    }

    private fun reopen() {
        if (::db.isInitialized) db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
        settings = AppSettings(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE))
    }

    private fun repository(appSettings: AppSettings = settings) =
        LibraryRepository(context, db.comicDao(), appSettings, db.readingListDao(), db)

    private fun failingSettings() = AppSettings(object : SharedPreferences by prefs {
        override fun edit(): SharedPreferences.Editor {
            val editor = prefs.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
                override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
                override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
                override fun commit(): Boolean = false
            }
        }
    })

    @After
    fun cleanup() {
        db.close()
        context.deleteDatabase(databaseName)
        prefs.edit().clear().commit()
    }

    @Test
    fun recoveryAfterDatabaseCommitNeedsNoOriginalBackupAndDoesNotReplayProgress() = runBlocking {
        val failedPreferences = failingSettings()
        val failed = repository(failedPreferences)
        assertTrue(failed.applyBackup(backup, BackupConflictStrategy.OVERWRITE).isFailure)
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
        assertEquals(1, db.readingListDao().getAllItems().size)
        assertEquals(2, settings.libraryColumns)
        assertEquals(net.digitalbc.leafreader.data.settings.LibraryAppearance(), failedPreferences.libraryAppearanceFlow.value)
        val expected = BackupManager.parseAndValidate(backup).getOrThrow().settings
        assertEquals(expected, db.pendingBackupPreferencesDao().get()!!.settings)
        assertTrue(failed.recoverPendingBackupPreferences().isFailure)
        assertNotNull(db.pendingBackupPreferencesDao().get())

        // Recreate every repository/DB/settings object at the durable crash boundary.
        reopen()
        db.comicDao().updateProgress(1, 14, 3, 10000)
        val restarted = repository()
        restarted.recoverPendingBackupPreferences().getOrThrow()
        assertNull(db.pendingBackupPreferencesDao().get())
        assertEquals(expected, BackupManager.parseAndValidate(restarted.exportBackup()).getOrThrow().settings)
        assertEquals(net.digitalbc.leafreader.data.settings.LibraryAppearance(false, true, false), settings.libraryAppearanceFlow.value)
        assertEquals(14, db.comicDao().getById(1)!!.lastPage)
        assertEquals(3, db.comicDao().getById(1)!!.lastSlot)
        settings.libraryColumns = 4
        restarted.recoverPendingBackupPreferences().getOrThrow()
        assertEquals(4, settings.libraryColumns)
        reopen()
        assertNull(db.pendingBackupPreferencesDao().get())
        assertEquals(14, db.comicDao().getById(1)!!.lastPage)
    }

    @Test
    fun recoveryAfterPreferenceCommitCanSafelyReplayUntilJournalIsCleared() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER hold_recovery BEFORE DELETE ON pending_backup_preferences " +
                "BEGIN SELECT RAISE(ABORT, 'injected death before journal cleanup'); END"
        )
        assertTrue(repository().applyBackup(backup, BackupConflictStrategy.OVERWRITE).isFailure)
        assertEquals(6, settings.libraryColumns)
        assertNotNull(db.pendingBackupPreferencesDao().get())
        reopen()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER hold_recovery")
        repository().recoverPendingBackupPreferences().getOrThrow()
        assertEquals(6, settings.libraryColumns)
        assertNull(db.pendingBackupPreferencesDao().get())
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
        assertEquals(1, db.readingListDao().getAllLists().size)
        assertEquals(1, db.readingListDao().getAllItems().size)
        reopen()
        assertNull(db.pendingBackupPreferencesDao().get())
    }

    @Test
    fun journalAndLibraryChangesRollBackTogether() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_recovery BEFORE INSERT ON pending_backup_preferences " +
                "BEGIN SELECT RAISE(ABORT, 'injected journal failure'); END"
        )
        assertTrue(repository().applyBackup(backup, BackupConflictStrategy.OVERWRITE).isFailure)
        reopen()
        assertEquals(2, db.comicDao().getById(1)!!.lastPage)
        assertTrue(db.readingListDao().getAllLists().isEmpty())
        assertTrue(db.readingListDao().getAllItems().isEmpty())
        assertNull(db.pendingBackupPreferencesDao().get())
        assertEquals(2, settings.libraryColumns)
    }

    @Test
    fun newerRestoreCannotReplacePendingPreferencesWhileRecoveryFails() = runBlocking {
        val failed = repository(failingSettings())
        assertTrue(failed.applyBackup(backup, BackupConflictStrategy.OVERWRITE).isFailure)
        val newer = backup.replace("\"libraryColumns\":6", "\"libraryColumns\":3")
            .replace("\"lastPage\":12", "\"lastPage\":15")
        assertTrue(failed.applyBackup(newer, BackupConflictStrategy.OVERWRITE).isFailure)
        assertEquals(6, db.pendingBackupPreferencesDao().get()!!.settings.libraryColumns)
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
        reopen()
        repository().recoverPendingBackupPreferences().getOrThrow()
        assertEquals(6, settings.libraryColumns)
        assertEquals(12, db.comicDao().getById(1)!!.lastPage)
    }
}
