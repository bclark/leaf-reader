package net.digitalbc.leafreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ComicEntity::class,
        ReadingListEntity::class,
        ReadingListItemEntity::class,
        RemoteComicLinkEntity::class,
        JellyfinSyncOutboxEntity::class,
        PendingBackupPreferences::class,
        JellyfinMetadataCache::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun remoteComicLinkDao(): RemoteComicLinkDao
    abstract fun jellyfinSyncOutboxDao(): JellyfinSyncOutboxDao
    abstract fun pendingBackupPreferencesDao(): PendingBackupPreferencesDao
    abstract fun jellyfinMetadataCacheDao(): JellyfinMetadataCacheDao

    internal val backupRestoreMutex = kotlinx.coroutines.sync.Mutex()

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comics ADD COLUMN source TEXT NOT NULL DEFAULT 'local'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comics ADD COLUMN series TEXT")
                db.execSQL("ALTER TABLE comics ADD COLUMN volume TEXT")
                db.execSQL("ALTER TABLE comics ADD COLUMN issueNumber REAL")
                db.execSQL("CREATE TABLE IF NOT EXISTS reading_lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reading_list_items (listId INTEGER NOT NULL, comicId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(listId, comicId))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS jellyfin_sync_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        comicId INTEGER NOT NULL,
                        serverId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        pageIndex INTEGER NOT NULL,
                        markPlayed INTEGER,
                        updatedAt INTEGER NOT NULL,
                        revision INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(comicId) REFERENCES comics(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_jellyfin_sync_outbox_comicId ON jellyfin_sync_outbox(comicId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_jellyfin_sync_outbox_serverId_userId ON jellyfin_sync_outbox(serverId, userId)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Support both v5 layouts without resetting existing revisions.
                val hasRevision = db.query("PRAGMA table_info(jellyfin_sync_outbox)").use { columns ->
                    val nameIndex = columns.getColumnIndexOrThrow("name")
                    var found = false
                    while (columns.moveToNext()) {
                        if (columns.getString(nameIndex) == "revision") found = true
                    }
                    found
                }
                if (!hasRevision) {
                    db.execSQL("ALTER TABLE jellyfin_sync_outbox ADD COLUMN revision INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS pending_backup_preferences (
                        id INTEGER NOT NULL PRIMARY KEY,
                        defaultRightToLeft INTEGER NOT NULL,
                        amoledTheme INTEGER NOT NULL,
                        pageSlideEnabled INTEGER NOT NULL,
                        pageFitMode TEXT NOT NULL,
                        portraitSpread INTEGER NOT NULL,
                        landscapeSpread INTEGER NOT NULL,
                        libraryColumns INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_list_items RENAME TO reading_list_items_v7")
                db.execSQL("""CREATE TABLE reading_list_items (
                    listId INTEGER NOT NULL, comicId INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                    addedAt INTEGER NOT NULL, referenceKey TEXT NOT NULL,
                    serverId TEXT, userId TEXT, itemId TEXT, libraryId TEXT, snapshot TEXT, title TEXT,
                    PRIMARY KEY(listId, referenceKey))""")
                db.execSQL("""INSERT INTO reading_list_items(
                    listId,comicId,sortOrder,addedAt,referenceKey,title,serverId,userId,itemId)
                    SELECT r.listId,r.comicId,r.sortOrder,r.addedAt,
                    CASE WHEN l.id IS NULL THEN 'local:' || r.comicId ELSE
                        'remote:' || length(l.serverId) || ':' || l.serverId ||
                        length(l.userId) || ':' || l.userId || length(l.itemId) || ':' || l.itemId END,
                    c.title,l.serverId,l.userId,l.itemId
                    FROM reading_list_items_v7 r LEFT JOIN comics c ON c.id=r.comicId
                    LEFT JOIN remote_comic_links l ON l.id=(SELECT id FROM remote_comic_links
                        WHERE comicId=r.comicId AND provider='jellyfin' ORDER BY id LIMIT 1)""")
                db.execSQL("DROP TABLE reading_list_items_v7")
                db.execSQL("""CREATE TABLE jellyfin_metadata_cache(
                    serverId TEXT NOT NULL,userId TEXT NOT NULL,scope TEXT NOT NULL,
                    cacheKey TEXT NOT NULL,payload TEXT NOT NULL,
                    PRIMARY KEY(serverId,userId,scope,cacheKey))""")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE comics ADD COLUMN completionOverride INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserve a pending restore and supply the same defaults as older backup files.
                db.execSQL("ALTER TABLE pending_backup_preferences ADD COLUMN libraryShowTitles INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE pending_backup_preferences ADD COLUMN libraryShowDetails INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pending_backup_preferences ADD COLUMN libraryShowReadingIndicators INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comics.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build().also { instance = it }
            }
    }
}
