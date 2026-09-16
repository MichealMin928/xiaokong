package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class PushDetectorTest {
    private fun frame(t:Long,size:Float=.2f,area:Float=.1f,x:Float=.5f)=HandObservation(t,CursorPoint(.5f,.5f),CursorPoint(x,.5f),.8f,true,false,size,area)
    private fun arm(d:PushDetector){repeat(5){d.update(frame(1000+it*100L))}}
    @Test fun expansionNeedsMultipleCuesAndFiresOnceUntilRetreat() {
        val d=PushDetector();arm(d)
        assertFalse(d.update(frame(1500,.27f,.18f)))
        assertTrue(d.update(frame(1600,.27f,.18f)))
        repeat(20){assertFalse(d.update(frame(1700+it*100L,.27f,.18f)))}
    }
    @Test fun translationOrOnlyOneGrowthCueDoesNotClick() {
        val d=PushDetector();arm(d)
        assertFalse(d.update(frame(1500,.27f,.1f)))
        arm(d);assertFalse(d.update(frame(1500,.27f,.18f,.7f)))
    }
}
