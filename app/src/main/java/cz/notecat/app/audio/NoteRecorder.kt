package cz.notecat.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import cz.notecat.app.voice.VoskEndpointer
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jediný vlastník mikrofonu během diktování.
 * PCM data posílá současně do WAV souboru a (volitelně) do Vosk
 * rozpoznávače povelu „konec".
 *
 * Nahrávání se NIKDY neukončuje automaticky podle ticha – jen povelem
 * nebo voláním [stop]/[cancel].
 */
class NoteRecorder(
    private val outputFile: File,
    private val scope: CoroutineScope,
    private val onAmplitude: (Float) -> Unit,
    private val onElapsed: (Long) -> Unit,
    private val onEndCommand: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 16000
        /** Konzervativní práh „úplně prázdného" záznamu (z max. 32767). */
        const val SILENCE_PEAK_THRESHOLD = 250
        const val MIN_DURATION_MS = 700L
    }

    /**
     * Rozpoznávač povelu „konec" – nastavuje se asynchronně, jakmile se
     * načte Vosk model. Do té doby (a když model chybí) funguje jen tlačítko.
     */
    @Volatile
    var endpointer: VoskEndpointer? = null

    private var audioRecord: AudioRecord? = null
    private var wavWriter: WavWriter? = null
    private var job: Job? = null

    @Volatile
    private var running = false

    /** Nejvyšší absolutní amplituda za celé nahrávání – pro detekci prázdného záznamu. */
    @Volatile
    var peakAmplitude: Int = 0
        private set

    @Volatile
    var durationMs: Long = 0
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2) // ~100 ms bloky
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError(IllegalStateException("AudioRecord se nepodařilo inicializovat"))
            return
        }
        audioRecord = record
        wavWriter = WavWriter(outputFile, SAMPLE_RATE)
        running = true
        record.startRecording()

        job = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            var totalSamples = 0L
            try {
                while (running) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    wavWriter?.write(buffer, read)
                    totalSamples += read / 2
                    durationMs = totalSamples * 1000 / SAMPLE_RATE
                    onElapsed(durationMs)

                    var peak = 0
                    var i = 0
                    while (i + 1 < read) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        val a = abs(sample.toShort().toInt())
                        if (a > peak) peak = a
                        i += 2
                    }
                    if (peak > peakAmplitude) peakAmplitude = peak
                    onAmplitude(peak / 32767f)

                    if (endpointer?.acceptAudio(buffer, read, durationMs) == true) {
                        onEndCommand()
                    }
                }
            } catch (t: Throwable) {
                if (running) onError(t)
            }
        }
    }

    /** Zjevně prázdný záznam = extrémně krátký, nebo prakticky nulový signál. */
    fun isObviouslyEmpty(): Boolean =
        durationMs < MIN_DURATION_MS || peakAmplitude < SILENCE_PEAK_THRESHOLD

    /** Korektně dokončí WAV soubor. Musí se volat mimo hlavní vlákno. */
    suspend fun stop() {
        running = false
        withContext(Dispatchers.IO) {
            job?.join()
            releaseRecord()
            wavWriter?.close()
            endpointer?.close()
        }
    }

    /** Zahodí nahrávku i soubor. */
    suspend fun cancel() {
        running = false
        withContext(Dispatchers.IO) {
            job?.join()
            releaseRecord()
            wavWriter?.abortAndDelete()
            endpointer?.close()
        }
    }

    private fun releaseRecord() {
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
    }
}
