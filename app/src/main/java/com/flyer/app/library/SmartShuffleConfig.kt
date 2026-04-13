package com.flyer.app.library

/**
 * All tunable parameters for SmartShuffleEngine v2.
 * Adjust these values to change how shuffle feels without touching selection logic.
 */
data class SmartShuffleConfig(

    // ── Pool ──────────────────────────────────────────────────────────────
    // How many eligible tracks to consider before tier assignment.
    // Larger = more variety. Smaller = tighter focus on top tracks.
    val candidatePoolSize: Int = 75,

    // ── Tiers (by ranked position in candidate pool) ───────────────────────
    // Tier A = top 20%, Tier B = next 40%, Tier C = remaining 40%
    val tierAPercent: Float = 0.20f,
    val tierBPercent: Float = 0.40f,

    // ── Tier pick weights (must sum to 1.0) ────────────────────────────────
    // Default: 70% Tier A, 20% Tier B, 10% Tier C
    val tierAWeight: Float = 0.70f,
    val tierBWeight: Float = 0.20f,
    val tierCWeight: Float = 0.10f,

    // ── Anti-repetition ────────────────────────────────────────────────────
    // Minimum number of other tracks between plays of the same artist.
    val artistSpacing: Int = 4,

    // Tracks played within this window are blocked from re-queuing.
    val trackCooldownMs: Long = 30 * 60 * 1000L,  // 30 minutes

    // ── Exclusion ──────────────────────────────────────────────────────────
    // Tracks with affinity score at or below this threshold are excluded.
    // NEVER_PLAY sets score to -1000f, so -100f is a safe cutoff.
    val neverPlayCutoff: Float = -100f,

    // ── Momentum ───────────────────────────────────────────────────────────
    // How many consecutive early skips triggers a broader pool selection.
    val momentumSkipThreshold: Int = 3,

    // How many consecutive completions triggers a tighter top-track focus.
    val momentumCompleteThreshold: Int = 3,

    // How many recent events to inspect when calculating momentum.
    val recentEventLookback: Int = 20
)
