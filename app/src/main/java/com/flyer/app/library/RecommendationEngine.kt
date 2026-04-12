package com.flyer.app.library

import com.flyer.app.data.db.entities.TrackFile
import com.flyer.app.data.db.entities.TrackStats
import com.flyer.app.ui.models.TrackUiModel

class RecommendationEngine {

    private fun toUiModel(stats: TrackStats, trackMap: Map<Long, TrackFile>): TrackUiModel? {
        return trackMap[stats.canonicalTrackId]?.let { track ->
            TrackUiModel(stats.canonicalTrackId, track.title, track.artist, stats.affinityScore)
        }
    }

    // The single highest-affinity track — shown as the hero at the top
    fun heroTrack(stats: List<TrackStats>, trackMap: Map<Long, TrackFile>): TrackUiModel? {
        return stats
            .filter { it.totalPlays > 0 }
            .maxByOrNull { it.affinityScore }
            ?.let { toUiModel(it, trackMap) }
    }

    // Tracks you clearly love — high affinity, played and finished
    fun forYou(
        stats: List<TrackStats>,
        trackMap: Map<Long, TrackFile>,
        limit: Int = 20
    ): List<TrackUiModel> {
        return stats
            .filter { it.affinityScore > 10f && it.totalPlays > 0 }
            .sortedByDescending { it.affinityScore }
            .take(limit)
            .mapNotNull { toUiModel(it, trackMap) }
    }

    // Tracks with some plays and neutral-to-decent scores — worth more listens
    fun keepDigging(
        stats: List<TrackStats>,
        trackMap: Map<Long, TrackFile>,
        limit: Int = 20
    ): List<TrackUiModel> {
        return stats
            .filter { it.affinityScore in 0f..10f && it.totalPlays > 0 }
            .sortedByDescending { it.affinityScore }
            .take(limit)
            .mapNotNull { toUiModel(it, trackMap) }
    }

    // Tracks you own but have barely touched — low plays, not actively avoided
    fun deepCuts(
        stats: List<TrackStats>,
        trackMap: Map<Long, TrackFile>,
        limit: Int = 20
    ): List<TrackUiModel> {
        return stats
            .filter { it.totalPlays < 3 && it.affinityScore > -5f }
            .shuffled()
            .take(limit)
            .mapNotNull { toUiModel(it, trackMap) }
    }
}