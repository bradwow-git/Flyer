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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
            onBack = { currentScreen = Screen.HOME },
            onPlayPause = {
                if (controller?.isPlaying == true) controller?.pause()
                else controller?.play()
            },
            onNext = { controller?.seekToNextMediaItem() },
            onPrevious = { controller?.seekToPreviousMediaItem() },
            onLove = { playerVm.loveTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title) },
            onHide = {
                playerVm.hideTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title)
            },
            onNeverPlay = {
                playerVm.neverPlayTrack(currentTrack!!.canonicalTrackId, currentTrack!!.title)
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
                                    val mediaItem = MediaItem.fromUri(Uri.fromFile(File(track.filePath)))
                                    controller?.apply {
                                        setMediaItem(mediaItem)
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
                                val mediaItem = MediaItem.fromUri(Uri.fromFile(File(track.filePath)))
                                controller?.apply {
                                    setMediaItem(mediaItem)
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
                        onExpand = { currentScreen = Screen.NOW_PLAYING },
                        onPlayPause = {
                            if (controller?.isPlaying == true) controller?.pause()
                            else controller?.play()
                        },
                        onNext = { controller?.seekToNextMediaItem() },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onLove = { playerVm.loveTrack(track.canonicalTrackId, track.title) },
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

// ── Library screen (flat track list) ──────────────────────────────────────

@Composable
fun LibraryScreen(
    tracks: List<TrackFile>,
    currentTrack: TrackFile?,
    isPlaying: Boolean,
    onTrackClick: (TrackFile) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks) { track ->
            TrackRow(
                track = track,
                isPlaying = currentTrack?.filePath == track.filePath && isPlaying,
                onClick = { onTrackClick(track) }
            )
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
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLove: () -> Unit,
    onHide: () -> Unit
) {
    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
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
                TextButton(onClick = onLove) { Text("♥ Love") }
                TextButton(onClick = onHide) { Text("✕ Hide") }
            }
        }
    }
}
