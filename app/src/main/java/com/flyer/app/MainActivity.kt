package com.flyer.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.UserFeedback
import com.flyer.app.library.AffinityCalculator
import com.flyer.app.library.FeedbackType
import com.flyer.app.library.MediaScanner
import com.flyer.app.library.SmartShuffleEngine
import com.flyer.app.playback.PlaybackService
import com.flyer.app.ui.theme.FlyerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch
import java.io.File

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
fun FlyerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    ) { granted -> hasPermission = granted }

    val tracks by AppDatabase.getInstance(context)
        .trackDao()
        .getAllTracks()
        .collectAsState(initial = emptyList())

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var controllerFuture by remember { mutableStateOf<ListenableFuture<MediaController>?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf<TrackFile?>(null) }
    val shuffleEngine = remember { SmartShuffleEngine(context) }

    // Request permission if needed
    DisposableEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(perm)
        }
        onDispose {}
    }

    // Scan library on startup
    DisposableEffect(hasPermission) {
        if (hasPermission) {
            scope.launch {
                try {
                    val scanner = MediaScanner(context)
                    scanner.scanDevice()
                } catch (e: Exception) {
                    Log.e("Flyer", "Scan failed", e)
                }
            }
        }
        onDispose {}
    }

    // Set up MediaController
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

    // Listen to playback state changes
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row with shuffle button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flyer",
                    style = MaterialTheme.typography.headlineMedium
                )
                Button(onClick = {
                    scope.launch {
                        try {
                            val shuffled = shuffleEngine.buildQueue()
                            if (shuffled.isNotEmpty()) {
                                val mediaItems = shuffled.map { track ->
                                    MediaItem.fromUri(Uri.fromFile(File(track.filePath)))
                                }
                                controller?.setMediaItems(mediaItems)
                                controller?.prepare()
                                controller?.play()
                            }
                        } catch (e: Exception) {
                            Log.e("Flyer", "Shuffle failed", e)
                        }
                    }
                }) {
                    Text("Smart Shuffle")
                }
            }

            // Track list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (currentTrack != null) 120.dp else 0.dp)
            ) {
                items(tracks) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = currentTrack?.filePath == track.filePath && isPlaying,
                        onClick = {
                            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(track.filePath)))
                            controller?.apply {
                                setMediaItem(mediaItem)
                                prepare()
                                play()
                            }
                            currentTrack = track
                        }
                    )
                }
            }
        }

        // Now Playing bar pinned to bottom
        currentTrack?.let { track ->
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                NowPlayingBar(
                    track = track,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (controller?.isPlaying == true) controller?.pause()
                        else controller?.play()
                    },
                    onNext = { controller?.seekToNextMediaItem() },
                    onPrevious = { controller?.seekToPreviousMediaItem() },
                    onLove = {
                        scope.launch {
                            try {
                                AppDatabase.getInstance(context).userFeedbackDao().insertOrReplace(
                                    UserFeedback(
                                        canonicalTrackId = track.canonicalTrackId,
                                        feedbackType = FeedbackType.LOVE
                                    )
                                )
                                AffinityCalculator(context).recalculateForTrack(track.canonicalTrackId)
                                Log.d("Flyer", "Loved: ${track.title}")
                            } catch (e: Exception) {
                                Log.e("Flyer", "Love failed", e)
                            }
                        }
                    },
                    onHide = {
                        scope.launch {
                            try {
                                AppDatabase.getInstance(context).userFeedbackDao().insertOrReplace(
                                    UserFeedback(
                                        canonicalTrackId = track.canonicalTrackId,
                                        feedbackType = FeedbackType.HIDE
                                    )
                                )
                                AffinityCalculator(context).recalculateForTrack(track.canonicalTrackId)
                                controller?.seekToNextMediaItem()
                                Log.d("Flyer", "Hidden: ${track.title}")
                            } catch (e: Exception) {
                                Log.e("Flyer", "Hide failed", e)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: TrackFile,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
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
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLove: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
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
