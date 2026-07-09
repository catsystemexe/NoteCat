package cz.notecat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stav životního cyklu poznámky.
 *
 * PENDING     – lokálně uložená, čeká na síťové zpracování (audio existuje)
 * PROCESSING  – právě probíhá přepis + AI úprava (audio existuje)
 * DONE        – hotovo, audio bylo smazáno
 * FAILED      – zpracování selhalo, audio zůstává pro opakování
 */
enum class NoteStatus { PENDING, PROCESSING, DONE, FAILED }

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Původní přepis z STT – ukládá se odděleně a nikdy se nepřepisuje editací. */
    val rawTranscript: String? = null,
    /** AI-upravený (nebo ručně editovaný) text poznámky. */
    val polishedText: String? = null,
    val status: NoteStatus = NoteStatus.PENDING,
    /** Cesta k dočasnému WAV souboru; null po úspěšném zpracování. */
    val audioPath: String? = null,
    /** Uživatelsky srozumitelný popis poslední chyby. */
    val errorMessage: String? = null,
    /** Klíč pro idempotentní zpracování na backendu (stabilní napříč retry). */
    val idempotencyKey: String = id,
    val durationMs: Long = 0,
) {
    /** Text, který se má zobrazit v UI (upravený má přednost před přepisem). */
    val displayText: String
        get() = polishedText?.takeIf { it.isNotBlank() }
            ?: rawTranscript?.takeIf { it.isNotBlank() }
            ?: ""
}
