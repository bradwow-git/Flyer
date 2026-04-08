package com.flyer.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flyer.app.playback.PlaybackService
import com.flyer.app.ui.theme.FlyerTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var isServiceConnected by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlyerTheme {
                FlyerHomeScreen(isServiceConnected = isServiceConnected)
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
fun FlyerHomeScreen(isServiceConnected: Boolean) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Flyer",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isServiceConnected) "Engine ready." else "Starting engine...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}