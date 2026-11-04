package net.digitalbc.leafreader.ui.library

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListDao
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    private lateinit var fakeComicDao: FakeComicDao
    private lateinit var fakeReadingListDao: FakeReadingListDao
    private lateinit var repository: LibraryRepository
    private lateinit var settings: AppSettings
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeComicDao = FakeComicDao()
        fakeReadingListDao = FakeReadingListDao().apply {
            comicsLookup = { fakeComicDao.getAllDirect() }
        }
        settings = AppSettings(context)
        repository = LibraryRepository(
            context = context,
            dao = fakeComicDao,
            settings = settings,
            readingListDao = fakeReadingListDao,
        )
        viewModel = LibraryViewModel(repository, settings).also {
            stores += ViewModelStore().apply { put("library", it) }
        }
    }

    @After
    fun cleanup() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun createComic(
        id: Long,
        title: String,
        series: String? = null,
        volume: String? = null,
        issueNumber: Float? = null,
        pageCount: Int = 20,
        lastPage: Int = 0,
    ) = ComicEntity(
        id = id,
        title = title,
        filePath = "/test/$title.cbz",
        coverPath = null,
        format = "CBZ",
        pageCount = pageCount,
        lastPage = lastPage,
        dateAdded = 1000L * id,
        lastOpened = 1000L * id,
        series = series,
        volume = volume,
        issueNumber = issueNumber,
    )

    @Test
    fun groupingMultipleIssuesIntoSeriesStackInAllFilter() = runBlocking {
        val c1 = createComic(1, "Ultimate Spider-Man 01", series = "Ultimate Spider-Man", issueNumber = 1f)
        val c2 = createComic(2, "Ultimate Spider-Man 02", series = "Ultimate Spider-Man", issueNumber = 2f)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals("Ultimate Spider-Man", stack.seriesName)
        assertEquals(2, stack.totalIssues)
        assertEquals(1L, stack.issues[0].id)
        assertEquals(2L, stack.issues[1].id)
    }

    @Test
    fun singleIssueRemainsSingleComicInAllFilter() = runBlocking {
        val c1 = createComic(1, "Batman Year One 01", series = "Batman", issueNumber = 1f)
        val c2 = createComic(2, "Watchmen Complete", series = null)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(2, items.size)
        assertTrue(items[0] is LibraryItem.SingleComic)
        assertTrue(items[1] is LibraryItem.SingleComic)
    }

    @Test
    fun seriesFilterReturnsAllSeriesStacksSorted() = runBlocking {
        val c1 = createComic(1, "X-Men 01", series = "X-Men", issueNumber = 1f)
        val c2 = createComic(2, "Avengers 01", series = "Avengers", issueNumber = 1f)
        val c3 = createComic(3, "Avengers 02", series = "Avengers", issueNumber = 2f)
        fakeComicDao.setComics(listOf(c1, c2, c3))

        viewModel.setLibraryFilter(LibraryFilter.SERIES)

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.size == 2 }
        }

        assertEquals(2, items.size)
        val s1 = items[0] as LibraryItem.SeriesStack
        val s2 = items[1] as LibraryItem.SeriesStack
        assertEquals("Avengers", s1.seriesName)
        assertEquals("X-Men", s2.seriesName)
    }

    @Test
    fun unreadFilterOnlyReturnsComicsWithLastPageZero() = runBlocking {
        val unread = createComic(1, "Issue 1", pageCount = 20, lastPage = 0)
        val read = createComic(2, "Issue 2", pageCount = 20, lastPage = 19)
        fakeComicDao.setComics(listOf(unread, read))

        viewModel.setLibraryFilter(LibraryFilter.UNREAD)

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val single = items[0] as LibraryItem.SingleComic
        assertEquals(1L, single.comic.id)
    }

    @Test
    fun readingListsFilterReturnsReadingListItems() = runBlocking {
        val c1 = createComic(1, "Comic 1")
        val c2 = createComic(2, "Comic 2")
        fakeComicDao.setComics(listOf(c1, c2))

        val list = ReadingListEntity(id = 10, name = "Favorites", createdAt = 1000L, updatedAt = 1000L)
        val item1 = ReadingListItemEntity(listId = 10, comicId = 1, sortOrder = 0)
        val item2 = ReadingListItemEntity(listId = 10, comicId = 2, sortOrder = 1)
        fakeReadingListDao.setListsAndItems(listOf(list), listOf(item1, item2))

        viewModel.setLibraryFilter(LibraryFilter.READING_LISTS)

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val listItem = items[0] as LibraryItem.ReadingListItem
        assertEquals("Favorites", listItem.readingList.name)
        assertEquals(2, listItem.itemCount)
        assertEquals(2, listItem.previewComics.size)
    }

    @Test
    fun searchQueryFiltersBothTitleAndSeries() = runBlocking {
        val c1 = createComic(1, "Astonishing Tales 01", series = "X-Men", issueNumber = 1f)
        val c2 = createComic(2, "Amazing Fantasy 15", series = "Spider-Man", issueNumber = 15f)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.setSearchQuery("tales")
        var items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }
        assertEquals(1, items.size)
        assertEquals(1L, (items[0] as LibraryItem.SingleComic).comic.id)

        viewModel.setSearchQuery("spider-man")
        items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }
        assertEquals(1, items.size)
        assertEquals(2L, (items[0] as LibraryItem.SingleComic).comic.id)
    }

    @Test
    fun topCoverComicResolvesToFirstUnreadOrInProgress() = runBlocking {
        val c1 = createComic(1, "Issue 01", series = "Saga", issueNumber = 1f, pageCount = 20, lastPage = 19) // Completed
        val c2 = createComic(2, "Issue 02", series = "Saga", issueNumber = 2f, pageCount = 20, lastPage = 5)  // In-progress
        val c3 = createComic(3, "Issue 03", series = "Saga", issueNumber = 3f, pageCount = 20, lastPage = 0)  // Unread
        fakeComicDao.setComics(listOf(c1, c2, c3))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals(2L, stack.topCoverComic.id)
    }

    @Test
    fun topCoverComicFallsBackToFirstIssueWhenAllCompleted() = runBlocking {
        val c1 = createComic(1, "Issue 01", series = "Saga", issueNumber = 1f, pageCount = 20, lastPage = 19)
        val c2 = createComic(2, "Issue 02", series = "Saga", issueNumber = 2f, pageCount = 20, lastPage = 19)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals(1L, stack.topCoverComic.id)
        assertEquals(1.0f, stack.progressFraction, 0.001f)
    }

    @Test
    fun markSeriesReadAndUnreadOperations() = runBlocking {
        val c1 = createComic(1, "Invincible 01", series = "Invincible", issueNumber = 1f, pageCount = 25, lastPage = 0)
        val c2 = createComic(2, "Invincible 02", series = "Invincible", issueNumber = 2f, pageCount = 25, lastPage = 0)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.markSeriesRead("Invincible", null, true)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 24 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        var all = fakeComicDao.getAll()
        assertTrue(all.all { it.lastPage == 24 })

        viewModel.markSeriesRead("Invincible", null, false)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 0 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        all = fakeComicDao.getAll()
        assertTrue(all.all { it.lastPage == 0 })
    }

    @Test
    fun updateComicsSeriesUpdatesMetadata() = runBlocking {
        val c1 = createComic(1, "Comic 01", series = null)
        val c2 = createComic(2, "Comic 02", series = null)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.updateComicsSeries(listOf(1L, 2L), "New Mutants", "Vol 1")
        withTimeout(3000) {
            while (fakeComicDao.getAll().find { it.id == 1L }?.series != "New Mutants") {
                kotlinx.coroutines.delay(10)
            }
        }
        val all = fakeComicDao.getAll()
        assertEquals("New Mutants", all.find { it.id == 1L }?.series)
        assertEquals("Vol 1", all.find { it.id == 1L }?.volume)
        assertEquals("New Mutants", all.find { it.id == 2L }?.series)
        assertEquals("Vol 1", all.find { it.id == 2L }?.volume)
    }

    @Test
    fun readingListCreationAndItemInsertion() = runBlocking {
        val c1 = createComic(1, "Infinity Gauntlet 01")
        val c2 = createComic(2, "Silver Surfer 34")
        fakeComicDao.setComics(listOf(c1, c2))

        var createdId = 0L
        viewModel.createReadingList("Cosmic Marvel") { id ->
            createdId = id
        }

        withTimeout(3000) {
            while (fakeReadingListDao.getAllLists().isEmpty()) {
                kotlinx.coroutines.delay(10)
            }
        }
        val list = fakeReadingListDao.getAllLists().first()
        assertEquals("Cosmic Marvel", list.name)

        viewModel.addComicsToReadingList(list.id, listOf(1L, 2L))
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().size < 2) {
                kotlinx.coroutines.delay(10)
            }
        }
        val items = fakeReadingListDao.getAllItems().filter { it.listId == list.id }
        assertEquals(2, items.size)
    }

    @Test
    fun readingListReorderingUpdatesSortOrder() = runBlocking {
        val c1 = createComic(1, "Issue 1")
        val c2 = createComic(2, "Issue 2")
        val c3 = createComic(3, "Issue 3")
        fakeComicDao.setComics(listOf(c1, c2, c3))

        val list = ReadingListEntity(1L, "Storyline")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(
                ReadingListItemEntity(1L, 1L, sortOrder = 0),
                ReadingListItemEntity(1L, 2L, sortOrder = 1),
                ReadingListItemEntity(1L, 3L, sortOrder = 2),
            )
        )

        viewModel.moveReadingListItem(1L, 3L, -1)
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().find { it.comicId == 3L }?.sortOrder != 1) {
                kotlinx.coroutines.delay(10)
            }
        }
        val reordered = fakeReadingListDao.getAllItems().filter { it.listId == 1L }.sortedBy { it.sortOrder }
        assertEquals(listOf(1L, 3L, 2L), reordered.map { it.comicId })

        viewModel.reorderReadingList(1L, listOf(2L, 1L, 3L))
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().find { it.comicId == 2L }?.sortOrder != 0) {
                kotlinx.coroutines.delay(10)
            }
        }
        val finalOrder = fakeReadingListDao.getAllItems().filter { it.listId == 1L }.sortedBy { it.sortOrder }
        assertEquals(listOf(2L, 1L, 3L), finalOrder.map { it.comicId })
    }

    @Test
    fun readingListMarkAllReadAndUnread() = runBlocking {
        val c1 = createComic(1, "Event 01", pageCount = 30, lastPage = 0)
        val c2 = createComic(2, "Event 02", pageCount = 20, lastPage = 0)
        fakeComicDao.setComics(listOf(c1, c2))

        val list = ReadingListEntity(1L, "Crossover")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(
                ReadingListItemEntity(1L, 1L, sortOrder = 0),
                ReadingListItemEntity(1L, 2L, sortOrder = 1),
            )
        )

        viewModel.markReadingListRead(1L, true)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage > 0 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        var all = fakeComicDao.getAll()
        assertEquals(29, all.find { it.id == 1L }?.lastPage)
        assertEquals(19, all.find { it.id == 2L }?.lastPage)

        viewModel.markReadingListRead(1L, false)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 0 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        all = fakeComicDao.getAll()
        assertEquals(0, all.find { it.id == 1L }?.lastPage)
        assertEquals(0, all.find { it.id == 2L }?.lastPage)
    }

    @Test
    fun readingListRenameAndDelete() = runBlocking {
        val list = ReadingListEntity(1L, "Old Title")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(ReadingListItemEntity(1L, 10L, 0))
        )

        viewModel.renameReadingList(1L, "New Title")
        withTimeout(3000) {
            while (fakeReadingListDao.getById(1L)?.name != "New Title") {
                kotlinx.coroutines.delay(10)
            }
        }
        assertEquals("New Title", fakeReadingListDao.getById(1L)?.name)

        viewModel.deleteReadingList(1L)
        withTimeout(3000) {
            while (fakeReadingListDao.getAllLists().isNotEmpty()) {
                kotlinx.coroutines.delay(10)
            }
        }
        assertTrue(fakeReadingListDao.getAllLists().isEmpty())
        assertTrue(fakeReadingListDao.getAllItems().isEmpty())
    }

    private class FakeComicDao : ComicDao {
        private val comicsState = MutableStateFlow<List<ComicEntity>>(emptyList())

        fun getAllDirect(): List<ComicEntity> = comicsState.value

        fun setComics(list: List<ComicEntity>) {
            comicsState.value = list
        }

        override fun observeAll(): Flow<List<ComicEntity>> = comicsState

        override suspend fun getAll(): List<ComicEntity> = comicsState.value

        override suspend fun getById(id: Long): ComicEntity? = comicsState.value.find { it.id == id }

        override suspend fun insert(comic: ComicEntity): Long {
            comicsState.value = comicsState.value + comic
            return comic.id
        }

        override suspend fun updateProgress(id: Long, page: Int, slot: Int, openedAt: Long): Int {
            comicsState.value = comicsState.value.map {
                if (it.id == id) it.copy(lastPage = page, lastSlot = slot, lastOpened = openedAt) else it
            }
            return 1
        }

        override suspend fun updatePageCount(id: Long, pageCount: Int): Int {
            comicsState.value = comicsState.value.map {
                if (it.id == id) it.copy(pageCount = pageCount) else it
            }
            return 1
        }

        override suspend fun updateReadingDirection(id: Long, rtl: Boolean): Int {
            comicsState.value = comicsState.value.map {
                if (it.id == id) it.copy(rightToLeft = rtl) else it
            }
            return 1
        }

        override suspend fun updateMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?) {
            comicsState.value = comicsState.value.map {
                if (it.id == id) it.copy(series = series, volume = volume, issueNumber = issueNumber) else it
            }
        }

        override suspend fun getComicsWithoutSeries(): List<ComicEntity> =
            comicsState.value.filter { it.series == null }

        override fun observeBySeries(series: String, volume: String?): Flow<List<ComicEntity>> =
            comicsState.map { list ->
                list.filter {
                    it.series.equals(series, ignoreCase = true) &&
                    (volume.isNullOrBlank() || it.volume.equals(volume, ignoreCase = true))
                }
            }

        override suspend fun delete(id: Long): Int {
            comicsState.value = comicsState.value.filter { it.id != id }
            return 1
        }
    }

    private class FakeReadingListDao : ReadingListDao {
        private val listsState = MutableStateFlow<List<ReadingListEntity>>(emptyList())
        private val itemsState = MutableStateFlow<List<ReadingListItemEntity>>(emptyList())
        var comicsLookup: () -> List<ComicEntity> = { emptyList() }

        fun setListsAndItems(lists: List<ReadingListEntity>, items: List<ReadingListItemEntity>) {
            listsState.value = lists
            itemsState.value = items
        }

        override fun observeById(id: Long): Flow<ReadingListEntity?> =
            listsState.map { lists -> lists.find { it.id == id } }

        override fun observeAll(): Flow<List<ReadingListEntity>> = listsState

        override suspend fun getAllLists(): List<ReadingListEntity> = listsState.value

        override fun observeAllItems(): Flow<List<ReadingListItemEntity>> = itemsState

        override suspend fun getAllItems(): List<ReadingListItemEntity> = itemsState.value

        override suspend fun getById(id: Long): ReadingListEntity? =
            listsState.value.find { it.id == id }

        override suspend fun insertList(list: ReadingListEntity): Long {
            val id = if (list.id == 0L) (listsState.value.maxOfOrNull { it.id } ?: 0L) + 1 else list.id
            listsState.value = listsState.value + list.copy(id = id)
            return id
        }

        override suspend fun updateName(id: Long, name: String, updatedAt: Long): Int {
            listsState.value = listsState.value.map {
                if (it.id == id) it.copy(name = name, updatedAt = updatedAt) else it
            }
            return 1
        }

        override suspend fun deleteList(id: Long): Int {
            listsState.value = listsState.value.filter { it.id != id }
            return 1
        }

        override fun observeComicsForList(listId: Long): Flow<List<ComicEntity>> =
            itemsState.map { items ->
                val comicMap = comicsLookup().associateBy { it.id }
                items.filter { it.listId == listId }
                    .sortedBy { it.sortOrder }
                    .mapNotNull { comicMap[it.comicId] }
            }

        override fun observeItemCount(listId: Long): Flow<Int> =
            itemsState.map { items -> items.count { it.listId == listId } }

        override suspend fun insertItem(item: ReadingListItemEntity) {
            itemsState.value = itemsState.value.filterNot { it.listId == item.listId && it.comicId == item.comicId } + item
        }

        override suspend fun updateItemOrder(listId: Long, comicId: Long, sortOrder: Int): Int {
            itemsState.value = itemsState.value.map {
                if (it.listId == listId && it.comicId == comicId) it.copy(sortOrder = sortOrder) else it
            }
            return 1
        }

        override suspend fun removeItem(listId: Long, comicId: Long): Int {
            itemsState.value = itemsState.value.filterNot { it.listId == listId && it.comicId == comicId }
            return 1
        }

        override suspend fun clearItems(listId: Long): Int {
            itemsState.value = itemsState.value.filterNot { it.listId == listId }
            return 1
        }
    }
}
