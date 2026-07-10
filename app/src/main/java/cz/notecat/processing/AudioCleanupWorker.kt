package cz.notecat.processing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.notecat.NoteCatApp
import java.io.File

class AudioCleanupWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = (applicationContext as NoteCatApp).db.noteDao()
        dao.doneWithAudio(System.currentTimeMillis() - 24 * 60 * 60 * 1000L).forEach { note ->
            note.audioPath?.let { File(it).delete() }
            dao.update(note.copy(audioPath = null, updatedAt = System.currentTimeMillis()))
        }
        val audioDir = File(applicationContext.filesDir, "audio")
        audioDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(100)?.forEach { it.delete() }
        return Result.success()
    }
}
