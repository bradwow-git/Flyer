package com.flyer.app.data.db.dao

import androidx.room.*
import com.flyer.app.data.db.entities.CanonicalTrack
import com.flyer.app.data.db.entities.TrackFile
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM track_files ORDER BY artist, title")
    fun getAllTracks(): Flow<List<TrackFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackFile(trackFile: TrackFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanonicalTrack(track: CanonicalTrack): Long

    @Query("SELECT * FROM track_files WHERE filePath = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackFile?

    @Query("SELECT COUNT(*) FROM track_files")
    suspend fun getTrackCount(): Int

    @Query("SELECT * FROM track_files")
    suspend fun getAllTracksOnce(): List<TrackFile>
}