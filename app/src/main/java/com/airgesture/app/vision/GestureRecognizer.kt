package com.airgesture.app.vision

import com.airgesture.app.settings.GestureConfig
import kotlin.math.hypot

data class Posture(val name: String, val pinchRatio: Float? = null)

/** Diagnostic posture hints only. This class never triggers clicks or system gestures. */
class GestureRecognizer(private val config: GestureConfig) {
    fun describe(frame: TrackingFrame): Posture {
        val p = frame.hand?.normalized ?: return Posture("NO_HAND")
        if (p.size != 21) return Posture("UNKNOWN")
        fun distance(a: Int, b: Int) = hypot(
            (p[a].x - p[b].x) * frame.width,
            (p[a].y - p[b].y) * frame.height,
        )
        val palm = distance(0, 9)
        if (palm < 8f) return Posture("TOO_SMALL")
        val pinch = distance(4, 8) / palm
        val extended = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18)
            .map { (tip, pip) -> distance(tip, 0) > distance(pip, 0) * 1.15f }
        val name = when {
            extended.none { it } -> "FIST_CANDIDATE"
            pinch < config.pinchPreviewRatio -> "PINCH_CANDIDATE"
            extended.all { it } -> "OPEN_PALM"
            extended[0] && extended.drop(1).none { it } -> "POINTING"
            else -> "HAND"
        }
        return Posture(name, pinch)
    }
}
