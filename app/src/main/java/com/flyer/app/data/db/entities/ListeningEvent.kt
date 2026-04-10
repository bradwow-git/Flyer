package com.flyer.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_events",
    foreignKeys = [ForeignKey(
        entity = CanonicalTrack::class,
        parentColumns = ["id"],
        childColumns = ["canonicalTrackId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalTrackId")]
)
data class ListeningEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalTrackId: Long,
    val eventType: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)