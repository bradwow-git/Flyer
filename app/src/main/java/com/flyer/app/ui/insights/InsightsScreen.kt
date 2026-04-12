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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flyer.app.ui.models.TrackUiModel

// ── UI Models ──────────────────────────────────────────────────────────────

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

// ── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    vm: InsightsViewModel
) {
    val uiState by vm.uiState.collectAsState()

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
            item { StatsCard(uiState.stats) }

            // ── Pattern insights ──
            if (uiState.patterns.isNotEmpty()) {
                item { SectionHeader("Patterns") }
                items(uiState.patterns) { insight -> PatternCard(insight) }
            }

            // ── Top tracks ──
            if (uiState.topTracks.isNotEmpty()) {
                item { SectionHeader("Top Tracks by Affinity") }
                items(uiState.topTracks) { track -> InsightsTrackRow(track, showScore = true) }
            }

            // ── Most skipped ──
            if (uiState.mostSkipped.isNotEmpty()) {
                item { SectionHeader("Most Skipped") }
                items(uiState.mostSkipped) { track -> InsightsTrackRow(track, showScore = false) }
            }

            // ── Empty state ──
            if (!uiState.isLoading && uiState.topTracks.isEmpty() && uiState.mostSkipped.isEmpty()) {
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
