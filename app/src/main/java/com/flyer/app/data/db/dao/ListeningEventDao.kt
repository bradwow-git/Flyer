package com.flyer.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.flyer.app.data.db.entities.ListeningEvent

@Dao
interface ListeningEventDao {

    @Insert
    suspend fun insert(event: ListeningEvent)

    @Query("SELECT * FROM listening_events WHERE canonicalTrackId = :trackId ORDER BY timestamp DESC")
    suspend fun getEventsForTrack(trackId: Long): List<ListeningEvent>

    @Query("SELECT COUNT(*) FROM listening_events WHERE canonicalTrackId = :trackId AND eventType = :eventType")
    suspend fun getEventCount(trackId: Long, eventType: String): Int

    @Query("SELECT COUNT(*) FROM listening_events")
    suspend fun getTotalEventCount(): Int

    @Query("SELECT * FROM listening_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<ListeningEvent>
}