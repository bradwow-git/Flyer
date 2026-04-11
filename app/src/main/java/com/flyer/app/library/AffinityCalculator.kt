package com.flyer.app.library

import android.content.Context
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.min

class AffinityCalculator(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val eventDao = db.listeningEventDao()
    private val statsDao = db.trackStatsDao()
    private val trackDao = db.trackDao()

    suspend fun recalculateAll() = withContext(Dispatchers.IO) {
        val allTracks = trackDao.getAllTracksOnce()
        for (track in allTracks) {
            recalculateForTrack(track.canonicalTrackId)
        }
    }

    suspend fun recalculateForTrack(canonicalTrackId: Long) = withContext(Dispatchers.IO) {
        val totalPlays = eventDao.getEventCount(canonicalTrackId, EventType.PLAY_START)
        val completions = eventDao.getEventCount(canonicalTrackId, EventType.PLAY_COMPLETE)
        val earlySkips = eventDao.getEventCount(canonicalTrackId, EventType.SKIP_EARLY)
        val midSkips = eventDao.getEventCount(canonicalTrackId, EventType.SKIP_MID)
        val lateSkips = eventDao.getEventCount(canonicalTrackId, EventType.SKIP_LATE)
        val replays = eventDao.getEventCount(canonicalTrackId, EventType.REPLAY)

        val existing = statsDao.getStatsForTrack(canonicalTrackId)
        val lastPlayedAt = existing?.lastPlayedAt ?: 0L

        // Completion score (0-20)
        val completionRate = completions.toFloat() / maxOf(totalPlays, 1)
        val completionScore = completionRate * 20f

        // Replay score (0-15)
        val replayRate = replays.toFloat() / maxOf(totalPlays, 1)
        val replayScore = min(replayRate, 1f) * 15f

        // Play count score with diminishing returns (0-10)
        val playCountScore = min(
            log10(totalPlays.toDouble() + 1).toFloat() / 2f,
            1f
        ) * 10f

        // Recency score using buckets (0-15)
        val now = System.currentTimeMillis()
        val daysSinceLastPlay = if (lastPlayedAt > 0L) {
            (now - lastPlayedAt) / (1000L * 60 * 60 * 24)
        } else {
            Long.MAX_VALUE
        }
        val recencyScore = when {
            daysSinceLastPlay <= 1  -> 15f
            daysSinceLastPlay <= 3  -> 12f
            daysSinceLastPlay <= 7  -> 8f
            daysSinceLastPlay <= 14 -> 5f
            daysSinceLastPlay <= 30 -> 2f
            else                    -> 0f
        }

        // Skip penalty (max -20)
        val rawSkipPenalty = (earlySkips * 1.0f) +
                (midSkips * 0.5f) +
                (lateSkips * 0.15f)
        val skipPenalty = -min(rawSkipPenalty, 20f)

        val behavioralScore = completionScore + replayScore +
                playCountScore + recencyScore + skipPenalty

        // Explicit and context scores come next phase
        val finalScore = behavioralScore

        statsDao.insertOrReplace(
            TrackStats(
                id = existing?.id ?: 0,
                canonicalTrackId = canonicalTrackId,
                totalPlays = totalPlays,
                completions = completions,
                earlySkips = earlySkips,
                midSkips = midSkips,
                lateSkips = lateSkips,
                replays = replays,
                affinityScore = finalScore,
                lastPlayedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}