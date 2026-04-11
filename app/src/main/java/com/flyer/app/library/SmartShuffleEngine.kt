package com.flyer.app.library

import android.content.Context
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartShuffleEngine(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val trackDao = db.trackDao()
    private val statsDao = db.trackStatsDao()

    suspend fun buildQueue(queueSize: Int = 50): List<TrackFile> = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAllTracksOnce()
        if (tracks.isEmpty()) return@withContext emptyList()

        val allStats = statsDao.getAllStatsOnce()
        val statsMap = allStats.associateBy { it.canonicalTrackId }
        val now = System.currentTimeMillis()

        val weighted = tracks.map { track ->
            val stats = statsMap[track.canonicalTrackId]
            val baseScore = stats?.affinityScore ?: 0f
            val fatigue = fatiguePenalty(stats, now)
            val weight = maxOf(baseScore + fatigue + 5f, 0.5f)
            Pair(track, weight)
        }

        weightedSample(weighted, minOf(queueSize, tracks.size))
    }

    private fun fatiguePenalty(stats: TrackStats?, now: Long): Float {
        if (stats == null || stats.lastPlayedAt == 0L) return 0f
        val minutesSince = (now - stats.lastPlayedAt) / (1000L * 60)
        return when {
            minutesSince <= 30   -> -7f
            minutesSince <= 120  -> -5f
            minutesSince <= 1440 -> -3f
            else                 -> 0f
        }
    }

    private fun weightedSample(
        items: List<Pair<TrackFile, Float>>,
        count: Int
    ): List<TrackFile> {
        val result = mutableListOf<TrackFile>()
        val pool = items.toMutableList()

        repeat(count) {
            if (pool.isEmpty()) return result
            val total = pool.sumOf { it.second.toDouble() }.toFloat()
            var random = (Math.random() * total).toFloat()
            val index = pool.indexOfFirst { (_, w) ->
                random -= w
                random <= 0
            }.takeIf { it >= 0 } ?: 0
            result.add(pool[index].first)
            pool.removeAt(index)
        }
        return result
    }
}