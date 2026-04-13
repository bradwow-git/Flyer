package com.flyer.app.data.repository

import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.TrackStats
import com.flyer.app.data.db.entities.UserFeedback
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val db: AppDatabase) {

    // ── Tracks ─────────────────────────────────────────────────────────────

    fun getAllTracks(): Flow<List<TrackFile>> =
        db.trackDao().getAllTracks()

    suspend fun getAllTracksOnce(): List<TrackFile> =
        db.trackDao().getAllTracksOnce()

    // ── Stats ──────────────────────────────────────────────────────────────

    fun getAllStatsSortedByAffinity(): Flow<List<TrackStats>> =
        db.trackStatsDao().getAllStatsSortedByAffinity()

    suspend fun getTopTracks(limit: Int): List<TrackStats> =
        db.trackStatsDao().getTopTracks(limit)

    suspend fun getMostSkippedTracks(limit: Int): List<TrackStats> =
        db.trackStatsDao().getMostSkippedTracks(limit)

    // ── Feedback ───────────────────────────────────────────────────────────

    suspend fun insertOrReplaceFeedback(feedback: UserFeedback) =
        db.userFeedbackDao().insertOrReplace(feedback)

    suspend fun getFeedbackForTrack(canonicalTrackId: Long): UserFeedback? =
        db.userFeedbackDao().getFeedbackForTrack(canonicalTrackId)

    suspend fun deleteFeedbackForTrack(canonicalTrackId: Long) =
        db.userFeedbackDao().deleteFeedbackForTrack(canonicalTrackId)
}
