package com.airgesture.app.cursor

import kotlin.math.hypot

/** Optional relative gain: slow motion is precise, fast motion crosses more distance.
 * Camera-region edges remain absolute anchors so every screen edge remains reachable. */
class CursorMotionCurve {
    private var raw:CursorPoint?=null
    private var output:CursorPoint?=null
    private var time=0L
    fun reset() {raw=null;output=null;time=0}
    fun apply(point:CursorPoint,timestamp:Long):CursorPoint {
        val previous=raw;val old=output
        if(previous==null || old==null || timestamp-time>300) {
            raw=point;output=point;time=timestamp;return point
        }
        if(timestamp<=time) return old
        val dt=(timestamp-time)/1000f
        val dx=point.x-previous.x;val dy=point.y-previous.y
        val speed=hypot(dx,dy)/dt
        val gain=.55f+((speed-.08f)/.72f).coerceIn(0f,1f)*1.05f
        fun axis(value:Float,oldValue:Float,delta:Float)=when {
            value<=0f -> 0f
            value>=1f -> 1f
            else -> (oldValue+delta*gain).coerceIn(0f,1f)
        }
        val next=CursorPoint(axis(point.x,old.x,dx),axis(point.y,old.y,dy))
        raw=point;output=next;time=timestamp
        return next
    }
}
