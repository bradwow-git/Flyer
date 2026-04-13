package com.flyer.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import com.flyer.app.ui.nowplaying.loadAlbumArt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.playback.PlaybackService
import com.flyer.app.ui.home.HomeScreen
import com.flyer.app.ui.home.HomeViewModel
import com.flyer.app.ui.home.PlayerViewModel
import com.flyer.app.ui.insights.InsightsScreen
import com.flyer.app.ui.insights.InsightsViewModel
import com.flyer.app.ui.nowplaying.NowPlayingScreen
import com.flyer.app.ui.theme.FlyerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { HOME, LIBRARY, INSIGHTS, NOW_PLAYING }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlyerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FlyerApp()
                }
            }
        }
    }
}

@Composable
fun FlyerApp(
    playerVm: PlayerViewModel = viewModel(),
    homeVm: HomeViewModel = viewModel(),
    insightsVm: InsightsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Permission ─────────────────────────────────────────────────────────

    var hasPermission by remember {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) playerVm.scanLibrary()
    }

    DisposableEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm)
        } else {
            playerVm.scanLibrary()
        }
        onDispose {}
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // ── Tracks (needed for playback lookup and Library screen) ─────────────

    val tracks by playerVm.tracks.collectAsState()

    // ── MediaController (lifecycle-bound, stays in UI layer) ───────────────

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<TrackFile?>(null) }
    var currentFeedbackType by remember { mutableStateOf<String?>(null) }

    // Reload feedback state whenever the playing track changes
    LaunchedEffect(currentTrack?.canonicalTrackId) {
        currentFeedbackType = currentTrack?.let { playerVm.getFeedbackType(it.canonicalTrackId) }
    }

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = future.get()
            isPlaying = controller?.isPlaying ?: false
        }, MoreExecutors.directExecutor())
        onDispose {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        }
    }

    DisposableEffect(controller, tracks) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uriString = mediaItem?.localConfiguration?.uri?.toString()
                currentTrack = tracks.find {
                    Uri.fromFile(File(it.filePath)).toString() == uriString
                }
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    // ── Screen routing ─────────────────────────────────────────────────────

    if (currentScreen == Screen.NOW_PLAYING && currentTrack != null) {
        NowPlayingScreen(
            track = currentTrack!!,
            isPlaying = isPlaying,
            player = controller,
            initialFeedbackType = currentFeedbackType,
            onBack = { currentScreen = Screen.HOME },
            onPlayPause = {
                if (controller?.isPlaying == true) controller?.pause()
                else controller?.play()
            },
            onNext = { controller?.seekToNextMediaItem() },
            onPrevious = { controller?.seekToPreviousMediaItem() },
            onLove = {
                playerVm.loveTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title)
                currentFeedbackType = "LOVE"
            },
            onRemoveFeedback = {
                playerVm.removeFeedback(currentTrack!!.canonicalTrackId, currentTrack!!.title)
                currentFeedbackType = null
            },
            onHide = {
                playerVm.hideTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title)
                currentFeedbackType = "HIDE"
            },
            onNeverPlay = {
                playerVm.neverPlayTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title)
                currentFeedbackType = "NEVER_PLAY"
            }
        )
    } else if (currentScreen == Screen.INSIGHTS) {
        InsightsScreen(onBack = { currentScreen = Screen.HOME }, vm = insightsVm)
    } else {

        // ── Main layout ────────────────────────────────────────────────────────

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flyer", style = MaterialTheme.typography.headlineMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { currentScreen = Screen.HOME },
                            enabled = currentScreen != Screen.HOME
                        ) { Text("Home") }
                        TextButton(
                            onClick = { currentScreen = Screen.LIBRARY },
                            enabled = currentScreen != Screen.LIBRARY
                        ) { Text("Library") }
                        TextButton(
                            onClick = { currentScreen = Screen.INSIGHTS }
                        ) { Text("Insights") }
                    }
                }

                // Smart Shuffle button — visible on both Home and Library
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = {
                        scope.launch {
                            val shuffled = playerVm.buildShuffleQueue()
                            if (shuffled.isNotEmpty()) {
                                val mediaItems = shuffled.map { track ->
                                    MediaItem.fromUri(Uri.fromFile(File(track.filePath)))
                                }
                                controller?.setMediaItems(mediaItems)
                                controller?.prepare()
                                controller?.play()
                            }
                        }
                    }) { Text("Smart Shuffle") }
                }

                // Screen content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (currentTrack != null) 120.dp else 0.dp)
                ) {
                    when (currentScreen) {
                        Screen.HOME -> HomeScreen(
                            onTrackClick = { canonicalTrackId ->
                                val track = tracks.find { it.canonicalTrackId == canonicalTrackId }
                                if (track != null) {
                                    val startIndex = tracks.indexOfFirst { it.canonicalTrackId == canonicalTrackId }
                                    val ordered = if (startIndex >= 0)
                                        tracks.drop(startIndex) + tracks.take(startIndex)
                                    else tracks
                                    val mediaItems = ordered.map { MediaItem.fromUri(Uri.fromFile(File(it.filePath))) }
                                    controller?.apply {
                                        setMediaItems(mediaItems)
                                        prepare()
                                        play()
                                    }
                                    currentTrack = track
                                }
                            },
                            vm = homeVm
                        )
                        Screen.LIBRARY -> LibraryScreen(
                            tracks = tracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onTrackClick = { track ->
                                val startIndex = tracks.indexOfFirst { it.filePath == track.filePath }
                                val ordered = if (startIndex >= 0)
                                    tracks.drop(startIndex) + tracks.take(startIndex)
                                else tracks
                                val mediaItems = ordered.map { MediaItem.fromUri(Uri.fromFile(File(it.filePath))) }
                                controller?.apply {
                                    setMediaItems(mediaItems)
                                    prepare()
                                    play()
                                }
                                currentTrack = track
                            }
                        )
                        Screen.INSIGHTS -> {} // handled by if/else above
                        Screen.NOW_PLAYING -> {} // handled by if/else above
                    }
                }
            }

            // Now Playing bar
            currentTrack?.let { track ->
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    NowPlayingBar(
                        track = track,
                        isPlaying = isPlaying,
                        feedbackType = currentFeedbackType,
                        onExpand = { currentScreen = Screen.NOW_PLAYING },
                        onPlayPause = {
                            if (controller?.isPlaying == true) controller?.pause()
                            else controller?.play()
                        },
                        onNext = { controller?.seekToNextMediaItem() },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onLove = {
                            playerVm.loveTrack(track.canonicalTrackId, track.title)
                            currentFeedbackType = "LOVE"
                        },
                        onHide = {
                            playerVm.hideTrack(track.canonicalTrackId, track.title)
                            controller?.seekToNextMediaItem()
                        }
                    )
                }
            }
        }
    } // end else (not INSIGHTS)
}

// ── Library screen (with search) ──────────────────────────────────────────

@Composable
fun LibraryScreen(
    tracks: List<TrackFile>,
    currentTrack: TrackFile?,
    isPlaying: Boolean,
    onTrackClick: (TrackFile) -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, tracks) {
        if (query.isBlank()) tracks
        else {
            val q = query.trim().lowercase()
            tracks.filter { track ->
                track.title.lowercase().contains(q) ||
                        track.artist.lowercase().contains(q) ||
                        track.album.lowercase().contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Search bar ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search songs, artists, albums…") },
            leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 4.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("✕") }
                }
            },
            singleLine = true
        )

        // ── Result count ───────────────────────────────────────────────────
        if (query.isNotBlank()) {
            Text(
                text = "${filtered.size} result${if (filtered.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            HorizontalDivider()
        }

        // ── Empty state ────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (query.isBlank()) "No tracks in library"
                        else "No results for \"$query\"",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (query.isNotBlank()) {
                        Text(
                            text = "Try a different song, artist, or album",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.filePath }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = currentTrack?.filePath == track.filePath && isPlaying,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}

// ── Shared composables ─────────────────────────────────────────────────────

@Composable
fun TrackRow(track: TrackFile, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isPlaying) {
            Text(
                text = "▶",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun NowPlayingBar(
    track: TrackFile,
    isPlaying: Boolean,
    feedbackType: String? = null,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLove: () -> Unit,
    onHide: () -> Unit
) {
    val isLoved = feedbackType == "LOVE"
    val loveColor = if (isLoved) androidx.compose.ui.graphics.Color(0xFFE91E63)
    else MaterialTheme.colorScheme.onSurfaceVariant
    var albumArt by remember(track.filePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(track.filePath) { albumArt = loadAlbumArt(track.filePath) }

    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArt != null) {
                        androidx.compose.foundation.Image(
                            painter = BitmapPainter(albumArt!!),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("♪", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)) {
                    Text(text = track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                TextButton(onClick = onPrevious) { Text("⏮") }
                TextButton(onClick = onPlayPause) { Text(if (isPlaying) "⏸" else "▶") }
                TextButton(onClick = onNext) { Text("⏭") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onLove) {
                    Text(
                        text = if (isLoved) "♥ Loved" else "♡ Love",
                        color = loveColor
                    )
                }
                TextButton(onClick = onHide) {
                    Text("✕ Hide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
