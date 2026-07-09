package cz.notecat.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import cz.notecat.app.NoteCatApp
import cz.notecat.app.data.NoteStatus
import cz.notecat.app.net.BackendClient
import cz.notecat.app.net.BackendError
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zpracování jedné poznámky na pozadí: upload audia → přepis + AI úprava
 * → uložení výsledku → smazání audia.
 *
 * Naprosto oddělené od capture flow – poznámka je v DB dřív, než tenhle
 * worker vůbec vznikne. Přežije zavření aplikace, výpadek sítě (retry
 * s exponenciálním backoffem) i zabití procesu (WorkManager persistence
 * + recoverOnStartup v Application).
 */
class ProcessNoteWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as NoteCatApp
        val repository = app.repository
        val noteId = inputData.getString(KEY_NOTE_ID) ?: return@withContext Result.success()
        val note = repository.getById(noteId) ?: return@withContext Result.success()

        // Idempotence na straně klienta: hotovou poznámku nezpracovávat znovu
        if (note.status == NoteStatus.DONE) return@withContext Result.success()

        val audioPath = note.audioPath
        if (audioPath == null || !File(audioPath).exists()) {
            repository.markFailed(noteId, "Zvukový záznam se ztratil, poznámku nelze zpracovat.")
            return@withContext Result.failure()
        }

        val settings = app.settings
        if (!settings.isBackendConfigured) {
            // Bez konfigurace nemá smysl pálit retry – poznámka zůstává PENDING
            // a zpracuje se po nastavení backendu (recovery/ruční retry).
            repository.markFailed(noteId, "Backend není nastaven. Vyplň adresu a token v nastavení a zkus to znovu.")
            return@withContext Result.failure()
        }

        repository.markProcessing(noteId)

        val client = BackendClient(settings.backendUrl, settings.backendToken)
        try {
            val result = client.processNote(note.idempotencyKey, File(audioPath))
            if (result.polishFailed) {
                // Přepis zůstává jako dočasný text, AI úpravu lze zopakovat
                repository.completeTranscriptOnly(
                    noteId,
                    result.transcript,
                    "AI úprava textu se nezdařila. Přepis je uložen, úpravu můžeš zopakovat.",
                )
                Result.failure()
            } else {
                repository.completeProcessing(noteId, result.transcript, result.polished)
                Result.success()
            }
        } catch (e: BackendError.Transient) {
            if (runAttemptCount < MAX_RETRIES) {
                repository.markPending(noteId)
                Result.retry()
            } else {
                repository.markFailed(noteId, e.message ?: "Síťová chyba")
                Result.failure()
            }
        } catch (e: BackendError.EmptyTranscript) {
            // Poznámka nesmí tiše zmizet – označí se jako neúspěšná, audio zůstává.
            repository.markFailed(noteId, e.message ?: "Prázdný přepis")
            Result.failure()
        } catch (e: BackendError.Permanent) {
            repository.markFailed(noteId, e.message ?: "Chyba backendu")
            Result.failure()
        } catch (e: Exception) {
            repository.markFailed(noteId, "Neočekávaná chyba: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val KEY_NOTE_ID = "note_id"
        const val MAX_RETRIES = 6

        /**
         * Zařadí zpracování poznámky. Unikátní jméno + KEEP zaručují,
         * že stejná poznámka nikdy neběží dvakrát souběžně.
         */
        fun enqueue(context: Context, noteId: String) {
            val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(10))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "process-note-$noteId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Ruční opakování z detailu poznámky – REPLACE restartuje i failed práci. */
        fun retry(context: Context, noteId: String) {
            val request = OneTimeWorkRequestBuilder<ProcessNoteWorker>()
                .setInputData(workDataOf(KEY_NOTE_ID to noteId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofSeconds(10))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "process-note-$noteId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
