package com.flyer.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flyer.app.data.db.dao.ListeningEventDao
import com.flyer.app.data.db.dao.TrackDao
import com.flyer.app.data.db.dao.TrackStatsDao
import com.flyer.app.data.db.entities.CanonicalTrack
import com.flyer.app.data.db.entities.ListeningEvent
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.TrackStats

@Database(
    entities = [
        CanonicalTrack::class,
        TrackFile::class,
        ListeningEvent::class,
        TrackStats::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun trackStatsDao(): TrackStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flyer_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}