package com.airgesture.app.cursor

/** Render only between measured positions. Never predict a target or manufacture gesture input. */
class CursorPresentation(private val durationMs:Long=60) {
    private var from:CursorPoint?=null
    private var target:CursorPoint?=null
    private var startedAt=0L
    private var blendMs=durationMs
    fun reset(){from=null;target=null;blendMs=durationMs}
    fun set(point:CursorPoint,now:Long,immediate:Boolean=false):CursorPoint {
        require(point.isFinite())
        val previous=sample(now)
        // Large moves must catch up promptly instead of adding the resting filter's
        // full delay. Small corrections retain the smoother 60 ms presentation.
        blendMs=if(previous!=null && kotlin.math.hypot(point.x-previous.x,point.y-previous.y)>.06f)
            minOf(durationMs,32L) else durationMs
        from=if(immediate || previous==null)point else previous
        target=point;startedAt=now
        return checkNotNull(from)
    }
    fun sample(now:Long):CursorPoint? {
        val end=target ?: return null;val start=from ?: return end
        val t=((now-startedAt).toFloat()/blendMs.coerceAtLeast(1)).coerceIn(0f,1f)
        return CursorPoint(start.x+(end.x-start.x)*t,start.y+(end.y-start.y)*t)
    }
    fun moving(now:Long)=target!=null && from!=target && now-startedAt<blendMs
}
