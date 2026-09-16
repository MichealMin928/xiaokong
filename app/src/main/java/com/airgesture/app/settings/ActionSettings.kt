package com.airgesture.app.settings

data class ActionSettings(
    val pinch: Boolean = true,
    val swipeUp: Boolean = true,
    val swipeDown: Boolean = true,
    val fistPause: Boolean = true,
    val swipeLeftBack: Boolean = false,
    val swipeRightHome: Boolean = false,
    val pushExperimental: Boolean = false,
    val debug: Boolean = true,
    val drag: Boolean = false,
    val doublePinch: Boolean = false,
    val palmFlip: Boolean = false,
    val sceneSwipes: Boolean = false,
)

/** All timing/geometry thresholds live here, not inside Android event handlers. */
data class ActionConfig(
    val maxGapMs: Long = 250,
    val neutralMs: Long = 180,
    val pinchClose: Float = .30f,
    val pinchRelease: Float = .50f,
    val pinchHoldMs: Long = 90,
    val pinchReleaseMs: Long = 110,
    val pinchMaxDrift: Float = .055f,
    val clickCooldownMs: Long = 450,
    val swipeWindowMs: Long = 350,
    val swipeRestMs: Long = 160,
    val swipeDistance: Float = .17f,
    val swipeMinSpeed: Float = .65f,
    val swipeAxisRatio: Float = 1.7f,
    val swipeCooldownMs: Long = 650,
    val fistHoldMs: Long = 500,
    val fistMaxDrift: Float = .08f,
)
