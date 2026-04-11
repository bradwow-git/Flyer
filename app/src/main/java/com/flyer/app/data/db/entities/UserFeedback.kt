package com.flyer.app.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_feedback",
    foreignKeys = [ForeignKey(
        entity = CanonicalTrack::class,
        parentColumns = ["id"],
        childColumns = ["canonicalTrackId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("canonicalTrackId", unique = true)]
)
data class UserFeedback(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val canonicalTrackId: Long,
    val feedbackType: String,
    val updatedAt: Long = System.currentTimeMillis()
)