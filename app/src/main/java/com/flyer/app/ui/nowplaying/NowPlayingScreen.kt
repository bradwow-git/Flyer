package com.flyer.app.ui.nowplaying

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.flyer.app.data.db.entities.TrackFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Love color — warm pink that reads clearly in both light and dark themes
private val LoveColor = Color(0xFFE91E63)

@Composable
fun NowPlayingScreen(
    track: TrackFile,
    isPlaying: Boolean,
    player: Player?,
    initialFeedbackType: String? = null,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLove: () -> Unit,
    onRemoveFeedback: () -> Unit,
    onHide: () -> Unit,
    onNeverPlay: () -> Unit
) {
    // ── Album art ──────────────────────────────────────────────────────────
    var albumArt by remember(track.filePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(track.filePath) {
        albumArt = loadAlbumArt(track.filePath)
    }

    // ── Seek position tracking ─────────────────────────────────────────────
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            if (!isSeeking && player != null) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // ── Feedback state ────────────────────────────────────────────────────
    // Local mirror of DB state so UI responds instantly on tap
    var feedbackType by remember(track.canonicalTrackId, initialFeedbackType) {
        mutableStateOf(initialFeedbackType)
    }
    val isLoved = feedbackType == "LOVE"

    // ── Never Play dialog ─────────────────────────────────────────────────
    var showNeverPlayDialog by remember { mutableStateOf(false) }
    if (showNeverPlayDialog) {
        AlertDialog(
            onDismissRequest = { showNeverPlayDialog = false },
            title = { Text("Ban this track?") },
            text = {
                Text("\"${track.title}\" will never appear in your shuffles again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNeverPlay()
                        onNext()
                        showNeverPlayDialog = false
                    }
                ) {
                    Text("Ban it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNeverPlayDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Top bar ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("↓ Close") }
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {}, enabled = false) {
                    Text("", color = MaterialTheme.colorScheme.surface)
                }
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(32.dp))

            // ── Artwork ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(albumArt!!),
                        contentDescription = "Album art for ${track.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Track info ─────────────────────────────────────────────────
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Seek bar ───────────────────────────────────────────────────
            val sliderValue = if (isSeeking) seekValue
            else if (duration > 0) position.toFloat() / duration.toFloat()
            else 0f

            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    seekValue = value
                },
                onValueChangeFinished = {
                    if (duration > 0) player?.seekTo((seekValue * duration).toLong())
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMillis(if (isSeeking && duration > 0) (seekValue * duration).toLong() else position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMillis(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Playback controls ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Text("⏮", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = onPlayPause) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.displaySmall
                    )
                }
                IconButton(onClick = onNext) {
                    Text("⏭", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Feedback row ───────────────────────────────────────────────
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Love / Un-love toggle
                FeedbackButton(
                    icon = if (isLoved) "♥" else "♡",
                    label = if (isLoved) "Loved" else "Love",
                    tint = if (isLoved) LoveColor else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        if (isLoved) {
                            onRemoveFeedback()
                            feedbackType = null
                        } else {
                            onLove()
                            feedbackType = "LOVE"
                        }
                    }
                )

                // Hide — muted, advances to next track
                FeedbackButton(
                    icon = "✕",
                    label = "Hide",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = {
                        onHide()
                        onNext()
                    }
                )

                // Never Play — shows confirmation dialog first
                FeedbackButton(
                    icon = "⊘",
                    label = "Never",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { showNeverPlayDialog = true }
                )
            }
        }
    }
}

// ── Feedback button ────────────────────────────────────────────────────────

@Composable
private fun FeedbackButton(
    icon: String,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium,
                color = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.75f)
            )
        }
    }
}

// ── Album art loader ───────────────────────────────────────────────────────

suspend fun loadAlbumArt(filePath: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(filePath)
        val bytes = retriever.embeddedPicture ?: return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

// ── Time formatter ─────────────────────────────────────────────────────────

private fun formatMillis(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
