package cz.notecat.processing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.notecat.NoteCatApp
import cz.notecat.data.ProcessingStatus
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

class ProcessNoteWorker(private val context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val noteId = inputData.getString(NOTE_ID) ?: return Result.failure()
        val dao = (context.applicationContext as NoteCatApp).db.noteDao()
        val note = dao.get(noteId) ?: return Result.failure()
        val audio = note.audioPath?.let(::File) ?: return Result.failure()
        dao.update(note.copy(processingStatus = ProcessingStatus.PROCESSING, updatedAt = System.currentTimeMillis(), errorMessage = null))
        return try {
            val baseUrl = System.getenv("NOTECAT_BACKEND_URL") ?: "http://10.0.2.2:3000"
            val token = System.getenv("NOTECAT_PERSONAL_TOKEN") ?: ""
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("idempotencyKey", note.idempotencyKey)
                .addFormDataPart("audio", audio.name, audio.asRequestBody("audio/mp4".toMediaType())).build()
            val req = Request.Builder().url("$baseUrl/v1/notes/process").header("Authorization", "Bearer $token").post(body).build()
            val res = OkHttpClient().newCall(req).execute()
            if (!res.isSuccessful) throw IllegalStateException("Backend ${res.code}")
            val json = JSONObject(res.body?.string().orEmpty())
            audio.delete()
            dao.update(note.copy(transcriptOriginal = json.getString("transcriptOriginal"), noteText = json.getString("noteText"), processingStatus = ProcessingStatus.DONE, audioPath = null, updatedAt = System.currentTimeMillis()))
            Result.success()
        } catch (e: Exception) {
            dao.update(note.copy(processingStatus = ProcessingStatus.FAILED, errorMessage = e.message, updatedAt = System.currentTimeMillis()))
            Result.retry()
        }
    }
    companion object { const val NOTE_ID = "noteId" }
}
