package com.flyer.app.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.UserFeedback
import com.flyer.app.data.repository.MusicRepository
import com.flyer.app.library.AffinityCalculator
import com.flyer.app.library.FeedbackType
import com.flyer.app.library.MediaScanner
import com.flyer.app.library.SmartShuffleEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository(AppDatabase.getInstance(application))
    private val shuffleEngine = SmartShuffleEngine(application)
    private val affinityCalculator = AffinityCalculator(application)

    // ── Tracks ─────────────────────────────────────────────────────────────

    val tracks: StateFlow<List<TrackFile>> = repo.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Library scan ───────────────────────────────────────────────────────

    fun scanLibrary() {
        viewModelScope.launch {
            try {
                MediaScanner(getApplication()).scanDevice()
            } catch (e: Exception) {
                Log.e("Flyer", "Scan failed", e)
            }
        }
    }

    // ── Shuffle ────────────────────────────────────────────────────────────

    suspend fun buildShuffleQueue(): List<TrackFile> {
        return try {
            shuffleEngine.buildQueue()
        } catch (e: Exception) {
            Log.e("Flyer", "Shuffle failed", e)
            emptyList()
        }
    }

    // ── Feedback ───────────────────────────────────────────────────────────

    fun loveTrack(canonicalTrackId: Long, title: String) {
        viewModelScope.launch {
            try {
                repo.insertOrReplaceFeedback(
                    UserFeedback(
                        canonicalTrackId = canonicalTrackId,
                        feedbackType = FeedbackType.LOVE
                    )
                )
                affinityCalculator.recalculateForTrack(canonicalTrackId)
                Log.d("Flyer", "Loved: $title")
            } catch (e: Exception) {
                Log.e("Flyer", "Love failed", e)
            }
        }
    }

    fun hideTrack(canonicalTrackId: Long, title: String) {
        viewModelScope.launch {
            try {
                repo.insertOrReplaceFeedback(
                    UserFeedback(
                        canonicalTrackId = canonicalTrackId,
                        feedbackType = FeedbackType.HIDE
                    )
                )
                affinityCalculator.recalculateForTrack(canonicalTrackId)
                Log.d("Flyer", "Hidden: $title")
            } catch (e: Exception) {
                Log.e("Flyer", "Hide failed", e)
            }
        }
    }
}