package com.airgesture.app.cursor

import com.airgesture.app.settings.CursorSettings

/** Pure controller, independent of Activity, CameraX, and Accessibility. */
class CursorController(initialSettings: CursorSettings = CursorSettings()) {
    private val mapper = CoordinateMapper()
    var settings = initialSettings
        private set
    private var smoother = CursorSmoother(settings)
    private val motionCurve = CursorMotionCurve()
    private var landscape = false
    val region: ControlRegion get() {
        (if(landscape) settings.landscapeRegion else settings.portraitRegion)?.let {return it}
        val halfX = settings.sensitivity.rangeX / 2f
        val halfY = settings.sensitivity.rangeY / 2f
        return ControlRegion(.5f - halfX, .5f + halfX, .4f - halfY, .4f + halfY)
    }

    fun configure(updated: CursorSettings) {
        settings = updated
        smoother = CursorSmoother(updated)
        motionCurve.reset()
    }
    fun setLandscape(value:Boolean) {if(landscape!=value){landscape=value;reset()}}

    fun update(rawIndex: CursorPoint?, timestampMs: Long, nowMs: Long): CursorPoint? {
        if (rawIndex == null || nowMs - timestampMs !in 0..300) { reset(); return null }
        val mapped = mapper.normalize(rawIndex, region) ?: run { reset(); return null }
        return smoother.filter(if(settings.acceleration) motionCurve.apply(mapped,timestampMs) else mapped, timestampMs)
    }

    fun reset() {smoother.reset();motionCurve.reset()}
}
