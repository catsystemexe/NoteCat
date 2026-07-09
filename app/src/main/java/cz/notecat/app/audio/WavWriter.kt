package cz.notecat.app.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Zapisuje PCM16 mono data do WAV souboru. Hlavička se dopíše při [close],
 * takže i po pádu aplikace zůstane soubor čitelný po opravě hlavičky
 * (Whisper i tak umí surová data s částečnou hlavičkou odmítnout – proto
 * save-first flow volá [close] ještě před vložením poznámky do DB).
 */
class WavWriter(private val file: File, private val sampleRate: Int = 16000) {

    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes: Long = 0
    private var closed = false

    init {
        raf.setLength(0)
        raf.write(buildHeader(sampleRate, 0))
    }

    fun write(buffer: ByteArray, length: Int) {
        check(!closed) { "WavWriter je již uzavřen" }
        raf.write(buffer, 0, length)
        dataBytes += length
    }

    /** Dopíše správné velikosti do hlavičky a zavře soubor. */
    fun close() {
        if (closed) return
        closed = true
        raf.seek(0)
        raf.write(buildHeader(sampleRate, dataBytes))
        raf.fd.sync()
        raf.close()
    }

    fun abortAndDelete() {
        runCatching { raf.close() }
        closed = true
        file.delete()
    }

    val bytesWritten: Long get() = dataBytes

    companion object {
        const val HEADER_SIZE = 44

        fun buildHeader(sampleRate: Int, dataBytes: Long): ByteArray {
            val byteRate = sampleRate * 2 // mono, 16 bit
            val totalDataLen = dataBytes + HEADER_SIZE - 8
            val h = ByteArray(HEADER_SIZE)
            fun putLE(offset: Int, value: Int, size: Int) {
                for (i in 0 until size) h[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
            }
            "RIFF".toByteArray().copyInto(h, 0)
            putLE(4, totalDataLen.toInt(), 4)
            "WAVE".toByteArray().copyInto(h, 8)
            "fmt ".toByteArray().copyInto(h, 12)
            putLE(16, 16, 4)          // velikost fmt bloku
            putLE(20, 1, 2)           // PCM
            putLE(22, 1, 2)           // mono
            putLE(24, sampleRate, 4)
            putLE(28, byteRate, 4)
            putLE(32, 2, 2)           // block align
            putLE(34, 16, 2)          // bits per sample
            "data".toByteArray().copyInto(h, 36)
            putLE(40, dataBytes.toInt(), 4)
            return h
        }
    }
}
