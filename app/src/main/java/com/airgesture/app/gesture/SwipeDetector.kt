package com.airgesture.app.gesture

import com.airgesture.app.settings.ActionConfig
import kotlin.math.abs

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }
data class SwipeMotion(val direction: SwipeDirection, val velocity: Float)

/** Requires a stationary open palm before every stroke. Continued travel cannot rearm it. */
class SwipeDetector(private val config: ActionConfig = ActionConfig(),private val scene:Boolean=false) {
    private var rest: HandObservation? = null
    private var armed = false
    private var poseLostAt:Long?=null
    private val samples = ArrayDeque<HandObservation>()
    var status="张开手，准备挥动"; private set
    val ready get()=armed
    fun reset() { rest=null; armed=false; poseLostAt=null;samples.clear();status="张开手，准备挥动" }
    fun update(hand: HandObservation): SwipeMotion? {
        val pose=if(scene)hand.swipePose else hand.openPalm
        if(hand.fist || (scene && hand.pinchRatio<=config.pinchClose) || (!pose && (!scene || !armed))){reset();return null}
        // Once deliberately armed, wrist flexion may briefly obscure extended fingers.
        // Never bridge a lost tracking frame, a fist, a true pinch or sustained closed posture.
        if(scene && !pose){
            if(poseLostAt==null)poseLostAt=hand.time
            if(hand.time-checkNotNull(poseLostAt)>300){reset();return null}
        }else poseLostAt=null
        val prior=samples.lastOrNull()
        if (prior != null && hand.time-prior.time !in 1..config.maxGapMs) reset()
        if (!armed) {
            val anchor=rest
            status="手掌稳住片刻"
            if (anchor==null || distance(anchor.palm,hand.palm)>(if(scene).035f else .025f) || hand.time-anchor.time>config.maxGapMs+config.swipeRestMs) rest=hand
            else if (hand.time-anchor.time>=config.swipeRestMs) { armed=true; samples.add(hand);status="已就绪 · 上下挥手" }
            return null
        }
        samples.add(hand)
        status="已就绪 · 上下挥手"
        while (samples.size>1 && hand.time-samples.first().time>config.swipeWindowMs) samples.removeFirst()
        for (first in samples) {
            val dt=(hand.time-first.time)/1000f
            if (dt<.08f) continue
            // Projected wrist-to-knuckle length changes sharply when waving at a front camera.
            val scale=hand.palmSize/first.palmSize
            if (scale !in (if(scene).4f..2.5f else .75f..1.30f)) { reset(); return null }
            val dx=hand.palm.x-first.palm.x;val dy=hand.palm.y-first.palm.y
            val vertical=abs(dy)>abs(dx)*config.swipeAxisRatio
            val horizontal=abs(dx)>abs(dy)*config.swipeAxisRatio
            val travel=if(vertical) abs(dy) else if(horizontal) abs(dx) else continue
            if(travel<config.swipeDistance || travel/dt<config.swipeMinSpeed) continue
            val path=samples.filter {it.time>=first.time}.zipWithNext().sumOf { (a,b)->distance(a.palm,b.palm).toDouble() }.toFloat()
            if (path>0 && distance(first.palm,hand.palm)/path<.8f) continue
            val direction=if(vertical) {if(dy<0) SwipeDirection.UP else SwipeDirection.DOWN}
                else if(dx<0) SwipeDirection.LEFT else SwipeDirection.RIGHT
            reset();return SwipeMotion(direction,travel/dt)
        }
        return null
    }
}
