package com.flyer.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flyer.app.data.db.AppDatabase
import com.flyer.app.data.repository.MusicRepository
import com.flyer.app.library.RecommendationEngine
import com.flyer.app.ui.models.TrackUiModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val heroTrack: TrackUiModel? = null,
    val forYou: List<TrackUiModel> = emptyList(),
    val keepDigging: List<TrackUiModel> = emptyList(),
    val deepCuts: List<TrackUiModel> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MusicRepository(AppDatabase.getInstance(application))
    private val recommender = RecommendationEngine()

    val uiState: StateFlow<HomeUiState> = combine(
        repo.getAllStatsSortedByAffinity(),
        repo.getAllTracks()
    ) { allStats, allTracks ->
        val trackMap = allTracks.associateBy { it.canonicalTrackId }

        HomeUiState(
            heroTrack = recommender.heroTrack(allStats, trackMap),
            forYou = recommender.forYou(allStats, trackMap),
            keepDigging = recommender.keepDigging(allStats, trackMap),
            deepCuts = recommender.deepCuts(allStats, trackMap),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )
}