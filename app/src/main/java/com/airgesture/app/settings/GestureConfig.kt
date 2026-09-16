package com.airgesture.app.settings

enum class VisionMode { HAND, FACE }

/** Initial hypotheses; tune against physical OPPO evidence before enabling any actions. */
data class GestureConfig(
    val analysisWidth: Int = 640,
    val analysisHeight: Int = 480,
    @Volatile var maxInferenceFps: Int = 25,
    val minDetectionConfidence: Float = 0.6f,
    val minPresenceConfidence: Float = 0.6f,
    val minTrackingConfidence: Float = 0.6f,
    val pinchPreviewRatio: Float = 0.35f,
    val useGpu: Boolean = false,
    val visionMode:VisionMode=VisionMode.HAND,
    val handFallback:Boolean=false,
)
