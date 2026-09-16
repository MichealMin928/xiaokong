package com.airgesture.app.cursor

import com.airgesture.app.settings.CursorSettings
import com.airgesture.app.settings.SmoothingMethod
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/** Time-based filters in normalized screen space, independent of resolution or camera FPS.
 * One Euro: Casiez et al. CHI 2012, https://gery.casiez.net/1euro/ .
 * A higher cutoff follows fast motion; the lower resting cutoff reduces jitter.
 */
class CursorSmoother(private val settings: CursorSettings) {
    private var previousTime: Long? = null
    private var previousRaw: CursorPoint? = null
    private var filtered: CursorPoint? = null
    private var dx = 0.0
    private var dy = 0.0

    fun reset() { previousTime = null; previousRaw = null; filtered = null; dx = 0.0; dy = 0.0 }

    fun filter(point: CursorPoint, timestampMs: Long): CursorPoint {
        require(point.isFinite())
        val priorTime = previousTime
        val lastRaw = previousRaw
        val lastFiltered = filtered
        if (priorTime == null || lastRaw == null || lastFiltered == null || timestampMs - priorTime > 300) {
            reset()
            previousTime = timestampMs
            previousRaw = point
            filtered = point
            return point
        }
        // Reject duplicate/out-of-order samples without poisoning derivative or future timing.
        if (timestampMs <= priorTime) return lastFiltered
        val dt = (timestampMs - priorTime) / 1000.0
        val result = when (settings.method) {
            SmoothingMethod.NONE -> point
            SmoothingMethod.EMA -> {
                val alpha = 1.0 - exp(-dt / settings.smoothness.emaTau)
                CursorPoint(blend(lastFiltered.x, point.x, alpha), blend(lastFiltered.y, point.y, alpha))
            }
            SmoothingMethod.ONE_EURO -> {
                val derivativeAlpha = alpha(1.0, dt)
                dx += derivativeAlpha * ((point.x - lastRaw.x) / dt - dx)
                dy += derivativeAlpha * ((point.y - lastRaw.y) / dt - dy)
                val cutoffX = settings.smoothness.minCutoff + settings.smoothness.beta * abs(dx)
                val cutoffY = settings.smoothness.minCutoff + settings.smoothness.beta * abs(dy)
                CursorPoint(blend(lastFiltered.x, point.x, alpha(cutoffX, dt)),
                    blend(lastFiltered.y, point.y, alpha(cutoffY, dt)))
            }
        }
        previousTime = timestampMs
        previousRaw = point
        filtered = result
        return result
    }

    private fun alpha(cutoff: Double, dt: Double) = 1.0 / (1.0 + 1.0 / (2.0 * PI * cutoff * dt))
    private fun blend(previous: Float, current: Float, weight: Double) = (previous + weight * (current - previous)).toFloat()
}
