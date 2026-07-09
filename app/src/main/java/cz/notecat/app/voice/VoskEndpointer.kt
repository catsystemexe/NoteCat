package cz.notecat.app.voice

import org.vosk.Model
import org.vosk.Recognizer

/**
 * Streamovací rozpoznávač povelu „konec". Krmený stejnými PCM buffery,
 * které jdou do WAV souboru – mikrofon tak drží jen jedna komponenta.
 *
 * Používá gramatikou omezené rozpoznávání (["konec", "[unk]"]), které je
 * v hluku výrazně robustnější než plný přepis.
 */
class VoskEndpointer(
    model: Model,
    sampleRate: Float = 16000f,
    private val detector: EndCommandDetector = EndCommandDetector(),
) : AutoCloseable {

    private val recognizer = Recognizer(model, sampleRate, "[\"konec\", \"[unk]\"]")

    /**
     * @param elapsedMs čas od začátku nahrávání
     * @return true pokud byl rozpoznán ukončovací povel
     */
    fun acceptAudio(buffer: ByteArray, length: Int, elapsedMs: Long): Boolean {
        val utteranceEnded = recognizer.acceptWaveForm(buffer, length)
        if (utteranceEnded) {
            return detector.onFinalResult(recognizer.result, elapsedMs)
        }
        return false
    }

    override fun close() {
        runCatching { recognizer.close() }
    }
}
