package com.airgesture.app.vision

import androidx.camera.view.transform.OutputTransform

data class Landmark3D(val x: Float, val y: Float, val z: Float)

data class HandLandmarks(
    val normalized: List<Landmark3D>,
    val world: List<Landmark3D>,
    val handedness: String,
    // Classification confidence is NOT palm detection or tracking confidence.
    val handednessScore: Float,
)

@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
data class TrackingFrame(
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val hand: HandLandmarks?,
    val transform: OutputTransform,
    val inferenceMs: Long,
    val processingMs: Long,
    val face:List<com.airgesture.app.eye.FacePoint>?=null,
    val source:com.airgesture.app.settings.VisionMode=com.airgesture.app.settings.VisionMode.HAND,
)
