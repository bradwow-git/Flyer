package com.flyer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flyer.app.data.db.dao.TrackDao
import com.flyer.app.data.db.entities.CanonicalTrack
import com.flyer.app.data.db.entities.TrackFile

@Database(
    entities = [CanonicalTrack::class, TrackFile::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flyer_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}