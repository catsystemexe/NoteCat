package cz.notecat.app.data

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Jediný vstupní bod pro práci s poznámkami. Drží invarianty:
 *  - audio soubor se maže výhradně tady (po úspěchu, při smazání poznámky, při úklidu),
 *  - rawTranscript se po prvním zápisu už nemění,
 *  - editace uživatele jde vždy do polishedText.
 */
class NoteRepository(private val dao: NoteDao) {

    fun observeAll(): Flow<List<Note>> = dao.observeAll()
    fun search(query: String): Flow<List<Note>> = dao.search(query)
    fun observeById(id: String): Flow<Note?> = dao.observeById(id)
    suspend fun getById(id: String): Note? = dao.getById(id)

    suspend fun insert(note: Note) = dao.insert(note)

    suspend fun markProcessing(id: String) {
        dao.getById(id)?.let {
            dao.update(it.copy(status = NoteStatus.PROCESSING, updatedAt = now()))
        }
    }

    suspend fun markPending(id: String) {
        dao.getById(id)?.let {
            dao.update(it.copy(status = NoteStatus.PENDING, updatedAt = now()))
        }
    }

    suspend fun markFailed(id: String, message: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(status = NoteStatus.FAILED, errorMessage = message, updatedAt = now())
            )
        }
    }

    /** Úspěšné dokončení: uloží texty, smaže audio a zahodí odkaz na něj. */
    suspend fun completeProcessing(id: String, transcript: String, polished: String) {
        val note = dao.getById(id) ?: return
        note.audioPath?.let { path -> File(path).delete() }
        dao.update(
            note.copy(
                rawTranscript = transcript,
                polishedText = polished,
                status = NoteStatus.DONE,
                audioPath = null,
                errorMessage = null,
                updatedAt = now(),
            )
        )
    }

    /**
     * Přepis uspěl, ale AI úprava selhala: uloží přepis jako dočasný text,
     * poznámku označí jako neúspěšnou (jde zopakovat) a audio ponechá.
     */
    suspend fun completeTranscriptOnly(id: String, transcript: String, message: String) {
        dao.getById(id)?.let {
            dao.update(
                it.copy(
                    rawTranscript = transcript,
                    status = NoteStatus.FAILED,
                    errorMessage = message,
                    updatedAt = now(),
                )
            )
        }
    }

    /** Ruční editace textu – nikdy nesahá na rawTranscript. */
    suspend fun updatePolishedText(id: String, text: String) {
        dao.getById(id)?.let {
            dao.update(it.copy(polishedText = text, updatedAt = now()))
        }
    }

    suspend fun delete(id: String) {
        dao.getById(id)?.audioPath?.let { path -> File(path).delete() }
        dao.delete(id)
    }

    /**
     * Obnova konzistence po pádu/zabití procesu:
     *  - poznámky zaseknuté v PROCESSING vrací do PENDING (worker je znovu vyzvedne),
     *  - PENDING/FAILED poznámky bez existujícího audia označí jako FAILED,
     *  - osiřelé audio soubory bez záznamu v DB smaže.
     */
    suspend fun recoverOnStartup(audioDir: File): List<Note> {
        dao.getByStatus(listOf(NoteStatus.PROCESSING)).forEach {
            dao.update(it.copy(status = NoteStatus.PENDING, updatedAt = now()))
        }
        val pending = dao.getByStatus(listOf(NoteStatus.PENDING))
        val toRetry = mutableListOf<Note>()
        for (note in pending) {
            if (note.audioPath == null || !File(note.audioPath).exists()) {
                dao.update(
                    note.copy(
                        status = NoteStatus.FAILED,
                        errorMessage = "Zvukový záznam se ztratil, poznámku nelze zpracovat.",
                        audioPath = null,
                        updatedAt = now(),
                    )
                )
            } else {
                toRetry += note
            }
        }
        val referenced = dao.getAllAudioPaths().toSet()
        audioDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in referenced) file.delete()
        }
        return toRetry
    }

    private fun now() = System.currentTimeMillis()
}
