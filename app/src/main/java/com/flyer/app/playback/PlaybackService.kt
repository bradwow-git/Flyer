package com.flyer.app.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.library.ListeningEventTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var tracker: ListeningEventTracker
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        tracker = ListeningEventTracker(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                    tracker.onTrackStopped(oldPosition.positionMs)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.localConfiguration?.uri?.toString() ?: return
                val filePath = android.net.Uri.parse(uri).path ?: return
                scope.launch {
                    val track = AppDatabase.getInstance(this@PlaybackService)
                        .trackDao()
                        .getTrackByPath(filePath)
                    track?.let {
                        tracker.onTrackStarted(
                            canonicalTrackId = it.canonicalTrackId,
                            durationMs = it.durationMs,
                            positionMs = 0L
                        )
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            if (player.currentMediaItem != null) {
                tracker.onTrackStopped(player.currentPosition)
            }
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}