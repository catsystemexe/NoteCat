package cz.notecat

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import cz.notecat.data.NoteDatabase

class NoteCatApp: Application(), Configuration.Provider {
    lateinit var db: NoteDatabase
    override fun onCreate() { super.onCreate(); db = Room.databaseBuilder(this, NoteDatabase::class.java, "notecat.db").build() }
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()
}
