package com.airgesture.app.gesture

import com.airgesture.app.settings.ActionConfig

/** Opening the palm rearms a single hold. Keeping a fist closed never toggles repeatedly. */
class FistDetector(private val config: ActionConfig = ActionConfig()) {
    private var openAt: Long? = null
    private var armed=false
    private var start: HandObservation? = null
    private var frames=0
    fun reset() { openAt=null;armed=false;start=null;frames=0 }
    fun update(hand: HandObservation): Boolean {
        if (!hand.fist) {
            start=null;frames=0
            if (hand.openPalm && hand.pinchRatio>=config.pinchRelease) {
                if(openAt==null) openAt=hand.time
                if(hand.time-checkNotNull(openAt)>=config.neutralMs) armed=true
            } else openAt=null
            return false
        }
        openAt=null
        if(!armed) return false
        val anchor=start
        if(anchor==null) {start=hand;frames=1;return false}
        if(distance(anchor.palm,hand.palm)>config.fistMaxDrift) {reset();return false}
        frames++
        if(frames>=4 && hand.time-anchor.time>=config.fistHoldMs) {reset();return true}
        return false
    }
}
