package com.airgesture.app.eye

import org.junit.Assert.*
import org.junit.Test

class GazeTrackingTest {
    private val c=GazeCalibration(.3f,.5f,.7f,0f,.4f,0f,.3f)
    @Test fun calibrationClassifiesBothSignalDirectionsAndRejectsHeadMotion(){
        fun sample(v:Float)=GazeSample(1000,v,0f,.4f,0f,.3f)
        assertEquals(GazeRegion.TOP,c.region(sample(.3f)));assertEquals(GazeRegion.CENTER,c.region(sample(.5f)));assertEquals(GazeRegion.BOTTOM,c.region(sample(.7f)))
        val reverse=c.copy(top=.7f,bottom=.3f);assertEquals(GazeRegion.TOP,reverse.region(sample(.7f)))
        assertEquals(GazeRegion.UNKNOWN,c.region(sample(.5f).copy(yaw=.2f)))
        assertEquals(GazeRegion.UNKNOWN,c.region(sample(.5f).copy(scale=.7f)))
        assertFalse(c.copy(bottom=.51f).valid)
    }
    @Test fun mustReadCenterBottomThenTopOnceWithDwell(){
        val r=GazeReader();var t=1000L
        fun hold(z:GazeRegion,n:Int=8)=List(n){t+=100;r.update(z,t)}.count{it}
        assertEquals(0,hold(GazeRegion.BOTTOM));assertEquals(0,hold(GazeRegion.TOP))
        hold(GazeRegion.CENTER);hold(GazeRegion.BOTTOM)
        assertEquals(1,hold(GazeRegion.TOP));assertEquals(0,hold(GazeRegion.TOP,40))
        hold(GazeRegion.CENTER);hold(GazeRegion.BOTTOM);assertEquals(1,hold(GazeRegion.TOP))
    }
    @Test fun blinkLossAndOldFrameCancelPreparedPage(){
        val r=GazeReader();var t=1000L
        fun hold(z:GazeRegion){repeat(8){t+=100;assertFalse(r.update(z,t))}}
        hold(GazeRegion.CENTER);hold(GazeRegion.BOTTOM);r.update(GazeRegion.UNKNOWN,t+100);hold(GazeRegion.TOP)
        hold(GazeRegion.CENTER);hold(GazeRegion.BOTTOM);assertFalse(r.update(GazeRegion.TOP,t+1000));hold(GazeRegion.TOP)
    }
    @Test fun threeStableCalibrationStagesAreRequired(){
        val calibrator=GazeCalibrator();var t=1000L
        repeat(130){t+=100;val value=when(calibrator.stage){GazeRegion.TOP->.3f;GazeRegion.CENTER->.5f;else->.7f}
            calibrator.update(GazeSample(t,value,0f,.4f,0f,.3f),t)}
        assertNotNull(calibrator.result);assertTrue(checkNotNull(calibrator.result).valid)
    }
    @Test fun orderedButDelayedFramesCancelPreparedPageAndCannotRearm(){
        val r=GazeReader();var t=1000L
        for(region in listOf(GazeRegion.CENTER,GazeRegion.BOTTOM))repeat(8){t+=100;assertFalse(r.update(region,t,t))}
        // Continuous callbacks can still contain old images; timestamp order alone is insufficient.
        repeat(8){t+=100;assertFalse(r.update(GazeRegion.TOP,t,t+300))}
        repeat(8){t+=100;assertFalse(r.update(GazeRegion.TOP,t,t))}
        for(region in listOf(GazeRegion.CENTER,GazeRegion.BOTTOM,GazeRegion.TOP))repeat(8){t+=100;assertFalse(r.update(region,t,t+300))}
        assertEquals(GazeRegion.UNKNOWN,r.region)
    }
    @Test fun localEyeAxisUsesIrisHeightAndRejectsClosedLids(){
        val p=MutableList(478){FacePoint(.5f,.5f)}
        p[33]=FacePoint(.3f,.35f);p[133]=FacePoint(.45f,.35f);p[362]=FacePoint(.55f,.35f);p[263]=FacePoint(.7f,.35f)
        p[159]=FacePoint(.375f,.33f);p[145]=FacePoint(.375f,.37f);p[386]=FacePoint(.625f,.33f);p[374]=FacePoint(.625f,.37f)
        p[468]=FacePoint(.375f,.342f);p[473]=FacePoint(.625f,.342f);p[1]=FacePoint(.5f,.5f);p[152]=FacePoint(.5f,.8f)
        assertEquals(.3f,checkNotNull(GazeFeatures.from(p,1000,640,480)).vertical,.01f)
        assertNull(GazeFeatures.from(p,1000,640,480,1251))
        assertNull(GazeFeatures.from(p,1000,640,480,999))
        p[159]=FacePoint(.375f,.35f);p[145]=FacePoint(.375f,.351f);assertNull(GazeFeatures.from(p,1000,640,480))
    }
    @Test fun noFaceOrBlinkNeverProducesSample(){assertNull(GazeFeatures.from(null,1,640,480));assertNull(GazeFeatures.from(List(478){FacePoint(.5f,.5f)},1,640,480))}
}
