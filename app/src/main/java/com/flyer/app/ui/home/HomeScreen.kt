package com.flyer.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flyer.app.ui.models.TrackUiModel

@Composable
fun HomeScreen(
    onTrackClick: (Long) -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    if (state.isLoading) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                "Loading your library...",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val hasAnyData = state.heroTrack != null ||
            state.forYou.isNotEmpty() ||
            state.keepDigging.isNotEmpty() ||
            state.deepCuts.isNotEmpty()

    if (!hasAnyData) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                "Play some tracks to unlock your Home screen — Flyer needs a few listens to learn your taste.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Hero track ──────────────────────────────────────────────────────
        state.heroTrack?.let { hero ->
            item {
                HeroSection(track = hero, onTrackClick = onTrackClick)
            }
        }

        // ── For You Right Now ───────────────────────────────────────────────
        if (state.forYou.isNotEmpty()) {
            item {
                HomeSectionHeader(
                    title = "For You Right Now",
                    subtitle = "Your highest-rated tracks"
                )
            }
            item {
                TrackRow(tracks = state.forYou, onTrackClick = onTrackClick)
            }
        }

        // ── Keep Digging ────────────────────────────────────────────────────
        if (state.keepDigging.isNotEmpty()) {
            item {
                HomeSectionHeader(
                    title = "Keep Digging",
                    subtitle = "You've played these — worth more listens"
                )
            }
            item {
                TrackRow(tracks = state.keepDigging, onTrackClick = onTrackClick)
            }
        }

        // ── Deep Cuts ───────────────────────────────────────────────────────
        if (state.deepCuts.isNotEmpty()) {
            item {
                HomeSectionHeader(
                    title = "Deep Cuts",
                    subtitle = "Your library's unexplored territory"
                )
            }
            item {
                TrackRow(tracks = state.deepCuts, onTrackClick = onTrackClick)
            }
        }
    }
}

// ── Components ─────────────────────────────────────────────────────────────

@Composable
fun HeroSection(track: TrackUiModel, onTrackClick: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onTrackClick(track.canonicalTrackId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "⭐ Top Pick",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = "Affinity ${track.affinityScore.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun HomeSectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TrackRow(tracks: List<TrackUiModel>, onTrackClick: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks) { track ->
            TrackCard(track = track, onTrackClick = onTrackClick)
        }
    }
}

@Composable
fun TrackCard(track: TrackUiModel, onTrackClick: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onTrackClick(track.canonicalTrackId) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}