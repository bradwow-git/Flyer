package com.flyer.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_files",
    foreignKeys = [ForeignKey(
        entity = CanonicalTrack::class,
        parentColumns = ["id"],
        childColumns = ["canonicalTrackId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalTrackId")]
)
data class TrackFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalTrackId: Long,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lastScannedAt: Long = System.currentTimeMillis()
)