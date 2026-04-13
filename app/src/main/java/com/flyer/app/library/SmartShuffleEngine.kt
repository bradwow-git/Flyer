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
    private val eventDao = db.listeningEventDao()
    private val config = SmartShuffleConfig()

    // ── Internal models ────────────────────────────────────────────────────

    private enum class Tier { A, B, C }

    private data class Candidate(
        val track: TrackFile,
        val effectiveScore: Float,
        val tier: Tier
    )

    private data class Momentum(
        val consecutiveSkips: Int,
        val consecutiveCompletions: Int
    )

    // ── Public API ─────────────────────────────────────────────────────────

    suspend fun buildQueue(queueSize: Int = 50): List<TrackFile> = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAllTracksOnce()
        if (tracks.isEmpty()) return@withContext emptyList()

        val allStats = statsDao.getAllStatsOnce()
        val statsMap = allStats.associateBy { it.canonicalTrackId }
        val now = System.currentTimeMillis()

        // Step 1 — Hard exclusions: NEVER_PLAY and recent cooldown
        val eligible = tracks.filter { track ->
            val stats = statsMap[track.canonicalTrackId]
            val score = stats?.affinityScore ?: 0f
            if (score <= config.neverPlayCutoff) return@filter false
            val lastPlayed = stats?.lastPlayedAt ?: 0L
            if (lastPlayed > 0L && (now - lastPlayed) < config.trackCooldownMs) return@filter false
            true
        }

        if (eligible.isEmpty()) return@withContext emptyList()

        // Step 2 — Apply fatigue penalty, sort highest score first
        val scored = eligible.map { track ->
            val stats = statsMap[track.canonicalTrackId]
            val base = stats?.affinityScore ?: 0f
            val fatigue = fatiguePenalty(stats, now)
            Pair(track, base + fatigue)
        }.sortedByDescending { it.second }

        // Step 3 — Take candidate pool (top N by score)
        val pool = scored.take(config.candidatePoolSize)
        val poolSize = pool.size

        // Step 4 — Assign tiers by ranked position
        val tierACount = maxOf(1, (poolSize * config.tierAPercent).toInt())
        val tierBCount = maxOf(1, (poolSize * config.tierBPercent).toInt())

        val candidates = pool.mapIndexed { index, (track, score) ->
            val tier = when {
                index < tierACount -> Tier.A
                index < tierACount + tierBCount -> Tier.B
                else -> Tier.C
            }
            Candidate(track, score, tier)
        }

        val tierBuckets = mapOf(
            Tier.A to candidates.filter { it.tier == Tier.A }.toMutableList(),
            Tier.B to candidates.filter { it.tier == Tier.B }.toMutableList(),
            Tier.C to candidates.filter { it.tier == Tier.C }.toMutableList()
        )

        // Step 5 — Determine momentum from recent listening history
        val momentum = computeMomentum()
        val (wA, wB, wC) = adjustedWeights(momentum)

        // Step 6 — Build queue slot by slot with anti-repetition rules
        val result = mutableListOf<TrackFile>()
        val recentArtists = ArrayDeque<String>() // sliding window for artist spacing

        repeat(minOf(queueSize, pool.size)) {
            val track = pickNext(tierBuckets, wA, wB, wC, recentArtists) ?: return@repeat
            result.add(track)
            recentArtists.addLast(track.artist)
            if (recentArtists.size > config.artistSpacing) recentArtists.removeFirst()
        }

        result
    }

    // ── Selection logic ────────────────────────────────────────────────────

    /**
     * Pick the next track. Selects a tier by weighted random roll, then picks the
     * first candidate in that tier whose artist is not in the recent-artist window.
     * Falls back to the other tiers if the chosen tier is empty or all artists blocked.
     * Last resort: artist spacing is relaxed so the queue always fills.
     */
    private fun pickNext(
        buckets: Map<Tier, MutableList<Candidate>>,
        wA: Float,
        wB: Float,
        wC: Float,
        recentArtists: ArrayDeque<String>
    ): TrackFile? {
        val tierOrder = weightedTierOrder(wA, wB, wC)

        // Primary pass: honour artist spacing
        for (tier in tierOrder) {
            val bucket = buckets[tier] ?: continue
            val idx = bucket.indexOfFirst { it.track.artist !in recentArtists }
            if (idx >= 0) {
                return bucket.removeAt(idx).track
            }
        }

        // Fallback pass: relax artist spacing so we still fill the queue
        for (tier in tierOrder) {
            val bucket = buckets[tier] ?: continue
            if (bucket.isNotEmpty()) {
                return bucket.removeAt(0).track
            }
        }

        return null
    }

    /**
     * Roll a random number across the combined tier weights and return a priority-ordered
     * list of tiers so that [pickNext] can try them in sequence.
     */
    private fun weightedTierOrder(wA: Float, wB: Float, wC: Float): List<Tier> {
        val roll = (Math.random() * (wA + wB + wC)).toFloat()
        val primary = when {
            roll < wA -> Tier.A
            roll < wA + wB -> Tier.B
            else -> Tier.C
        }
        return when (primary) {
            Tier.A -> listOf(Tier.A, Tier.B, Tier.C)
            Tier.B -> listOf(Tier.B, Tier.A, Tier.C)
            Tier.C -> listOf(Tier.C, Tier.B, Tier.A)
        }
    }

    // ── Momentum ───────────────────────────────────────────────────────────

    /**
     * Read the most recent outcome events (skips / completions) and count how many
     * of the same type appear consecutively at the top of the list.
     */
    private suspend fun computeMomentum(): Momentum {
        val recent = try {
            eventDao.getRecentEvents(config.recentEventLookback)
        } catch (e: Exception) {
            return Momentum(0, 0)
        }

        // Filter to outcome events only — ignore PLAY_START and REPLAY
        val outcomes = recent.filter {
            it.eventType == EventType.SKIP_EARLY ||
                    it.eventType == EventType.PLAY_COMPLETE
        }

        var consecutiveSkips = 0
        var consecutiveCompletions = 0

        for (event in outcomes) {
            when (event.eventType) {
                EventType.SKIP_EARLY -> {
                    if (consecutiveCompletions > 0) break
                    consecutiveSkips++
                }
                EventType.PLAY_COMPLETE -> {
                    if (consecutiveSkips > 0) break
                    consecutiveCompletions++
                }
                else -> break
            }
        }

        return Momentum(consecutiveSkips, consecutiveCompletions)
    }

    /**
     * Translate momentum state into adjusted tier weights.
     *
     * Skip streak  → broaden pool (pull more from B and C, shuffle is feeling stale)
     * Complete streak → tighten pool (lean hard into A, user is in the zone)
     * Neutral → use config defaults (70 / 20 / 10)
     */
    private fun adjustedWeights(momentum: Momentum): Triple<Float, Float, Float> {
        return when {
            momentum.consecutiveSkips >= config.momentumSkipThreshold ->
                Triple(0.40f, 0.40f, 0.20f)
            momentum.consecutiveCompletions >= config.momentumCompleteThreshold ->
                Triple(0.85f, 0.12f, 0.03f)
            else ->
                Triple(config.tierAWeight, config.tierBWeight, config.tierCWeight)
        }
    }

    // ── Fatigue penalty (preserved from v1) ────────────────────────────────

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
}
