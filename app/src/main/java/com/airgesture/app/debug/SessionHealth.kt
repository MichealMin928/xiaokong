package com.airgesture.app.debug

import com.airgesture.app.accessibility.ActionResult
import java.util.Locale

/** Constant-size cumulative counters preserve the complete run even after the raw log wraps. */
class SessionHealth(private val startedAt:Long) {
    private var frames=0L
    private var handFrames=0L
    private var ageSum=0L
    private var maxAge=0L
    private var gaps=0L
    private var lastFrame=startedAt
    private val actions=mutableMapOf<ActionResult,Long>()
    fun frame(now:Long,age:Long,hasHand:Boolean) {
        if(now-lastFrame>350)gaps++
        lastFrame=now;frames++;if(hasHand)handFrames++
        ageSum+=age;maxAge=maxOf(maxAge,age)
    }
    fun action(result:ActionResult){actions[result]=(actions[result] ?: 0)+1}
    fun summary(now:Long)=String.format(Locale.US,
        "session_seconds=%d frames=%d hand_frames=%d mean_age_ms=%.1f max_age_ms=%d gaps_over_350ms=%d completed_actions=%d cancelled_actions=%d rejected_actions=%d timed_out_actions=%d",
        (now-startedAt)/1000,frames,handFrames,if(frames==0L)0.0 else ageSum.toDouble()/frames,maxAge,gaps,
        actions[ActionResult.COMPLETED] ?: 0,actions[ActionResult.CANCELLED] ?: 0,actions[ActionResult.REJECTED] ?: 0,actions[ActionResult.TIMED_OUT] ?: 0)
}
