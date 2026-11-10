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
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun remoteComicLinkDao(): RemoteComicLinkDao
    abstract fun jellyfinSyncOutboxDao(): JellyfinSyncOutboxDao

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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comics.db",
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
            }
    }
}
