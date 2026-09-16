package com.airgesture.app.vision

import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.TrackingConfig
import kotlin.math.hypot

enum class TrackingState(val message: String) {
    MISSING("请把整只手放入画面"),
    EDGE("手靠近画面边缘，请移回活动区"),
    INVALID("手部形状不稳定，请把整只手放入画面"),
    ACQUIRING("已看到手，请稳住片刻"),
    UNSTABLE("位置突变已忽略，请稳住片刻"),
    STALE("识别暂时中断，光标已暂停"),
    TRACKING("光标跟随中 · 在活动区内轻轻移动食指"),
}

/** A model can return 21 inferred points for a cropped hand. Gate all points, not just the tip.
 * The guard accepts no system actions; later gesture state machines must also obey its decision.
 */
class HandTrackingGate(private val config: TrackingConfig = TrackingConfig()) {
    var state = TrackingState.MISSING
        private set
    private var lastTimestamp: Long? = null
    private var lastTip: CursorPoint? = null
    private var acquiredAt = 0L
    private var acquiringFrames = 0
    private var acquisitionAnchor: CursorPoint? = null

    fun reset(reason: TrackingState = TrackingState.MISSING) {
        state = reason
        lastTimestamp = null
        lastTip = null
        acquisitionAnchor = null
        acquiringFrames = 0
        acquiredAt = 0
    }

    fun update(points: List<CursorPoint>?, timestampMs: Long, nowMs: Long, width: Int, height: Int): CursorPoint? {
        if (nowMs - timestampMs !in 0..config.maxFrameAgeMs) return reject(TrackingState.STALE)
        if (points == null) return reject(TrackingState.MISSING)
        if (points.size != 21 || points.any { !it.isFinite() } || width <= 0 || height <= 0) return reject(TrackingState.INVALID)
        val priorTime = lastTimestamp
        if (priorTime != null && timestampMs <= priorTime) return reject(TrackingState.STALE)
        if (priorTime != null && timestampMs - priorTime > config.maxGapMs) reset(TrackingState.STALE)
        val margin = if (state == TrackingState.TRACKING) config.exitMargin else config.reentryMargin
        if (points.any { it.x < margin || it.x > 1f - margin || it.y < margin || it.y > 1f - margin }) {
            return reject(TrackingState.EDGE)
        }
        fun pixels(a: Int, b: Int) = hypot((points[a].x - points[b].x) * width, (points[a].y - points[b].y) * height)
        if (pixels(0, 9) < config.minPalmPixels || pixels(5, 17) < config.minPalmPixels) return reject(TrackingState.INVALID)
        val tip = points[8]
        val previous = lastTip
        if (state == TrackingState.TRACKING && previous != null && priorTime != null) {
            val dt = (timestampMs - priorTime) / 1000f
            if (distance(tip, previous) > config.suddenJump + config.jumpAllowancePerSecond * dt) {
                reset(TrackingState.UNSTABLE)
                beginAcquiring(tip, timestampMs)
                return null
            }
        }
        if (state != TrackingState.TRACKING) {
            val anchor = acquisitionAnchor
            if (anchor == null || distance(tip, anchor) > config.acquireMovement) {
                beginAcquiring(tip, timestampMs)
            } else {
                acquiringFrames++
            }
            state = if (acquiringFrames >= config.acquireFrames && timestampMs - acquiredAt >= config.acquireDurationMs)
                TrackingState.TRACKING else TrackingState.ACQUIRING
        }
        lastTimestamp = timestampMs
        lastTip = tip
        return tip.takeIf { state == TrackingState.TRACKING }
    }

    private fun beginAcquiring(tip: CursorPoint, timestamp: Long) {
        acquisitionAnchor = tip
        acquiredAt = timestamp
        acquiringFrames = 1
        lastTimestamp = timestamp
        lastTip = tip
    }
    private fun distance(a: CursorPoint, b: CursorPoint) = hypot(a.x - b.x, a.y - b.y)
    private fun reject(reason: TrackingState): CursorPoint? { reset(reason); return null }
}
