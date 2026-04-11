package com.flyer.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.TrackStats

// ── UI Models ──────────────────────────────────────────────────────────────

data class TrackUiModel(
    val canonicalTrackId: Long,
    val title: String,
    val artist: String,
    val affinityScore: Float
)

data class ListeningStats(
    val totalPlays: Int,
    val skipRate: Float,
    val completionRate: Float
)

data class PatternInsight(
    val description: String
)

data class InsightsUiState(
    val topTracks: List<TrackUiModel> = emptyList(),
    val mostSkipped: List<TrackUiModel> = emptyList(),
    val stats: ListeningStats = ListeningStats(0, 0f, 0f),
    val patterns: List<PatternInsight> = emptyList(),
    val isLoading: Boolean = true
)

// ── Pattern generation ─────────────────────────────────────────────────────

private fun generatePatterns(allStats: List<TrackStats>): List<PatternInsight> {
    if (allStats.isEmpty()) return emptyList()

    val insights = mutableListOf<PatternInsight>()

    val totalPlays = allStats.sumOf { it.totalPlays }
    val totalEarlySkips = allStats.sumOf { it.earlySkips }
    val totalCompletions = allStats.sumOf { it.completions }
    val totalReplays = allStats.sumOf { it.replays }

    if (totalPlays > 5) {
        val earlySkipRate = totalEarlySkips.toFloat() / totalPlays
        when {
            earlySkipRate > 0.5f ->
                insights.add(PatternInsight("You make fast calls — you bail early on over half your tracks"))
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

    if (totalReplays > 5) {
        insights.add(PatternInsight("You replay favorites — $totalReplays replays tracked so far"))
    }

    val topScoringTrack = allStats.maxByOrNull { it.affinityScore }
    if (topScoringTrack != null && topScoringTrack.affinityScore > 15f) {
        insights.add(PatternInsight("You have a clear top track (affinity ${topScoringTrack.affinityScore.toInt()}) — it's probably stuck in your head"))
    }

    if (insights.isEmpty()) {
        insights.add(PatternInsight("Keep listening — patterns will emerge as Flyer learns your taste"))
    }

    return insights
}

// ── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // Live stats flow — auto-updates as you listen
    val allStats by AppDatabase.getInstance(context)
        .trackStatsDao()
        .getAllStatsSortedByAffinity()
        .collectAsState(initial = emptyList())

    // Track files loaded once for title/artist lookup
    var trackMap by remember { mutableStateOf<Map<Long, TrackFile>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val tracks = AppDatabase.getInstance(context).trackDao().getAllTracksOnce()
        trackMap = tracks.associateBy { it.canonicalTrackId }
    }

    // Derive UI state from joined data
    val uiState = remember(allStats, trackMap) {
        if (trackMap.isEmpty()) return@remember InsightsUiState(isLoading = true)

        val topTracks = allStats
            .filter { it.totalPlays > 0 }
            .take(20)
            .mapNotNull { stats ->
                trackMap[stats.canonicalTrackId]?.let { track ->
                    TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
                }
            }

        val mostSkipped = allStats
            .filter { it.earlySkips > 0 }
            .sortedByDescending { it.earlySkips }
            .take(10)
            .mapNotNull { stats ->
                trackMap[stats.canonicalTrackId]?.let { track ->
                    TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
                }
            }

        val totalPlays = allStats.sumOf { it.totalPlays }
        val totalSkips = allStats.sumOf { it.earlySkips + it.midSkips + it.lateSkips }
        val totalCompletions = allStats.sumOf { it.completions }

        InsightsUiState(
            topTracks = topTracks,
            mostSkipped = mostSkipped,
            stats = ListeningStats(
                totalPlays = totalPlays,
                skipRate = if (totalPlays > 0) totalSkips.toFloat() / totalPlays else 0f,
                completionRate = if (totalPlays > 0) totalCompletions.toFloat() / totalPlays else 0f
            ),
            patterns = generatePatterns(allStats),
            isLoading = false
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn {

            // ── Header ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Insights", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = onBack) { Text("← Back") }
                }
                HorizontalDivider()
            }

            if (uiState.isLoading) {
                item {
                    Text(
                        "Loading your listening data...",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@LazyColumn
            }

            // ── Stats overview ──
            item { SectionHeader("Your Listening Stats") }
            item {
                StatsCard(uiState.stats)
            }

            // ── Pattern insights ──
            if (uiState.patterns.isNotEmpty()) {
                item { SectionHeader("Patterns") }
                items(uiState.patterns) { insight ->
                    PatternCard(insight)
                }
            }

            // ── Top tracks ──
            if (uiState.topTracks.isNotEmpty()) {
                item { SectionHeader("Top Tracks by Affinity") }
                items(uiState.topTracks) { track ->
                    InsightsTrackRow(track, showScore = true)
                }
            }

            // ── Most skipped ──
            if (uiState.mostSkipped.isNotEmpty()) {
                item { SectionHeader("Most Skipped") }
                items(uiState.mostSkipped) { track ->
                    InsightsTrackRow(track, showScore = false)
                }
            }

            // Empty state
            if (uiState.topTracks.isEmpty() && uiState.mostSkipped.isEmpty()) {
                item {
                    Text(
                        "No listening data yet — play some tracks and come back!",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Reusable components ────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun StatsCard(stats: ListeningStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "Total Plays", value = stats.totalPlays.toString())
            StatItem(label = "Skip Rate", value = "${(stats.skipRate * 100).toInt()}%")
            StatItem(label = "Completion", value = "${(stats.completionRate * 100).toInt()}%")
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PatternCard(insight: PatternInsight) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = insight.description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun InsightsTrackRow(track: TrackUiModel, showScore: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (showScore) {
            Text(
                text = "${track.affinityScore.toInt()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}