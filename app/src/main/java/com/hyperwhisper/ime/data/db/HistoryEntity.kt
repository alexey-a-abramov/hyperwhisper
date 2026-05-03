package com.hyperwhisper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted shape of a transcription history item.
 *
 * Mirrors [com.hyperwhisper.data.TranscriptionHistoryItem] so the existing call
 * sites that consume the domain class don't have to change — mapping is a thin
 * pair of extension functions in [Mappers].
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val timestamp: Long,
    val audioFilePath: String?,
)
