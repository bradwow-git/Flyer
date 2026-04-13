package com.flyer.app.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackStats
import com.flyer.app.data.repository.MusicRepository
import com.flyer.app.ui.models.TrackUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository(AppDatabase.getInstance(application))

    val uiState: StateFlow<InsightsUiState> = combine(
        repo.getAllStatsSortedByAffinity(),
        repo.getAllTracks()
    ) { allStats, allTracks ->

        if (allStats.isEmpty()) {
            return@combine InsightsUiState(isLoading = false)
        }

        val trackMap = allTracks.associateBy { it.canonicalTrackId }

        // ── Top tracks by affinity ──────────────────────────────────────────
        val topTracks = allStats
            .filter { it.totalPlays > 0 }
            .take(20)
            .mapNotNull { stats ->
                trackMap[stats.canonicalTrackId]?.let { track ->
                    TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
                }
            }

        // ── Most skipped ───────────────────────────────────────────────────
        val mostSkipped = allStats
            .filter { it.earlySkips > 0 }
            .sortedByDescending { it.earlySkips }
            .take(10)
            .mapNotNull { stats ->
                trackMap[stats.canonicalTrackId]?.let { track ->
                    TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
                }
            }

        // ── Most replayed ──────────────────────────────────────────────────
        val mostReplayed = allStats
            .filter { it.replays > 0 }
            .sortedByDescending { it.replays }
            .take(10)
            .mapNotNull { stats ->
                trackMap[stats.canonicalTrackId]?.let { track ->
                    TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
                        .let { model -> Pair(model, stats.replays) }
                }
            }
            .map { (model, _) -> model }

        // Keep replay counts for display
        val replayCountMap = allStats
            .filter { it.replays > 0 }
            .associate { it.canonicalTrackId to it.replays }

        // ── Top artists by total plays ─────────────────────────────────────
        val topArtists = allTracks
            .groupBy { it.artist }
            .map { (artist, artistTracks) ->
                val artistIds = artistTracks.map { it.canonicalTrackId }.toSet()
                val artistStats = allStats.filter { it.canonicalTrackId in artistIds && it.totalPlays > 0 }
                val totalPlays = artistStats.sumOf { it.totalPlays }
                val avgAffinity = if (artistStats.isNotEmpty())
                    artistStats.map { it.affinityScore }.average().toFloat()
                else 0f
                ArtistInsight(artist, totalPlays, avgAffinity)
            }
            .filter { it.totalPlays > 0 }
            .sortedByDescending { it.totalPlays }
            .take(10)

        // ── Library health ─────────────────────────────────────────────────
        val totalTracks = allTracks.size
        val trackedTracks = allStats.count { it.totalPlays > 0 }
        val avgAffinity = if (trackedTracks > 0)
            allStats.filter { it.totalPlays > 0 }.map { it.affinityScore }.average().toFloat()
        else 0f
        val lovedTracks = allStats.count { it.affinityScore > 30f }

        // ── Aggregate stats ────────────────────────────────────────────────
        val totalPlays   = allStats.sumOf { it.totalPlays }
        val totalSkips   = allStats.sumOf { it.earlySkips + it.midSkips + it.lateSkips }
        val totalComps   = allStats.sumOf { it.completions }
        val totalReplays = allStats.sumOf { it.replays }

        InsightsUiState(
            topTracks    = topTracks,
            mostSkipped  = mostSkipped,
            mostReplayed = mostReplayed,
            replayCountMap = replayCountMap,
            topArtists   = topArtists,
            libraryHealth = LibraryHealth(
                totalTracks   = totalTracks,
                trackedTracks = trackedTracks,
                avgAffinityScore = avgAffinity,
                lovedTracks   = lovedTracks
            ),
            stats = ListeningStats(
                totalPlays      = totalPlays,
                skipRate        = if (totalPlays > 0) totalSkips.toFloat() / totalPlays else 0f,
                completionRate  = if (totalPlays > 0) totalComps.toFloat() / totalPlays else 0f,
                totalReplays    = totalReplays
            ),
            patterns = generatePatterns(allStats, topArtists, totalReplays, trackedTracks, totalTracks),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InsightsUiState(isLoading = true)
    )
}

private fun generatePatterns(
    allStats: List<TrackStats>,
    topArtists: List<ArtistInsight>,
    totalReplays: Int,
    trackedTracks: Int,
    totalTracks: Int
): List<PatternInsight> {
    if (allStats.isEmpty()) return emptyList()

    val insights = mutableListOf<PatternInsight>()
    val totalPlays       = allStats.sumOf { it.totalPlays }
    val totalEarlySkips  = allStats.sumOf { it.earlySkips }
    val totalCompletions = allStats.sumOf { it.completions }

    // Skip behaviour
    if (totalPlays > 5) {
        val earlySkipRate = totalEarlySkips.toFloat() / totalPlays
        when {
            earlySkipRate > 0.5f ->
                insights.add(PatternInsight("You make fast calls — bailing early on over half your tracks"))
            earlySkipRate < 0.15f ->
                insights.add(PatternInsight("You give songs a real chance — low early skip rate"))
        }

        val completionRate = totalCompletions.toFloat() / totalPlays
        when {
            completionRate > 0.65f ->
                insights.add(PatternInsight("Committed listener — you finish most songs you start"))
            completionRate < 0.25f ->
                insights.add(PatternInsight("You treat your library like a radio dial — lots of skipping"))
        }
    }

    // Replay obsession
    if (totalReplays > 5) {
        insights.add(PatternInsight("You replay favourites — $totalReplays replays tracked so far"))
    }

    // Top artist shoutout
    if (topArtists.isNotEmpty()) {
        val top = topArtists.first()
        if (top.totalPlays > 3) {
            insights.add(PatternInsight("${top.artist} is leading your library with ${top.totalPlays} plays"))
        }
    }

    // Library exploration
    if (totalTracks > 10 && trackedTracks < totalTracks) {
        val unexplored = totalTracks - trackedTracks
        val pct = ((trackedTracks.toFloat() / totalTracks) * 100).toInt()
        when {
            pct < 30 ->
                insights.add(PatternInsight("Only $pct% of your library has been heard — lots to discover"))
            pct > 80 ->
                insights.add(PatternInsight("You've explored $pct% of your library — $unexplored tracks still unplayed"))
        }
    }

    // Top affinity track
    val topTrack = allStats.maxByOrNull { it.affinityScore }
    if (topTrack != null && topTrack.affinityScore > 15f) {
        insights.add(PatternInsight("One track is pulling ahead with an affinity of ${topTrack.affinityScore.toInt()} — it's probably stuck in your head"))
    }

    if (insights.isEmpty()) {
        insights.add(PatternInsight("Keep listening — patterns will emerge as Flyer learns your taste"))
    }

    return insights
}
