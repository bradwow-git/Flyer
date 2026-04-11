package com.flyer.app.data.db.dao

import androidx.room.*
import com.flyer.app.data.db.entities.TrackStats
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackStatsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(stats: TrackStats)

    @Query("SELECT * FROM track_stats WHERE canonicalTrackId = :trackId LIMIT 1")
    suspend fun getStatsForTrack(trackId: Long): TrackStats?

    @Query("SELECT * FROM track_stats ORDER BY affinityScore DESC")
    fun getAllStatsSortedByAffinity(): Flow<List<TrackStats>>

    @Query("SELECT * FROM track_stats ORDER BY affinityScore DESC LIMIT :limit")
    suspend fun getTopTracks(limit: Int): List<TrackStats>

    @Query("SELECT * FROM track_stats ORDER BY earlySkips DESC LIMIT :limit")
    suspend fun getMostSkippedTracks(limit: Int): List<TrackStats>
}