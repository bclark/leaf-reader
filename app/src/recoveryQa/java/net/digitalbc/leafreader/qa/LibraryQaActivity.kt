package net.digitalbc.leafreader.qa

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.jellyfin.*
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.ui.library.ProgressConflictDialog
import net.digitalbc.leafreader.ui.library.JellyfinDetailScreen
import net.digitalbc.leafreader.ui.library.JellyfinDetailState
import net.digitalbc.leafreader.ui.library.coverBitmapCache
import org.json.JSONObject
import org.json.JSONArray
import net.digitalbc.leafreader.data.db.RemoteComicLinkEntity
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxEntity
import net.digitalbc.leafreader.ui.nav.AppNavHost
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Production UI with synthetic local data. Never included in a release or owner package. */
open class LibraryQaActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val width = when (javaClass.simpleName) { "LibraryWideQaActivity" -> 840; "LibraryMediumQaActivity" -> 600; "LibraryPhoneQaActivity", "LibraryLargeQaActivity" -> 390; else -> 0 }
        val font = if (javaClass.simpleName == "LibraryLargeQaActivity") 2f else 1f
        val metrics = newBase.resources.displayMetrics
        val config = Configuration(newBase.resources.configuration).apply {
            fontScale = font
            if (width > 0) {
                densityDpi = (metrics.widthPixels.toFloat() / width * 160).toInt()
                screenWidthDp = width
                screenHeightDp = (metrics.heightPixels.toFloat() / densityDpi * 160).toInt()
            }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "net.digitalbc.leafreader.recoveryqa")
        enableEdgeToEdge()
        // These options affect this synthetic Activity only, never device settings.
        if (intent.getBooleanExtra("spanish", false)) {
            resources.updateConfiguration(Configuration(resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag("es-MX"))
            }, resources.displayMetrics)
        }
        val scenario = intent.getStringExtra("scenario") ?: "mixed"
        check(scenario in setOf("mixed", "started", "completed", "empty", "conflict", "download", "benchmark", "metadata"))
        val app = application as ComicReaderApp
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { seed(app, scenario) }
            setContent {
                    ComicReaderTheme(mode = if (intent.getBooleanExtra("dark", false)) ThemeMode.DARK else ThemeMode.LIGHT, amoled = intent.getBooleanExtra("amoled", false)) {
                        Surface(Modifier.fillMaxSize()) {
                            when (scenario) {
                                "conflict" -> ConflictFixture()
                                "download" -> DownloadFixture(app)
                                else -> AppNavHost()
                            }
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("operation") == "census") {
            val bitmaps = coverBitmapCache.snapshot().values.map { it.asAndroidBitmap() }
            val groups = bitmaps.groupingBy { "${it.width}x${it.height}:${it.allocationByteCount}" }.eachCount()
            File(filesDir, "native-cover-census.json").writeText(JSONObject()
                .put("cachedImages", bitmaps.size).put("residentBytes", coverBitmapCache.size())
                .put("maximumBytes", coverBitmapCache.maxSize()).put("bitmapGroups", JSONObject(groups))
                .put("syntheticDatasetImages", 60).toString())
        }
    }

    @Composable private fun ConflictFixture() {
        var result by remember { mutableStateOf<String?>(null) }
        val conflict = JellyfinProgressConflict(1, "Synthetic conflict fixture", JellyfinItemIdentity("qa", "qa", "qa"),
            "http://127.0.0.1", 20, 4, false, 1, 1, JellyfinProgressSnapshot(20_000, true))
        result?.let { Text(it) } ?: ProgressConflictDialog(conflict,
            resolving = intent.getBooleanExtra("busy", false),
            error = if (intent.getBooleanExtra("error", false)) "Synthetic retry failure" else null,
            onChoice = { result = "Selected ${it.name}" }, onDefer = { result = "Deferred; both positions retained" })
    }

    @Composable private fun DownloadFixture(app: ComicReaderApp) {
        var loaded by remember { mutableStateOf(false) }
        var comic by remember { mutableStateOf<ComicEntity?>(null) }
        var result by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val identity = JellyfinItemIdentity("qa-server", "qa-user", "qa-item")
        val item = JellyfinItem("qa-item", "Synthetic retained download", false, container = "cbz")
        LaunchedEffect(Unit) {
            comic = withContext(Dispatchers.IO) { app.database.comicDao().getAll().single() }
            loaded = true
        }
        if (!loaded) Text("Loading synthetic download")
        else if (result != null) Text(result!!)
        else JellyfinDetailScreen(JellyfinDetailState(item, identity, loading = false,
            details = JellyfinItemDetails(identity, item), localComic = comic),
            onRead = { result = "Synthetic Read selected" }, importing = false, importProgress = null,
            importDetail = null, onBack = { finish() }, onRetry = {}, onDownload = { result = "Synthetic Download selected" },
            onCancel = {}, cover = { _, _ -> }, onRemoveDownload = {
                scope.launch {
                    val before = checkNotNull(comic)
                    app.libraryRepository.removeJellyfinDownload(identity).getOrThrow()
                    withContext(Dispatchers.IO) {
                        val retained = checkNotNull(app.database.comicDao().getById(before.id))
                        check(retained.lastPage == 4 && retained.completionOverride == true)
                        check(app.database.jellyfinSyncOutboxDao().getPendingForComic(before.id)?.pageIndex == 4)
                        check(app.database.readingListDao().getAllItems().single().comicId == before.id)
                        check(!File(before.filePath).exists())
                        File(filesDir, "native-retention-result.txt").writeText("PASS archive removed; page4/readtrue/list1/pending4 retained")
                    }
                    comic = null
                }
            })
    }

    private suspend fun seed(app: ComicReaderApp, scenario: String) {
        val db = app.database
        // Only this disposable package's sandbox is touched. No network endpoint or credentials are configured.
        app.settings.jellyfinServerUrl = ""
        app.settings.libraryColumns = 3
        db.withTransaction {
            db.openHelper.writableDatabase.execSQL("DELETE FROM reading_list_items")
            db.openHelper.writableDatabase.execSQL("DELETE FROM reading_lists")
            db.comicDao().getAll().forEach { db.comicDao().delete(it.id) }
            if (scenario == "empty" || scenario == "conflict") return@withTransaction
            val fixtures = File(filesDir, "library-qa").apply { mkdirs() }
            val list = db.readingListDao().insertList(ReadingListEntity(name = "Synthetic reading order"))
            repeat(if (scenario == "download") 1 else if (scenario == "benchmark") 60 else if (scenario == "metadata") 2 else 9) { index ->
                val bitmap = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).apply {
                    drawColor(Color.rgb(35 + index * 18, 70 + index * 10, 95))
                    drawText("LEAF QA ${index + 1}", 18f, 170f, Paint().apply { color = Color.WHITE; textSize = 32f })
                }
                val cover = File(if (scenario == "download") File(filesDir, "covers").apply { mkdirs() } else fixtures, "cover-$index.jpg")
                cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                bitmap.recycle()
                val archive = File(if (scenario == "download") File(filesDir, "comics").apply { mkdirs() } else fixtures, "comic-$index.cbz")
                ZipOutputStream(archive.outputStream()).use { zip -> repeat(10) { page ->
                    zip.putNextEntry(ZipEntry("page-$page.png")); zip.write(bytes); zip.closeEntry()
                }
                    if (scenario == "metadata") {
                        zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                        zip.write("<ComicInfo><Series>QA &amp; Mañana &#x1F343;</Series><Number>${index + 1}</Number></ComicInfo>".toByteArray())
                        zip.closeEntry()
                    }
                }
                val missing = scenario == "mixed" && index == 8
                val completed = scenario == "download" || scenario == "completed" || (scenario == "mixed" && index % 3 == 2)
                val page = when { scenario == "download" -> 4; completed -> 9; scenario == "started" -> 2; scenario == "mixed" && index % 3 == 1 -> 2; else -> 0 }
                val id = if (scenario == "metadata") app.libraryRepository.importComic(android.net.Uri.fromFile(archive)).getOrThrow()
                    else db.comicDao().insert(ComicEntity(title = "QA issue ${index + 1}",
                    filePath = if (missing) File(fixtures, "absent.cbz").path else archive.path,
                    coverPath = cover.path, format = "CBZ", pageCount = 10, lastPage = page,
                    source = if (scenario == "download") "jellyfin" else "local",
                    series = if (scenario == "benchmark") "QA Series ${(index / 3 + 1).toString().padStart(3, '0')}" else "Synthetic Series ${index / 3 + 1}", issueNumber = (index % 3 + 1).toFloat(),
                    dateAdded = 1000 + index.toLong(), lastOpened = 2000 + index.toLong(), completionOverride = completed))
                if (scenario == "metadata") {
                    val imported = checkNotNull(db.comicDao().getById(id))
                    check(imported.series == "QA & Mañana 🍃" && imported.issueNumber == index + 1f)
                    check(!imported.isCompleted && imported.isUnread)
                    File(filesDir, "native-metadata-result.txt").writeText("PASS fresh import: predefined/numeric/Unicode decoded exactly; stored Series and issue; unread/completion separate")
                }
                if (scenario == "download") {
                    db.remoteComicLinkDao().insert(RemoteComicLinkEntity(comicId = id, serverId = "qa-server", userId = "qa-user", itemId = "qa-item"))
                    db.jellyfinSyncOutboxDao().upsert(JellyfinSyncOutboxEntity(comicId = id, serverId = "qa-server", userId = "qa-user", itemId = "qa-item", pageIndex = 4, markPlayed = true))
                }
                if (index < 3) db.readingListDao().insertItem(ReadingListItemEntity(listId = list, comicId = id, sortOrder = index))
            }
        }
    }
}

class LibraryPhoneQaActivity : LibraryQaActivity()
class LibraryLargeQaActivity : LibraryQaActivity()
class LibraryMediumQaActivity : LibraryQaActivity()
class LibraryWideQaActivity : LibraryQaActivity()
