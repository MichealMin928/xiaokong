package com.airgesture.app.cursor

import org.junit.Assert.*
import org.junit.Test

class CalibrationAndMotionTest {
    @Test fun stableCornersProduceRegionAndInvertedRangeIsRejected() {
        fun feed(s:CalibrationSession,p:CursorPoint,t:Long){s.capture(t);repeat(9){s.update(p,t+it*100)}}
        val s=CalibrationSession();feed(s,CursorPoint(.3f,.2f),1000);feed(s,CursorPoint(.7f,.6f),2000)
        assertEquals(ControlRegion(.3f,.7f,.2f,.6f),s.result)
        val bad=CalibrationSession();feed(bad,CursorPoint(.7f,.6f),1000);feed(bad,CursorPoint(.3f,.2f),2000)
        assertNull(bad.result);assertEquals(CalibrationStage.TOP_LEFT,bad.stage)
    }
    @Test fun handLossMovementAndTimeoutCannotSaveCalibration() {
        val s=CalibrationSession();s.capture(1000)
        repeat(4){s.update(CursorPoint(.3f,.2f),1000+it*100L)}
        s.update(null,1400);s.update(CursorPoint(.3f,.2f),1500)
        s.update(CursorPoint(.5f,.4f),1600);s.update(null,6100)
        assertEquals(CalibrationStage.TOP_LEFT,s.stage);assertFalse(s.collecting);assertNull(s.result)
    }
    @Test fun velocityGainIsPreciseWhenSlowFastWhenQuickAndStableWhenStill() {
        val slow=CursorMotionCurve();slow.apply(CursorPoint(.5f,.5f),1000)
        val p=slow.apply(CursorPoint(.51f,.5f),1200);assertTrue(p.x<.51f)
        assertEquals(p,slow.apply(CursorPoint(.51f,.5f),1300))
        val fast=CursorMotionCurve();fast.apply(CursorPoint(.5f,.5f),1000)
        assertTrue(fast.apply(CursorPoint(.6f,.5f),1100).x>.6f)
        assertEquals(1f,fast.apply(CursorPoint(1f,.5f),1200).x,0f)
        assertEquals(CursorPoint(.2f,.2f),fast.apply(CursorPoint(.2f,.2f),1800))
    }
}
