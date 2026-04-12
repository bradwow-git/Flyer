package com.flyer.app.ui.models

data class TrackUiModel(
    val canonicalTrackId: Long,
    val title: String,
    val artist: String,
    val affinityScore: Float
)