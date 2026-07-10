package cz.notecat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class ProcessingStatus { PENDING, PROCESSING, DONE, FAILED }

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val transcriptOriginal: String? = null,
    val noteText: String = "",
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
    val audioPath: String? = null,
    val errorMessage: String? = null,
    val idempotencyKey: String = UUID.randomUUID().toString(),
)
