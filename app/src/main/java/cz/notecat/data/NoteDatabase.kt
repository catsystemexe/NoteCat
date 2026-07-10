package cz.notecat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class NoteConverters { @TypeConverter fun toStatus(v: String)=ProcessingStatus.valueOf(v); @TypeConverter fun fromStatus(v: ProcessingStatus)=v.name }
@Database(entities = [Note::class], version = 1)
@TypeConverters(NoteConverters::class)
abstract class NoteDatabase: RoomDatabase() { abstract fun noteDao(): NoteDao }
