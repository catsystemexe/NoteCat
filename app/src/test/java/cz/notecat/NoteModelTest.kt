package cz.notecat

import cz.notecat.data.Note
import cz.notecat.data.ProcessingStatus
import org.junit.Assert.*
import org.junit.Test

class NoteModelTest {
    @Test fun newNoteIsPendingAndIdempotent() { val note = Note(audioPath = "/tmp/a.m4a"); assertEquals(ProcessingStatus.PENDING, note.processingStatus); assertTrue(note.idempotencyKey.isNotBlank()); assertEquals(note.createdAt, note.updatedAt) }
}
