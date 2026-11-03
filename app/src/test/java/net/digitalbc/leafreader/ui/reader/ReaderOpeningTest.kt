package net.digitalbc.leafreader.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import net.digitalbc.leafreader.data.archive.*
import net.digitalbc.leafreader.data.db.*
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.page.PageLoader
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.detection.NoopPanelSource
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ReaderOpeningTest {
    private val main = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(main) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    private fun image(): ByteArray = ByteArrayOutputStream().use { out ->
        Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
            compress(Bitmap.CompressFormat.PNG, 100, out)
            recycle()
        }
        out.toByteArray()
    }
    private fun archive(pages: List<ByteArray>, file: File = File.createTempFile("reader-open", ".cbz")): File {
        ZipOutputStream(file.outputStream()).use { zip -> pages.forEachIndexed { i, bytes ->
            zip.putNextEntry(ZipEntry("%04d.png".format(i))); zip.write(bytes); zip.closeEntry()
        } }
        return file
    }
    private fun dao(file: File, page: Int = 0) = FakeDao(ComicEntity(id = 1, title = "Cold open", filePath = file.path,
        coverPath = null, format = "cbz", pageCount = 8, lastPage = page, dateAdded = 0, lastOpened = 0))
    private fun reader(dao: FakeDao): ReaderViewModel {
        val context = RuntimeEnvironment.getApplication()
        return ReaderViewModel(1, LibraryRepository(context, dao, AppSettings(context)), NoopPanelSource).also {
            stores += ViewModelStore().apply { put("reader", it) }
        }
    }
    private fun await(vm: ReaderViewModel, predicate: (ReaderUiState) -> Boolean): ReaderUiState = runBlocking {
        withTimeout(10_000) { vm.state.first(predicate) }
    }
    private fun ready(vm: ReaderViewModel) = await(vm) { it.page != null && !it.loading && !it.preparingSpread && it.error == null }

    @Test fun coldCoverIsUsableEvenWhenTheNextImageIsCorrupt() {
        val vm = reader(dao(archive(listOf(image(), byteArrayOf(0, 1, 2)))))
        vm.setSpreadEnabled(true)
        assertEquals(0, ready(vm).readingPageIndex)
        runBlocking { delay(100) }
        assertNotNull(vm.state.value.page)
        assertNull(vm.state.value.error)
    }

    @Test fun coldProgressRestorationPreservesExactPageAcrossLayoutsAndReopen() {
        val dao = dao(archive(List(8) { image() }), 4)
        val vm = reader(dao)
        vm.setSpreadEnabled(true)
        val spread = await(vm) { it.isSpread && !it.loading }
        assertEquals(3, spread.pageIndex)
        assertEquals(4, spread.readingPageIndex)
        vm.setSpreadEnabled(false)
        assertEquals(4, ready(vm).pageIndex)
        runBlocking { withTimeout(5_000) { while (dao.comic.lastPage != 4) delay(10) } }
        assertEquals(4, ready(reader(dao)).readingPageIndex)
    }

    @Test fun corruptCurrentPageShowsFailureAndRetryRecovers() {
        val file = File.createTempFile("reader-broken", ".cbz").apply { writeBytes(byteArrayOf(0, 1, 2)) }
        val vm = reader(dao(file))
        assertFalse(await(vm) { it.error != null }.loading)
        archive(listOf(image()), file)
        vm.retry()
        assertNotNull(ready(vm).page)
    }

    @Test fun emptyArchiveHasAnErrorInsteadOfAnInfiniteSpinner() {
        val vm = reader(dao(archive(emptyList())))
        val failed = await(vm) { it.error != null }
        assertFalse(failed.loading)
        assertTrue(failed.error!!.contains("no readable pages"))
    }

    @Test fun stalledOpenHasABoundedFailureAndCanBeRetried() {
        val dao = dao(archive(listOf(image())))
        dao.gate = CompletableDeferred()
        val vm = reader(dao)
        main.scheduler.advanceTimeBy(30_001)
        main.scheduler.runCurrent()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.error!!.contains("taking too long"))
        dao.gate = null
        vm.retry()
        assertNotNull(ready(vm).page)
    }

    @Test fun boundsUseMetadataWithoutExtractingTheEncodedPage() = runBlocking {
        var reads = 0
        val source = object : ComicArchive, PageSizeSource {
            override val pageCount = 150
            override fun readPage(index: Int): ByteArray { reads++; error("Unexpected image extraction") }
            override fun pageName(index: Int) = "$index.png"
            override fun pageSize(index: Int) = intArrayOf(800, 1200)
            override fun close() {}
        }
        val loader = PageLoader(source)
        assertArrayEquals(intArrayOf(800, 1200), loader.pageSize(100))
        assertArrayEquals(intArrayOf(800, 1200), loader.pageSize(100))
        assertEquals(0, reads)
        loader.close()
    }

    @Test fun pageSettlementPersistsProgressPromptlyWithoutWaitingForPrefetch() {
        val dao = dao(archive(List(8) { image() }), 0)
        val vm = reader(dao)
        ready(vm)
        vm.jumpToPage(3)
        await(vm) { it.pageIndex == 3 && it.readingPageIndex == 3 }
        runBlocking { withTimeout(5_000) { while (dao.comic.lastPage != 3) delay(10) } }
        assertEquals(3, dao.comic.lastPage)
    }

    @Test fun rapidNavigationSerializesAndPersistsLatestSettledPage() {
        val dao = dao(archive(List(8) { image() }), 0)
        val vm = reader(dao)
        ready(vm)
        vm.jumpToPage(1)
        vm.jumpToPage(2)
        vm.jumpToPage(5)
        vm.jumpToPage(7)
        await(vm) { it.pageIndex == 7 && it.readingPageIndex == 7 && !it.loading }
        runBlocking { withTimeout(5_000) { while (dao.comic.lastPage != 7) delay(10) } }
        assertEquals(7, dao.comic.lastPage)
    }

    @Test fun archivePageCountChangeUpdatesDatabaseAndClampsProgress() {
        val file = archive(List(5) { image() })
        val dao = FakeDao(ComicEntity(id = 1, title = "Changed count", filePath = file.path,
            coverPath = null, format = "cbz", pageCount = 10, lastPage = 8, dateAdded = 0, lastOpened = 0))
        val vm = reader(dao)
        val state = ready(vm)
        assertEquals(5, state.pageCount)
        assertEquals(4, state.readingPageIndex)
        runBlocking { withTimeout(5_000) { while (dao.comic.pageCount != 5 || dao.comic.lastPage != 4) delay(10) } }
        assertEquals(5, dao.comic.pageCount)
        assertEquals(4, dao.comic.lastPage)
    }

    @Test fun progressPersistsAcrossViewModelClear() {
        val dao = dao(archive(List(8) { image() }), 0)
        val vm = reader(dao)
        ready(vm)
        vm.jumpToPage(4)
        await(vm) { it.pageIndex == 4 && it.readingPageIndex == 4 }
        stores.forEach { it.clear() }
        runBlocking { withTimeout(5_000) { while (dao.comic.lastPage != 4) delay(10) } }
        assertEquals(4, dao.comic.lastPage)
    }

    private class FakeDao(var comic: ComicEntity) : ComicDao {
        var gate: CompletableDeferred<Unit>? = null
        override fun observeAll() = flowOf(listOf(comic))
        override suspend fun getAll(): List<ComicEntity> = listOf(comic)
        override suspend fun getById(id: Long): ComicEntity { gate?.await(); return comic }
        override suspend fun insert(comic: ComicEntity): Long { this.comic = comic; return comic.id }
        override suspend fun updateProgress(id: Long, page: Int, slot: Int, openedAt: Long): Int {
            comic = comic.copy(lastPage = page, lastSlot = slot); return 1
        }
        override suspend fun updatePageCount(id: Long, pageCount: Int): Int {
            comic = comic.copy(pageCount = pageCount); return 1
        }
        override suspend fun updateReadingDirection(id: Long, rtl: Boolean): Int { comic = comic.copy(rightToLeft = rtl); return 1 }
        override suspend fun updateMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?) {}
        override suspend fun getComicsWithoutSeries() = emptyList<ComicEntity>()
        override fun observeBySeries(series: String, volume: String?) = flowOf(listOf(comic))
        override suspend fun delete(id: Long) = 0
    }
}
