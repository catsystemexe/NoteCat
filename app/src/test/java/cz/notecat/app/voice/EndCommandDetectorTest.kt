package cz.notecat.app.voice

import cz.notecat.app.voice.EndCommandDetector.Companion.extractJsonStringField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndCommandDetectorTest {

    private val detector = EndCommandDetector(minRecordingMs = 1500)

    // --- Rozpoznání povelu (scénář 5) ---

    @Test
    fun `samostatny povel konec ukonci nahravani`() {
        assertTrue(detector.onFinalResult("""{"text" : "konec"}""", elapsedMs = 5000))
    }

    @Test
    fun `povel s velkym pismenem funguje`() {
        assertTrue(detector.isCommandUtterance("Konec"))
    }

    @Test
    fun `povel s okolnimi mezerami funguje`() {
        assertTrue(detector.isCommandUtterance("  konec  "))
    }

    // --- Ochrana proti falešnému ukončení (scénář 8) ---

    @Test
    fun `konec uprostred promluvy neukonci`() {
        // „…a nakonec zavolat mámě“ -> gramatika vrátí okolní tokeny jako [unk]
        assertFalse(detector.onFinalResult("""{"text" : "[unk] konec [unk]"}""", 5000))
    }

    @Test
    fun `konec na konci delsi promluvy neukonci`() {
        assertFalse(detector.onFinalResult("""{"text" : "[unk] [unk] konec"}""", 5000))
    }

    @Test
    fun `prazdny vysledek neukonci`() {
        assertFalse(detector.onFinalResult("""{"text" : ""}""", 5000))
    }

    @Test
    fun `povel v prvnich vterinach se ignoruje`() {
        assertFalse(detector.onFinalResult("""{"text" : "konec"}""", elapsedMs = 800))
    }

    @Test
    fun `nevalidni json neukonci`() {
        assertFalse(detector.onFinalResult("not json", 5000))
    }

    // --- Extrakce JSON pole (formát Vosku) ---

    @Test
    fun `extrahuje text z vosk vysledku`() {
        assertEquals("konec", extractJsonStringField("""{ "text" : "konec" }""", "text"))
    }

    @Test
    fun `extrahuje partial pole`() {
        assertEquals(
            "[unk] konec",
            extractJsonStringField("""{"partial" : "[unk] konec"}""", "partial")
        )
    }

    @Test
    fun `chybejici pole vraci null`() {
        assertNull(extractJsonStringField("""{"text" : "konec"}""", "partial"))
    }

    @Test
    fun `zvlada escapovane uvozovky`() {
        assertEquals(
            """rekl "ahoj"""",
            extractJsonStringField("""{"text" : "rekl \"ahoj\""}""", "text")
        )
    }
}
