package com.airgesture.app.cursor

import org.junit.Assert.*
import org.junit.Test

class CursorPresentationTest {
    @Test fun largeMovesReachMeasuredTargetWithinTwoDisplayFramesWhileSmallCorrectionsStaySmooth(){
        val c=CursorPresentation();c.set(CursorPoint(.2f,.3f),1000)
        c.set(CursorPoint(.6f,.3f),1140)
        assertEquals(.6f,checkNotNull(c.sample(1172)).x,0f);assertFalse(c.moving(1172))
        c.set(CursorPoint(.62f,.3f),1280)
        assertTrue(c.moving(1312));assertEquals(.61f,checkNotNull(c.sample(1310)).x,.0001f)
        assertEquals(.62f,checkNotNull(c.sample(1340)).x,0f)
    }
    @Test fun lowFrameRateTargetsAnimateWithoutOvershootOrBackwardPrediction(){
        val c=CursorPresentation()
        assertEquals(CursorPoint(.2f,.3f),c.set(CursorPoint(.2f,.3f),1000))
        c.set(CursorPoint(.5f,.3f),1100)
        val xs=(1100L..1190L step 10).map{checkNotNull(c.sample(it)).x}
        assertTrue(xs.zipWithNext().all{(a,b)->b>=a})
        assertTrue(xs.all{it in .2f.. .5f});assertFalse(c.moving(1160))
        assertEquals(.5f,checkNotNull(c.sample(1600)).x,0f)
    }
    @Test fun confirmationIsImmediateAndHiddenCursorHasNoPendingAnimation(){
        val c=CursorPresentation();c.set(CursorPoint(.2f,.3f),1000);c.set(CursorPoint(.6f,.3f),1100)
        val aim=CursorPoint(.35f,.3f)
        assertEquals(aim,c.set(aim,1120,true));assertFalse(c.moving(1120))
        c.reset();assertNull(c.sample(1200));assertFalse(c.moving(1200))
        assertEquals(CursorPoint(.8f,.9f),c.set(CursorPoint(.8f,.9f),1500))
    }
}
