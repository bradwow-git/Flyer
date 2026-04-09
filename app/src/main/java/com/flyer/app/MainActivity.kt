package com.flyer.app

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.library.MediaScanner
import com.flyer.app.playback.PlaybackService
import com.flyer.app.ui.theme.FlyerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var isServiceConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlyerTheme {
                FlyerApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            { isServiceConnected = true },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        MediaController.releaseFuture(controllerFuture)
        super.onStop()
    }
}

@Composable
fun FlyerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var scanStatus by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            scope.launch {
                val count = MediaScanner(context).scanDevice()
                scanStatus = "Found $count tracks"
            }
        }
    }

    val tracks by AppDatabase.getInstance(context)
        .trackDao()
        .getAllTracks()
        .collectAsState(initial = emptyList())

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            scope.launch {
                val count = MediaScanner(context).scanDevice()
                scanStatus = "Found $count tracks"
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "Flyer",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp)
            )

            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Flyer needs access to your music files.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(permission) }) {
                        Text("Grant Access")
                    }
                }
            } else if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (scanStatus.isEmpty()) "Scanning..." else scanStatus)
                }
            } else {
                Text(
                    text = scanStatus,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LazyColumn {
                    items(tracks) { track ->
                        TrackRow(track = track)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackRow(track: TrackFile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = track.title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}