package com.flyer.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "canonical_tracks")
data class CanonicalTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbum: String,
    val durationMs: Long
)