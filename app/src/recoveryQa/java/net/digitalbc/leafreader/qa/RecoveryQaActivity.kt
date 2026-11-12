package net.digitalbc.leafreader.qa

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.widget.TextView
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.backup.BackupConflictStrategy
import net.digitalbc.leafreader.data.backup.BackupManager
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ADB-driven, real-process recovery checks, compiled only into the isolated recoveryQa variant.
 * seed pauses in the real repository's preference commit; kill the app, start MainActivity, verify.
 */
class RecoveryQaActivity : Activity() {
    private lateinit var status: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backup = """
        {"version":1,"settings":{"libraryColumns":6,"amoledTheme":true,
        "defaultRightToLeft":true,"pageSlideEnabled":false,"pageFitMode":"FIT_WIDTH",
        "portraitSpread":true,"landscapeSpread":true},
        "comics":[{"title":"Recovery QA fixture","pageCount":20,"lastPage":12,"lastSlot":2,"lastOpened":9000}],
        "readingLists":[{"name":"Recovery QA list","createdAt":100,"items":[
        {"comicTitle":"Recovery QA fixture","sortOrder":0}]}]}
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "net.digitalbc.leafreader.recoveryqa") { "Isolated QA package required" }
        status = TextView(this).apply {
            textSize = 22f
            setPadding(40, 140, 40, 40)
            text = "Recovery QA running"
        }
        setContentView(status)
        val operation = intent.getStringExtra("operation") ?: "verify"
        val stage = intent.getStringExtra("stage") ?: "BEFORE_PREFS"
        scope.launch {
            runCatching {
                when (operation) {
                    "seed" -> seedAndHold(stage)
                    "verify" -> verifyRecovered()
                    else -> error("Unknown QA operation")
                }
            }.onFailure { show("FAIL: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun show(message: String) = runOnUiThread { status.text = message }

    private suspend fun seedAndHold(stage: String) {
        check(stage == "BEFORE_PREFS" || stage == "AFTER_PREFS")
        val app = application as ComicReaderApp
        val db = app.database
        // This package is disposable. The production package/DB is never opened here.
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("DELETE FROM reading_list_items")
            db.openHelper.writableDatabase.execSQL("DELETE FROM reading_lists")
            db.openHelper.writableDatabase.execSQL("DELETE FROM pending_backup_preferences")
            db.comicDao().getAll().forEach { db.comicDao().delete(it.id) }
            db.comicDao().insert(ComicEntity(
                id = 1, title = "Recovery QA fixture", filePath = "/qa-only/fixture.cbz",
                coverPath = null, format = "CBZ", pageCount = 20,
                lastPage = 2, dateAdded = 1000, lastOpened = 2000,
            ))
        }
        val prefs = getSharedPreferences("leaf.settings", Context.MODE_PRIVATE)
        check(prefs.edit().clear().putInt("library_columns", 2).commit())
        fun hold() {
            runBlocking {
                check(db.comicDao().getById(1)!!.lastPage == 12)
                check(db.pendingBackupPreferencesDao().get() != null)
                check(db.readingListDao().getAllItems().size == 1)
            }
            show("READY ${stage} pid=${Process.myPid()} page=12 journal=1 columns=${prefs.getInt("library_columns", 2)}")
            check(CountDownLatch(1).await(120, TimeUnit.SECONDS)) { "QA pause timed out; kill/restart the QA package" }
        }
        val gatedPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val editor = prefs.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
                    override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
                    override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
                    override fun commit(): Boolean {
                        if (stage == "BEFORE_PREFS") hold()
                        val saved = editor.commit()
                        if (stage == "AFTER_PREFS" && saved) hold()
                        return saved
                    }
                }
            }
        }
        LibraryRepository(this, db.comicDao(), AppSettings(gatedPrefs), db.readingListDao(), db)
            .applyBackup(backup, BackupConflictStrategy.OVERWRITE).getOrThrow()
        error("Expected the host to kill this paused process")
    }

    private suspend fun verifyRecovered() {
        val app = application as ComicReaderApp
        val db = app.database
        val comic = checkNotNull(db.comicDao().getById(1))
        check(comic.lastPage == 12 && comic.lastSlot == 2 && comic.lastOpened == 9000L)
        check(db.readingListDao().getAllLists().single().name == "Recovery QA list")
        check(db.readingListDao().getAllItems().single().comicId == comic.id)
        check(db.pendingBackupPreferencesDao().get() == null) { "Journal still pending" }
        val repository = app.libraryRepository
        val expected = BackupManager.parseAndValidate(backup).getOrThrow().settings
        check(BackupManager.parseAndValidate(repository.exportBackup()).getOrThrow().settings == expected)
        // verify must not perform recovery itself: MainActivity must already have done it.
        show("PASS recovery pid=${Process.myPid()} page=12 slot=2 list=1 journal=0 preferences=7/7")
    }
}
