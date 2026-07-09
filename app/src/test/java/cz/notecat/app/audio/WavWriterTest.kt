package cz.notecat.app.audio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavWriterTest {

    private fun readLE(bytes: ByteArray, offset: Int, size: Int): Long {
        var v = 0L
        for (i in 0 until size) {
            v = v or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
        }
        return v
    }

    @Test
    fun `hlavicka ma spravny format a velikosti`() {
        val file = File.createTempFile("test", ".wav")
        try {
            val writer = WavWriter(file, sampleRate = 16000)
            val data = ByteArray(3200) { (it % 127).toByte() } // 100 ms zvuku
            writer.write(data, data.size)
            writer.close()

            val bytes = file.readBytes()
            assertEquals(WavWriter.HEADER_SIZE + data.size, bytes.size)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            assertEquals("fmt ", String(bytes, 12, 4))
            assertEquals("data", String(bytes, 36, 4))
            assertEquals(1L, readLE(bytes, 20, 2))       // PCM
            assertEquals(1L, readLE(bytes, 22, 2))       // mono
            assertEquals(16000L, readLE(bytes, 24, 4))   // sample rate
            assertEquals(32000L, readLE(bytes, 28, 4))   // byte rate
            assertEquals(16L, readLE(bytes, 34, 2))      // bits per sample
            assertEquals(data.size.toLong(), readLE(bytes, 40, 4)) // velikost dat
        } finally {
            file.delete()
        }
    }

    @Test
    fun `abortAndDelete smaze soubor`() {
        val file = File.createTempFile("test", ".wav")
        val writer = WavWriter(file)
        writer.write(ByteArray(100), 100)
        writer.abortAndDelete()
        assertTrue(!file.exists())
    }

    @Test
    fun `prazdny zaznam ma jen hlavicku`() {
        val file = File.createTempFile("test", ".wav")
        try {
            val writer = WavWriter(file)
            writer.close()
            assertEquals(WavWriter.HEADER_SIZE.toLong(), file.length())
        } finally {
            file.delete()
        }
    }
}
