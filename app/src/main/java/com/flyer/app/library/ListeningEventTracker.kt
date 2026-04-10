package com.flyer.app.library

import android.content.Context
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.ListeningEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object EventType {
    const val PLAY_START = "PLAY_START"
    const val PLAY_COMPLETE = "PLAY_COMPLETE"
    const val SKIP_EARLY = "SKIP_EARLY"
    const val SKIP_MID = "SKIP_MID"
    const val SKIP_LATE = "SKIP_LATE"
    const val REPLAY = "REPLAY"
}

class ListeningEventTracker(context: Context) {

    private val dao = AppDatabase.getInstance(context).listeningEventDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentCanonicalTrackId: Long = -1L
    private var currentDurationMs: Long = 0L
    private var playStartPositionMs: Long = 0L
    private var lastTrackedTrackId: Long = -1L

    fun onTrackStarted(canonicalTrackId: Long, durationMs: Long, positionMs: Long) {
        currentCanonicalTrackId = canonicalTrackId
        currentDurationMs = durationMs
        playStartPositionMs = positionMs
        record(canonicalTrackId, EventType.PLAY_START, positionMs, durationMs)
    }

    fun onTrackStopped(positionMs: Long) {
        if (currentCanonicalTrackId == -1L || currentDurationMs == 0L) return

        val percentReached = positionMs.toFloat() / currentDurationMs.toFloat()

        val eventType = when {
            percentReached >= 0.85f -> EventType.PLAY_COMPLETE
            percentReached < 0.20f -> EventType.SKIP_EARLY
            percentReached < 0.60f -> EventType.SKIP_MID
            else -> EventType.SKIP_LATE
        }

        record(currentCanonicalTrackId, eventType, positionMs, currentDurationMs)

        if (eventType == EventType.PLAY_COMPLETE &&
            currentCanonicalTrackId == lastTrackedTrackId) {
            record(currentCanonicalTrackId, EventType.REPLAY, positionMs, currentDurationMs)
        }

        lastTrackedTrackId = currentCanonicalTrackId
        currentCanonicalTrackId = -1L
    }

    private fun record(
        canonicalTrackId: Long,
        eventType: String,
        positionMs: Long,
        durationMs: Long
    ) {
        scope.launch {
            dao.insert(
                ListeningEvent(
                    canonicalTrackId = canonicalTrackId,
                    eventType = eventType,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
        }
    }
}