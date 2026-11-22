package net.digitalbc.leafreader.data.db

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun migrationFrom4To7PreservesPopulatedDataAndCreatesOutboxTable() = runBlocking {
        verifyPopulatedUpgrade(version = 4)
    }

    @Test
    fun migrationFromPreRevision5PreservesPendingProgress() = runBlocking {
        verifyPopulatedUpgrade(version = 5)
    }

    @Test
    fun migrationFromPreRevision5PreservesExplicitRead() = runBlocking {
        verifyPopulatedUpgrade(version = 5, markPlayed = true)
    }

    @Test
    fun migrationFromPreRevision5PreservesExplicitUnread() = runBlocking {
        verifyPopulatedUpgrade(version = 5, markPlayed = false)
    }

    @Test
    fun migrationFromRevised5PreservesExistingRevision() = runBlocking {
        verifyPopulatedUpgrade(version = 5, hasRevision = true, markPlayed = false)
    }

    @Test
    fun migrationFrom6PreservesDataAndCreatesEmptyRecoveryJournal() = runBlocking {
        verifyPopulatedUpgrade(version = 6, hasRevision = true, markPlayed = false)
    }

    @Test
    fun migrationFrom8To9PreservesPositionsListsOutboxAndMetadata() = runBlocking {
        verifyPopulatedUpgrade(version = 8, hasRevision = true, markPlayed = false)
    }

    @Test
    fun migrationFrom9PreservesPendingRestoreWithQuietAppearanceDefaults() = runBlocking {
        verifyPopulatedUpgrade(version = 9, hasRevision = true, markPlayed = false)
    }

    private suspend fun verifyPopulatedUpgrade(
        version: Int,
        hasRevision: Boolean = false,
        markPlayed: Boolean? = null,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "test_migration_to_7.db"
        context.deleteDatabase(databaseName)

        // Independent predecessor fixture, opened through Room's normal upgrade path.
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS comics (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            filePath TEXT NOT NULL,
                            coverPath TEXT,
                            format TEXT NOT NULL,
                            pageCount INTEGER NOT NULL,
                            rightToLeft INTEGER NOT NULL,
                            lastPage INTEGER NOT NULL,
                            lastSlot INTEGER NOT NULL,
                            source TEXT NOT NULL DEFAULT 'local',
                            series TEXT,
                            volume TEXT,
                            issueNumber REAL,
                            dateAdded INTEGER NOT NULL,
                            lastOpened INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS reading_lists (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS reading_list_items (
                            listId INTEGER NOT NULL,
                            comicId INTEGER NOT NULL,
                            sortOrder INTEGER NOT NULL,
                            addedAt INTEGER NOT NULL,
                            PRIMARY KEY(listId, comicId)
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS remote_comic_links (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            comicId INTEGER NOT NULL,
                            provider TEXT NOT NULL,
                            serverId TEXT NOT NULL,
                            userId TEXT NOT NULL,
                            itemId TEXT NOT NULL,
                            parentId TEXT,
                            downloadedAt INTEGER NOT NULL,
                            remoteVersionTag TEXT,
                            remoteName TEXT,
                            FOREIGN KEY(comicId) REFERENCES comics(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_remote_comic_links_provider_serverId_userId_itemId ON remote_comic_links(provider, serverId, userId, itemId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_remote_comic_links_comicId ON remote_comic_links(comicId)"
                    )
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(config)
        val v4Db = helper.writableDatabase

        // Populate Schema 4 data
        v4Db.execSQL(
            """
            INSERT INTO comics (id, title, filePath, coverPath, format, pageCount, rightToLeft, lastPage, lastSlot, source, series, volume, issueNumber, dateAdded, lastOpened)
            VALUES (42, 'Spider-Man #1', '/data/spidey.cbz', '/data/cover.jpg', 'CBZ', 32, 0, 15, 0, 'jellyfin', 'Amazing Spider-Man', 'Vol 1', 1.0, 1000, 2000)
            """.trimIndent()
        )
        v4Db.execSQL(
            """
            INSERT INTO remote_comic_links (id, comicId, provider, serverId, userId, itemId, parentId, downloadedAt, remoteVersionTag, remoteName)
            VALUES (1, 42, 'jellyfin', 'srv-test', 'usr-test', 'item-spidey-42', 'folder-spidey', 2000, 'tag-1', 'Spider-Man #1')
            """.trimIndent()
        )
        v4Db.execSQL(
            """
            INSERT INTO reading_lists (id, name, createdAt, updatedAt)
            VALUES (1, 'Favorites', 1000, 2000)
            """.trimIndent()
        )
        v4Db.execSQL(
            """
            INSERT INTO reading_list_items (listId, comicId, sortOrder, addedAt)
            VALUES (1, 42, 0, 2000)
            """.trimIndent()
        )

        if (version >= 5) {
            val revisionColumn = if (hasRevision) ", revision INTEGER NOT NULL DEFAULT 1" else ""
            v4Db.execSQL(
                """
                CREATE TABLE jellyfin_sync_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    comicId INTEGER NOT NULL,
                    serverId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    itemId TEXT NOT NULL,
                    pageIndex INTEGER NOT NULL,
                    markPlayed INTEGER,
                    updatedAt INTEGER NOT NULL
                    $revisionColumn,
                    FOREIGN KEY(comicId) REFERENCES comics(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            v4Db.execSQL("CREATE UNIQUE INDEX index_jellyfin_sync_outbox_comicId ON jellyfin_sync_outbox(comicId)")
            v4Db.execSQL("CREATE INDEX index_jellyfin_sync_outbox_serverId_userId ON jellyfin_sync_outbox(serverId, userId)")
            val revisionName = if (hasRevision) ", revision" else ""
            val revisionValue = if (hasRevision) ", 9" else ""
            v4Db.execSQL(
                """
                INSERT INTO jellyfin_sync_outbox
                    (id, comicId, serverId, userId, itemId, pageIndex, markPlayed, updatedAt$revisionName)
                VALUES (7, 42, 'srv-test', 'usr-test', 'item-spidey-42', 15, ?, 2500$revisionValue)
                """.trimIndent(),
                arrayOf<Any?>(markPlayed?.let { if (it) 1 else 0 }),
            )
        }
        if (version >= 7) AppDatabase.MIGRATION_6_7.migrate(v4Db)
        if (version >= 8) {
            AppDatabase.MIGRATION_7_8.migrate(v4Db)
            v4Db.execSQL("INSERT INTO jellyfin_metadata_cache VALUES('srv-test','usr-test','https://server.test','baseline','saved-state')")
        }
        if (version >= 9) {
            AppDatabase.MIGRATION_8_9.migrate(v4Db)
            v4Db.execSQL("INSERT INTO pending_backup_preferences VALUES(1, 1, 0, 1, 'FIT_WIDTH', 0, 1, 4)")
        }
        helper.close()

        fun openMigratedDatabase() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()

        var migratedDb = openMigratedDatabase()

        try {
            // Force migration/validation, then reopen the on-disk database to check durability.
            assertEquals(10, migratedDb.openHelper.writableDatabase.version)
            if (version < 9) assertNull(migratedDb.pendingBackupPreferencesDao().get())
            migratedDb.close()
            migratedDb = openMigratedDatabase()

            if (version >= 9) assertEquals(
                net.digitalbc.leafreader.data.backup.BackupSettings(true, false, true, "FIT_WIDTH", false, true, 4),
                migratedDb.pendingBackupPreferencesDao().get()!!.settings,
            )

            // Verify existing comic survived migration intact
            val comic = migratedDb.comicDao().getById(42)
            assertNotNull("Comic 42 must survive migration", comic)
            assertEquals("Spider-Man #1", comic?.title)
            assertEquals(15, comic?.lastPage)
            assertEquals(32, comic?.pageCount)
            assertEquals("jellyfin", comic?.source)
            assertNull(comic?.completionOverride)
            if (version >= 8) assertEquals("saved-state", migratedDb.jellyfinMetadataCacheDao()
                .get("srv-test", "usr-test", "https://server.test", "baseline"))
            migratedDb.comicDao().updateCompletion(42, true)
            assertEquals(15, migratedDb.comicDao().getById(42)?.lastPage)
            assertTrue(migratedDb.comicDao().getById(42)!!.isCompleted)

            // Verify remote comic link survived migration intact
            val link = migratedDb.remoteComicLinkDao().findLink("jellyfin", "srv-test", "usr-test", "item-spidey-42")
            assertNotNull("Remote comic link must survive migration", link)
            assertEquals(42L, link?.comicId)
            assertEquals("folder-spidey", link?.parentId)

            // Verify reading list and its items survived migration intact
            val lists = migratedDb.readingListDao().getAllLists()
            assertEquals(1, lists.size)
            assertEquals("Favorites", lists[0].name)
            val listItems = migratedDb.readingListDao().getAllItems()
            assertEquals(1, listItems.size)
            assertEquals(42L, listItems[0].comicId)
            assertEquals(1L, listItems[0].listId)
            assertEquals(0, listItems[0].sortOrder)
            assertEquals(net.digitalbc.leafreader.data.jellyfin.JellyfinSnapshots.reference(
                "srv-test", "usr-test", "item-spidey-42"), listItems[0].referenceKey)
            assertEquals("item-spidey-42", listItems[0].itemId)
            migratedDb.readingListDao().insertItem(ReadingListItemEntity(1, 0, 1,
                referenceKey = "remote-only-a", serverId = "srv-test", userId = "usr-test", itemId = "a"))
            migratedDb.readingListDao().insertItem(ReadingListItemEntity(1, 0, 2,
                referenceKey = "remote-only-b", serverId = "srv-test", userId = "usr-test", itemId = "b"))
            assertEquals(3, migratedDb.readingListDao().getAllItems().size)
            migratedDb.jellyfinMetadataCacheDao().put(JellyfinMetadataCache("s", "u", "url", "key", "metadata"))
            assertEquals("metadata", migratedDb.jellyfinMetadataCacheDao().get("s", "u", "url", "key"))
            assertNull(migratedDb.jellyfinMetadataCacheDao().get("s", "other", "url", "key"))

            // Verify newly created jellyfin_sync_outbox table works with Room DAO and includes revision
            val outboxDao = migratedDb.jellyfinSyncOutboxDao()
            if (version >= 5) {
                assertEquals(
                    listOf(JellyfinSyncOutboxEntity(
                        id = 7,
                        comicId = 42,
                        serverId = "srv-test",
                        userId = "usr-test",
                        itemId = "item-spidey-42",
                        pageIndex = 15,
                        markPlayed = markPlayed,
                        updatedAt = 2500L,
                        revision = if (hasRevision) 9L else 1L,
                    )),
                    outboxDao.getPendingForUser("srv-test", "usr-test"),
                )
            } else {
                assertEquals(0, outboxDao.countPendingForUser("srv-test", "usr-test"))
            }
            val outboxEntry = JellyfinSyncOutboxEntity(
                comicId = 42,
                serverId = "srv-test",
                userId = "usr-test",
                itemId = "item-spidey-42",
                pageIndex = 16,
                markPlayed = null,
                updatedAt = 3000L,
                revision = 1L,
            )
            outboxDao.upsert(outboxEntry)
            val pending = outboxDao.getPendingForComic(42)
            assertNotNull("Outbox entry must be queryable in migrated DB", pending)
            assertEquals(16, pending?.pageIndex)
            assertEquals(1L, pending?.revision)

            // Verify cascade delete from comic to outbox and remote link in migrated DB
            migratedDb.comicDao().delete(42)
            assertNull(migratedDb.remoteComicLinkDao().findLink("jellyfin", "srv-test", "usr-test", "item-spidey-42"))
            assertNull(outboxDao.getPendingForComic(42))
        } finally {
            migratedDb.close()
            context.deleteDatabase(databaseName)
        }
    }
}
