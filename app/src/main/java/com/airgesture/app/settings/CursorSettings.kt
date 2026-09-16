package com.airgesture.app.settings

import com.airgesture.app.cursor.ControlRegion

// Keep the fingertip above center so there is room below it for the rest of the hand.
enum class Sensitivity(val label: String, val rangeX: Float, val rangeY: Float) {
    LOW("低", .48f, .40f), MEDIUM("中", .36f, .30f), HIGH("高", .28f, .24f)
}
enum class Smoothness(val label: String, val minCutoff: Double, val beta: Double, val emaTau: Double) {
    LOW("低", 1.6, 10.0, .07), MEDIUM("中", 1.0, 8.0, .12), HIGH("高", .65, 6.0, .18)
}
enum class SmoothingMethod(val label: String) { ONE_EURO("One Euro"), EMA("EMA"), NONE("关闭") }
data class CursorSettings(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val smoothness: Smoothness = Smoothness.MEDIUM,
    val method: SmoothingMethod = SmoothingMethod.ONE_EURO,
    val acceleration: Boolean = false,
    val portraitRegion: ControlRegion? = null,
    val landscapeRegion: ControlRegion? = null,
)
