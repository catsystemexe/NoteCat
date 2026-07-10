package cz.notecat

import cz.notecat.data.ProcessingStatus
import cz.notecat.ui.demo.DemoNote
import cz.notecat.ui.demo.NoteCatDemoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCatDemoStateTest {
    @Test fun insertingProcessingNotePlacesNewestFirst() {
        val state = NoteCatDemoState(listOf(DemoNote("old", 10L, text = "old", originalTranscript = "old")))
        val note = state.insertProcessing(20L)
        assertEquals(note.id, state.currentNotes().first().id)
        assertEquals(ProcessingStatus.PROCESSING, state.currentNotes().first().status)
    }

    @Test fun processingNoteCanCompleteToDone() {
        val state = NoteCatDemoState(emptyList())
        val note = state.insertProcessing(20L)
        state.completeProcessing(note.id, "Hotovo", "hotovo", 30L)
        val completed = state.currentNotes().first()
        assertEquals(ProcessingStatus.DONE, completed.status)
        assertEquals("Hotovo", completed.text)
        assertEquals("hotovo", completed.originalTranscript)
    }

    @Test fun failedNoteRetryTransitionsThroughProcessingToDone() {
        val state = NoteCatDemoState(listOf(DemoNote("failed", 10L, text = "fail", originalTranscript = "", status = ProcessingStatus.FAILED, errorMessage = "chyba")))
        state.retry("failed", 20L)
        assertEquals(ProcessingStatus.PROCESSING, state.currentNotes().first().status)
        state.completeRetry("failed", 30L)
        assertEquals(ProcessingStatus.DONE, state.currentNotes().first().status)
    }

    @Test fun editingUpdatesTextOnlyWhenSaved() {
        val state = NoteCatDemoState(listOf(DemoNote("note", 10L, text = "původní", originalTranscript = "původní")))
        state.edit("note", "upravené", 20L)
        assertEquals("upravené", state.currentNotes().first().text)
    }

    @Test fun deleteAndUndoRestoresOriginalPosition() {
        val state = NoteCatDemoState(listOf(DemoNote("new", 30L, text = "new", originalTranscript = "new"), DemoNote("old", 10L, text = "old", originalTranscript = "old")))
        val deleted = state.delete("old")
        assertNotNull(deleted)
        assertEquals(1, state.currentNotes().size)
        state.undo(deleted!!.first, deleted.second)
        assertEquals(listOf("new", "old"), state.currentNotes().map { it.id })
    }

    @Test fun newestNoteIsAlwaysOnTop() {
        val state = NoteCatDemoState(listOf(DemoNote("old", 10L, text = "old", originalTranscript = "old"), DemoNote("new", 40L, text = "new", originalTranscript = "new")))
        assertTrue(state.currentNotes().zipWithNext().all { it.first.createdAt >= it.second.createdAt })
    }
}
