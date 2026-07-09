package cz.notecat.app.capture

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.notecat.app.NoteCatApp
import cz.notecat.app.audio.NoteRecorder
import cz.notecat.app.data.Note
import cz.notecat.app.data.NoteStatus
import cz.notecat.app.voice.VoskEndpointer
import cz.notecat.app.voice.VoskModelProvider
import cz.notecat.app.work.ProcessNoteWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CaptureUiState {
    data object Preparing : CaptureUiState
    data class Recording(val elapsedMs: Long, val amplitude: Float) : CaptureUiState
    data object Saving : CaptureUiState
    data object Saved : CaptureUiState
    data object DiscardedEmpty : CaptureUiState
    data class Error(val message: String) : CaptureUiState
}

class CaptureViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<NoteCatApp>()

    private val _state = MutableStateFlow<CaptureUiState>(CaptureUiState.Preparing)
    val state: StateFlow<CaptureUiState> = _state

    val closeAfterSave: Boolean get() = app.settings.closeAfterSave

    private var recorder: NoteRecorder? = null
    private var noteId: String = ""
    private var audioFile: File? = null
    private val stopping = AtomicBoolean(false)

    /** Save-first flow: nahrávání startuje okamžitě, Vosk model se dohrává na pozadí. */
    fun startRecording() {
        if (recorder != null) return
        noteId = UUID.randomUUID().toString()
        val file = File(app.audioDir, "$noteId.wav")
        audioFile = file

        val rec = NoteRecorder(
            outputFile = file,
            scope = viewModelScope,
            onAmplitude = { amp ->
                val current = _state.value
                if (current is CaptureUiState.Recording) {
                    _state.value = current.copy(amplitude = amp)
                }
            },
            onElapsed = { elapsed ->
                val current = _state.value
                if (current is CaptureUiState.Recording || current is CaptureUiState.Preparing) {
                    val amp = (current as? CaptureUiState.Recording)?.amplitude ?: 0f
                    _state.value = CaptureUiState.Recording(elapsed, amp)
                }
            },
            onEndCommand = { finish(save = true) },
            onError = { t ->
                _state.value = CaptureUiState.Error(t.message ?: "Chyba nahrávání")
            },
        )
        recorder = rec
        rec.start()
        _state.value = CaptureUiState.Recording(0, 0f)
        vibrate(30)

        // Vosk model pro povel „konec" – načtení může chvíli trvat, nahrávání neblokuje
        viewModelScope.launch(Dispatchers.IO) {
            val model = VoskModelProvider.getModel(app)
            if (model != null && recorder === rec && !stopping.get()) {
                val endpointer = VoskEndpointer(model)
                rec.endpointer = endpointer
                // Závod s ukončením: kdyby stop proběhl mezi kontrolou a přiřazením,
                // rozpoznávač by nikdo nezavřel.
                if (stopping.get() && rec.endpointer === endpointer) {
                    rec.endpointer = null
                    endpointer.close()
                }
            }
        }
    }

    /** Ukončení tlačítkem nebo hlasovým povelem. */
    fun finish(save: Boolean) {
        if (!stopping.compareAndSet(false, true)) return
        val rec = recorder ?: return
        _state.value = CaptureUiState.Saving
        viewModelScope.launch {
            if (!save) {
                rec.cancel()
                _state.value = CaptureUiState.DiscardedEmpty
                return@launch
            }
            if (rec.isObviouslyEmpty()) {
                // Konzervativní kontrola: jen zjevně prázdné/extrémně krátké záznamy
                rec.cancel()
                _state.value = CaptureUiState.DiscardedEmpty
                return@launch
            }
            rec.stop()
            val now = System.currentTimeMillis()
            val note = Note(
                id = noteId,
                createdAt = now,
                updatedAt = now,
                status = NoteStatus.PENDING,
                audioPath = audioFile?.absolutePath,
                durationMs = rec.durationMs,
            )
            // 1) Lokální uložení (save-first) …
            app.repository.insert(note)
            // 2) … teprve potom síťové zpracování na pozadí
            ProcessNoteWorker.enqueue(app, noteId)
            vibrate(60)
            _state.value = CaptureUiState.Saved
        }
    }

    override fun onCleared() {
        // Zavření obrazovky uprostřed nahrávání: záznam se bezpečně uloží,
        // aby myšlenka nezmizela (stop místo cancel).
        val rec = recorder
        if (rec != null && stopping.compareAndSet(false, true)) {
            val app = app
            val id = noteId
            val file = audioFile
            app.appScope.launch {
                if (rec.isObviouslyEmpty()) {
                    rec.cancel()
                } else {
                    rec.stop()
                    val now = System.currentTimeMillis()
                    app.repository.insert(
                        Note(
                            id = id,
                            createdAt = now,
                            updatedAt = now,
                            status = NoteStatus.PENDING,
                            audioPath = file?.absolutePath,
                            durationMs = rec.durationMs,
                        )
                    )
                    ProcessNoteWorker.enqueue(app, id)
                }
            }
        }
        super.onCleared()
    }

    private fun vibrate(ms: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
