package com.airgesture.app.settings

/** Geometry checks are conservative heuristics, not a model visibility/confidence score. */
data class TrackingConfig(
    val exitMargin: Float = .015f,
    val reentryMargin: Float = .045f,
    val maxFrameAgeMs: Long = 250,
    val maxGapMs: Long = 250,
    val acquireDurationMs: Long = 180,
    val acquireFrames: Int = 3,
    val acquireMovement: Float = .07f,
    val suddenJump: Float = .24f,
    val jumpAllowancePerSecond: Float = .35f,
    val minPalmPixels: Float = 12f,
)
