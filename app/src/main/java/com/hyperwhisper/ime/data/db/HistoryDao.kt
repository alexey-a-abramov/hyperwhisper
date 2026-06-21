package com.hyperwhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryEntity>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HistoryEntity>)

    @Query("UPDATE history SET text = :newText, timestamp = :newTimestamp WHERE id = :id")
    suspend fun updateText(id: String, newText: String, newTimestamp: Long)

    /** Re-point (or clear) a row's audio file path after reconciling with disk. */
    @Query("UPDATE history SET audioFilePath = :path WHERE id = :id")
    suspend fun updateAudioPath(id: String, path: String?)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    /**
     * Returns rows that would be deleted if we trimmed to [keep] most recent.
     * Callers use this to also clean up audio files on disk before [trimToSize].
     */
    @Query(
        "SELECT * FROM history WHERE id NOT IN " +
            "(SELECT id FROM history ORDER BY timestamp DESC LIMIT :keep)",
    )
    suspend fun staleAfterTrim(keep: Int): List<HistoryEntity>

    @Query(
        "DELETE FROM history WHERE id NOT IN " +
            "(SELECT id FROM history ORDER BY timestamp DESC LIMIT :keep)",
    )
    suspend fun trimToSize(keep: Int)
}
