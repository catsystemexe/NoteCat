package cz.notecat.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    fun start(): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        file = File.createTempFile("capture-", ".m4a", dir)
        recorder = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioSamplingRate(44100); setAudioEncodingBitRate(96000)
            setOutputFile(file!!.absolutePath); prepare(); start()
        }
        return file!!
    }
    fun stopSafely(): File { recorder?.apply { runCatching { stop() }; reset(); release() }; recorder = null; return requireNotNull(file) }
}
