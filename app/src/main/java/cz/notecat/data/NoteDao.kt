package cz.notecat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE noteText LIKE '%' || :query || '%' OR transcriptOriginal LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun observeNotes(query: String = ""): Flow<List<Note>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): Note?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(note: Note)
    @Update suspend fun update(note: Note)
    @Delete suspend fun delete(note: Note)
    @Query("SELECT * FROM notes WHERE processingStatus = 'DONE' AND audioPath IS NOT NULL AND updatedAt < :olderThan") suspend fun doneWithAudio(olderThan: Long): List<Note>
}
