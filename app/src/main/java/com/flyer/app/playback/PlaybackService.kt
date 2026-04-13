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
import com.flyer.app.library.AffinityCalculator

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var tracker: ListeningEventTracker
    private lateinit var calculator: AffinityCalculator
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        tracker = ListeningEventTracker(this)
        calculator = AffinityCalculator(this)

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
                    val trackId = tracker.currentCanonicalTrackId
                    tracker.onTrackStopped(oldPosition.positionMs)
                    if (trackId != -1L) {
                        scope.launch { calculator.recalculateForTrack(trackId) }
                    }
                }
            }

            // Handles the case where the last track in the queue plays to completion.
            // onPositionDiscontinuity only fires on item transitions, not on STATE_ENDED.
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val trackId = tracker.currentCanonicalTrackId
                    if (trackId != -1L) {
                        tracker.onTrackStopped(player.duration.coerceAtLeast(0L))
                        scope.launch { calculator.recalculateForTrack(trackId) }
                    }
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
            val trackId = tracker.currentCanonicalTrackId
            if (player.currentMediaItem != null && trackId != -1L) {
                tracker.onTrackStopped(player.currentPosition)
                scope.launch { calculator.recalculateForTrack(trackId) }
            }
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}