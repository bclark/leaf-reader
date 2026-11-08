package net.digitalbc.leafreader.data.db

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AppDatabaseMigrationTest {

    private lateinit var db: AppDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var linkDao: RemoteComicLinkDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = db.comicDao()
        linkDao = db.remoteComicLinkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryRemoteComicLink() = runBlocking {
        val comicId = comicDao.insert(
            ComicEntity(
                title = "Test Comic",
                filePath = "/data/comic.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 20,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )

        val link = RemoteComicLinkEntity(
            comicId = comicId,
            provider = "jellyfin",
            serverId = "srv-1",
            userId = "usr-1",
            itemId = "item-123",
            parentId = "folder-456",
            downloadedAt = 2000L,
            remoteVersionTag = "tag-abc",
            remoteName = "Test Comic Issue 1",
        )
        linkDao.insert(link)

        val found = linkDao.findLink("jellyfin", "srv-1", "usr-1", "item-123")
        assertNotNull(found)
        assertEquals(comicId, found?.comicId)
        assertEquals("Test Comic Issue 1", found?.remoteName)
        assertEquals("tag-abc", found?.remoteVersionTag)

        val byComic = linkDao.findByComicId(comicId)
        assertNotNull(byComic)
        assertEquals("item-123", byComic?.itemId)

        val userLinks = linkDao.getLinksForUser("jellyfin", "srv-1", "usr-1")
        assertEquals(1, userLinks.size)
        assertEquals("item-123", userLinks[0].itemId)
    }

    @Test
    fun deletingComicCascadesToRemoteLink() = runBlocking {
        val comicId = comicDao.insert(
            ComicEntity(
                title = "Cascade Test",
                filePath = "/data/cascade.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 10,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )

        linkDao.insert(
            RemoteComicLinkEntity(
                comicId = comicId,
                provider = "jellyfin",
                serverId = "srv-1",
                userId = "usr-1",
                itemId = "item-cascade",
            )
        )

        assertNotNull(linkDao.findLink("jellyfin", "srv-1", "usr-1", "item-cascade"))

        // Delete the parent comic
        comicDao.delete(comicId)

        // Link should be cascade-deleted
        assertNull(linkDao.findLink("jellyfin", "srv-1", "usr-1", "item-cascade"))
        assertNull(linkDao.findByComicId(comicId))
    }

    @Test
    fun insertAndQueryJellyfinSyncOutbox() = runBlocking {
        val outboxDao = db.jellyfinSyncOutboxDao()
        val comicId = comicDao.insert(
            ComicEntity(
                title = "Outbox Test Comic",
                filePath = "/data/outbox.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 25,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )

        val entry = JellyfinSyncOutboxEntity(
            comicId = comicId,
            serverId = "srv-1",
            userId = "usr-1",
            itemId = "item-outbox-1",
            pageIndex = 12,
            markPlayed = null,
            updatedAt = 3000L,
        )
        outboxDao.upsert(entry)

        val pending = outboxDao.getPendingForUser("srv-1", "usr-1")
        assertEquals(1, pending.size)
        assertEquals(12, pending[0].pageIndex)
        assertEquals("item-outbox-1", pending[0].itemId)
        assertNull(pending[0].markPlayed)

        val forComic = outboxDao.getPendingForComic(comicId)
        assertNotNull(forComic)
        assertEquals(12, forComic?.pageIndex)

        // Update page to 24 (completed)
        outboxDao.upsert(entry.copy(pageIndex = 24, markPlayed = true, updatedAt = 4000L))
        val updated = outboxDao.getPendingForComic(comicId)
        assertEquals(24, updated?.pageIndex)
        assertEquals(true, updated?.markPlayed)
        assertEquals(1, outboxDao.countPendingForUser("srv-1", "usr-1"))

        // Delete by comic ID
        outboxDao.deleteByComicId(comicId)
        assertEquals(0, outboxDao.countPendingForUser("srv-1", "usr-1"))
    }

    @Test
    fun deletingComicCascadesToSyncOutbox() = runBlocking {
        val outboxDao = db.jellyfinSyncOutboxDao()
        val comicId = comicDao.insert(
            ComicEntity(
                title = "Outbox Cascade Test",
                filePath = "/data/outbox_cascade.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 10,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )

        outboxDao.upsert(
            JellyfinSyncOutboxEntity(
                comicId = comicId,
                serverId = "srv-1",
                userId = "usr-1",
                itemId = "item-cascade-outbox",
                pageIndex = 5,
            )
        )

        assertEquals(1, outboxDao.countPendingForUser("srv-1", "usr-1"))

        // Delete the parent comic
        comicDao.delete(comicId)

        // Outbox entry should be cascade-deleted
        assertEquals(0, outboxDao.countPendingForUser("srv-1", "usr-1"))
        assertNull(outboxDao.getPendingForComic(comicId))
    }
}
