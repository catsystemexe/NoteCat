package cz.notecat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query(
        """
        SELECT * FROM notes
        WHERE polishedText LIKE '%' || :query || '%'
           OR rawTranscript LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<Note?>

    @Query("SELECT * FROM notes WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<NoteStatus>): List<Note>

    @Query("SELECT audioPath FROM notes WHERE audioPath IS NOT NULL")
    suspend fun getAllAudioPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}
