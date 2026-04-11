package com.flyer.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_stats",
    foreignKeys = [ForeignKey(
        entity = CanonicalTrack::class,
        parentColumns = ["id"],
        childColumns = ["canonicalTrackId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalTrackId", unique = true)]
)
data class TrackStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalTrackId: Long,
    val totalPlays: Int = 0,
    val completions: Int = 0,
    val earlySkips: Int = 0,
    val midSkips: Int = 0,
    val lateSkips: Int = 0,
    val replays: Int = 0,
    val affinityScore: Float = 0f,
    val lastPlayedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)