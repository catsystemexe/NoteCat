package cz.notecat.app

import android.app.Application
import androidx.work.Configuration
import cz.notecat.app.data.NoteDatabase
import cz.notecat.app.data.NoteRepository
import cz.notecat.app.data.NoteStatus
import cz.notecat.app.settings.AppSettings
import cz.notecat.app.work.ProcessNoteWorker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NoteCatApp : Application(), Configuration.Provider {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { NoteDatabase.get(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }
    val settings by lazy { AppSettings(this) }

    /** Adresář pro dočasné WAV soubory (interní úložiště, mimo zálohy). */
    val audioDir: File
        get() = File(filesDir, "audio").apply { mkdirs() }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Obnova po pádu / zabití procesu: vrátit rozpracované poznámky do fronty
        // a uklidit osiřelé audio soubory.
        appScope.launch {
            val toRetry = repository.recoverOnStartup(audioDir)
            toRetry.forEach { note ->
                if (note.status == NoteStatus.PENDING) {
                    ProcessNoteWorker.enqueue(this@NoteCatApp, note.id)
                }
            }
        }
    }
}
