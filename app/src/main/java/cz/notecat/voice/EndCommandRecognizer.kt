package cz.notecat.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class EndCommandRecognizer(context: Context, private val onEnd: () -> Unit) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(this@EndCommandRecognizer) }
    fun start() = recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    fun stop() = recognizer.destroy()
    private fun handle(words: List<String>) { if (words.any { it.trim().lowercase(Locale("cs", "CZ")) in setOf("konec", "ukončit", "uložit") }) onEnd() }
    override fun onResults(r: Bundle) = handle(r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
    override fun onPartialResults(r: Bundle) = handle(r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
    override fun onError(error: Int) { /* non-fatal: button remains primary fallback */ }
    override fun onReadyForSpeech(p0: Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(p0: Float) {}; override fun onBufferReceived(p0: ByteArray?) {}; override fun onEndOfSpeech() {}; override fun onEvent(p0: Int, p1: Bundle?) {}
}
