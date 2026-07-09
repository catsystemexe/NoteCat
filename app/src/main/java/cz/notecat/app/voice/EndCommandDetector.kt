package cz.notecat.app.voice

/**
 * Čistá (JVM-testovatelná) logika rozpoznání ukončovacího povelu „konec".
 *
 * Vstupem jsou výsledky Vosk rozpoznávače běžícího s omezenou gramatikou
 * ["konec", "[unk]"] – každé jiné slovo se mapuje na [unk].
 *
 * Pravidla proti falešnému ukončení:
 *  1. Povel platí jen jako FINÁLNÍ výsledek celé promluvy, jejíž text je
 *     přesně "konec" (uživatel udělá krátkou pauzu a řekne povel samostatně).
 *     „...a nakonec zavolat mámě" tedy diktát neukončí – povel je obklopen
 *     dalšími tokeny v téže promluvě.
 *  2. Povel se ignoruje během prvních [minRecordingMs] nahrávky, aby náhodný
 *     šum při startu nemohl záznam okamžitě zavřít.
 *
 * Když povel není rozpoznán (hluk, tichý hlas), nic se nestane – uživatel
 * má vždy k dispozici tlačítko.
 */
class EndCommandDetector(
    private val command: String = "konec",
    private val minRecordingMs: Long = 1500,
) {

    /**
     * Zpracuje finální výsledek promluvy (JSON z Recognizer.result / finalResult).
     * @return true pokud má nahrávání skončit.
     */
    fun onFinalResult(json: String, elapsedMs: Long): Boolean {
        if (elapsedMs < minRecordingMs) return false
        val text = extractJsonStringField(json, "text") ?: return false
        return isCommandUtterance(text)
    }

    /** Promluva je povelem, jen pokud neobsahuje nic jiného než povel. */
    fun isCommandUtterance(text: String): Boolean {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tokens.size == 1 && tokens[0].equals(command, ignoreCase = true)
    }

    companion object {
        /**
         * Minimalistický extraktor stringového pole z jednoduchého JSONu,
         * který produkuje Vosk ({"text" : "..."}). Bez závislosti na org.json,
         * aby šla logika testovat na JVM.
         */
        fun extractJsonStringField(json: String, field: String): String? {
            val keyIdx = json.indexOf("\"$field\"")
            if (keyIdx < 0) return null
            val colon = json.indexOf(':', keyIdx + field.length + 2)
            if (colon < 0) return null
            val start = json.indexOf('"', colon + 1)
            if (start < 0) return null
            val sb = StringBuilder()
            var i = start + 1
            while (i < json.length) {
                when (val c = json[i]) {
                    '\\' -> {
                        if (i + 1 < json.length) {
                            sb.append(json[i + 1])
                            i++
                        }
                    }
                    '"' -> return sb.toString()
                    else -> sb.append(c)
                }
                i++
            }
            return null
        }
    }
}
