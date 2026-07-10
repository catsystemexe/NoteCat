package cz.notecat.data

import android.content.Context
import androidx.work.*
import cz.notecat.processing.ProcessNoteWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class NoteRepository(private val context: Context, private val dao: NoteDao) {
    fun observeNotes(query: String) = dao.observeNotes(query)
    suspend fun get(id: String) = dao.get(id)
    suspend fun createPending(audio: File): Note {
        val note = Note(audioPath = audio.absolutePath, idempotencyKey = UUID.randomUUID().toString())
        dao.insert(note)
        enqueue(note.id)
        return note
    }
    fun enqueue(noteId: String) {
        val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
            .setInputData(workDataOf(ProcessNoteWorker.NOTE_ID to noteId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("process-$noteId", ExistingWorkPolicy.KEEP, request)
    }
    suspend fun update(note: Note) = dao.update(note.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(note: Note) { note.audioPath?.let { File(it).delete() }; dao.delete(note) }
}
