package net.digitalbc.leafreader.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import net.digitalbc.leafreader.data.backup.BackupSettings

/** Only non-sensitive preferences; committed atomically with restored progress and lists. */
@Entity(tableName = "pending_backup_preferences")
data class PendingBackupPreferences(
    @PrimaryKey val id: Int = 1,
    @Embedded val settings: BackupSettings,
)

@Dao
interface PendingBackupPreferencesDao {
    @Query("SELECT * FROM pending_backup_preferences WHERE id = 1")
    suspend fun get(): PendingBackupPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(pending: PendingBackupPreferences)

    @Query("DELETE FROM pending_backup_preferences WHERE id = 1")
    suspend fun clear()
}
