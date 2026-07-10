package cz.notecat.ui.demo

import cz.notecat.data.ProcessingStatus

private const val PROCESSING_TEXT = "Zpracovávám poznámku…"

data class DemoNote(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val text: String,
    val originalTranscript: String,
    val status: ProcessingStatus = ProcessingStatus.DONE,
    val errorMessage: String? = null,
)

class NoteCatDemoState(initialNotes: List<DemoNote> = demoNotes()) {
    private val notes = initialNotes.toMutableList().sortedByDescending { it.createdAt }.toMutableList()
    private var nextId = notes.size + 1

    fun currentNotes(): List<DemoNote> = notes.toList()

    fun insertProcessing(now: Long): DemoNote {
        val note = DemoNote(
            id = "demo-new-${nextId++}",
            createdAt = now,
            text = PROCESSING_TEXT,
            originalTranscript = "Simulovaný hlasový záznam čeká na přepis.",
            status = ProcessingStatus.PROCESSING,
        )
        notes.add(0, note)
        return note
    }

    fun completeProcessing(id: String, text: String, originalTranscript: String, now: Long) = update(id) {
        it.copy(text = text, originalTranscript = originalTranscript, status = ProcessingStatus.DONE, errorMessage = null, updatedAt = now)
    }

    fun retry(id: String, now: Long) = update(id) {
        it.copy(text = PROCESSING_TEXT, status = ProcessingStatus.PROCESSING, errorMessage = null, updatedAt = now)
    }

    fun completeRetry(id: String, now: Long) = completeProcessing(
        id = id,
        text = "Opakované zpracování proběhlo úspěšně. Poznámka je připravená k použití.",
        originalTranscript = "Opakované zpracování proběhlo úspěšně poznámka je připravená k použití",
        now = now,
    )

    fun edit(id: String, text: String, now: Long) = update(id) { it.copy(text = text, updatedAt = now) }

    fun delete(id: String): Pair<DemoNote, Int>? {
        val index = notes.indexOfFirst { it.id == id }
        if (index < 0) return null
        return notes.removeAt(index) to index
    }

    fun undo(note: DemoNote, index: Int) {
        if (notes.any { it.id == note.id }) return
        notes.add(index.coerceIn(0, notes.size), note)
    }

    private fun update(id: String, block: (DemoNote) -> DemoNote) {
        val index = notes.indexOfFirst { it.id == id }
        if (index >= 0) notes[index] = block(notes[index])
        notes.sortByDescending { it.createdAt }
    }
}

fun demoNotes(baseTime: Long = 1_725_000_000_000L): List<DemoNote> = listOf(
    DemoNote("demo-processing", baseTime + 5_000, text = PROCESSING_TEXT, originalTranscript = "", status = ProcessingStatus.PROCESSING),
    DemoNote("demo-short", baseTime + 4_000, text = "Koupit cestou domů kávu a krmivo pro kočku.", originalTranscript = "koupit cestou domů kávu a krmivo pro kočku"),
    DemoNote("demo-work", baseTime + 3_000, text = "Na standupu zmínit nový onboarding, stav Android buildu a připravit otázky pro design review.", originalTranscript = "na standupu zmínit nový onboarding stav android buildu a připravit otázky pro design review"),
    DemoNote("demo-personal", baseTime + 2_000, text = "Myšlenka: zapisovat méně věcí, ale každou poznámku večer rovnou přetavit v konkrétní další krok.", originalTranscript = "myšlenka zapisovat méně věcí ale každou poznámku večer rovnou přetavit v konkrétní další krok"),
    DemoNote("demo-long", baseTime + 1_000, text = "Dlouhá poznámka k víkendovému plánu: v sobotu ráno vyrazit na nákup, potom uklidit pracovní stůl a odpoledne dokončit čtení článku o produktivitě.", originalTranscript = "dlouhá poznámka k víkendovému plánu v sobotu ráno vyrazit na nákup potom uklidit pracovní stůl a odpoledne dokončit čtení článku o produktivitě"),
    DemoNote("demo-failed", baseTime, text = "Zpracování selhalo. Zvuk byl příliš tichý.", originalTranscript = "", status = ProcessingStatus.FAILED, errorMessage = "Zvuk byl příliš tichý."),
)
