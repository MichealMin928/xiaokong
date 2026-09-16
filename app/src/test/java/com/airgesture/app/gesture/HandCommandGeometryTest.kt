package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class HandCommandGeometryTest {
    private fun folded()=MutableList(21){CursorPoint(.5f,.6f)}.apply{
        this[0]=CursorPoint(.5f,.75f);this[2]=CursorPoint(.42f,.65f);this[3]=CursorPoint(.34f,.60f);this[4]=CursorPoint(.26f,.55f)
        this[5]=CursorPoint(.43f,.59f);this[9]=CursorPoint(.5f,.57f);this[17]=CursorPoint(.62f,.61f)
        this[6]=CursorPoint(.43f,.55f);this[8]=this[6]
        this[10]=CursorPoint(.5f,.52f);this[12]=this[10]
        this[14]=CursorPoint(.56f,.55f);this[16]=this[14]
        this[18]=CursorPoint(.63f,.57f);this[20]=this[18]
    }
    private fun observe(p:List<CursorPoint>)=checkNotNull(HandObservation.from(p,480,640,1000,CursorPoint(.5f,.5f)))
    @Test fun sixUsesThumbAndLittleFingerOnEitherHandNeverIndexAndLittleFinger() {
        val six=folded().apply{this[20]=CursorPoint(.65f,.36f)}
        assertTrue(observe(six).homePose);assertTrue(observe(six.map{CursorPoint(1-it.x,it.y)}).homePose)
        assertFalse(observe(six).pointerPose)
        val old=six.toMutableList().apply{this[8]=CursorPoint(.43f,.34f)}
        assertFalse(observe(old).homePose)
    }
    @Test fun thumbsUpAndDownAreDistinctFromPointingAndSix() {
        for((y,expected) in listOf(.36f to ThumbCommandPose.UP,.91f to ThumbCommandPose.DOWN)){
            val p=folded().apply{this[2]=CursorPoint(.37f,.63f);this[3]=CursorPoint(.37f,(.63f+y)/2);this[4]=CursorPoint(.37f,y)}
            assertEquals(expected,observe(p).thumbCommand);assertFalse(observe(p).homePose);assertFalse(observe(p).pointerPose)
        }
        val point=folded().apply{this[8]=CursorPoint(.43f,.33f)}
        assertTrue(observe(point).pointerPose);assertFalse(observe(point).homePose)
    }

    @Test fun fingerOrientationUsesFourFingersAndIgnoresThumbOpeningOnEitherHand() {
        val up=folded().apply{
            this[8]=CursorPoint(.43f,.33f);this[12]=CursorPoint(.5f,.30f)
            this[16]=CursorPoint(.56f,.34f);this[20]=CursorPoint(.63f,.37f)
        }
        for(mirror in listOf(false,true))for(thumb in listOf(CursorPoint(.4f,.49f),CursorPoint(.24f,.55f))){
            val points=up.toMutableList().apply{this[4]=thumb}.map{if(mirror)CursorPoint(1-it.x,it.y) else it}
            assertTrue(observe(points).pagePose);assertEquals(FingerDirection.UP,observe(points).fingerDirection)
            assertEquals(FingerDirection.DOWN,observe(points.map{CursorPoint(it.x,1-it.y)}).fingerDirection)
            assertFalse(observe(points).pointerPose);assertFalse(observe(points).thumbOnly)
        }
        up[20]=up[18]
        assertFalse("Three extended fingers cannot arm four-finger pages",observe(up).pagePose)
    }
    @Test fun thumbLocalExcursionsSurviveMirrorAndDoNotCountWholeHandTranslation() {
        val p=folded().apply{this[8]=CursorPoint(.43f,.33f)}
        val tapped=p.toMutableList().apply{this[4]=CursorPoint(.32f,.53f)}
        fun delta(a:List<CursorPoint>,b:List<CursorPoint>)=distance(checkNotNull(observe(a).thumbLocal),checkNotNull(observe(b).thumbLocal))
        val travel=delta(p,tapped)
        assertTrue(travel>.24f)
        assertEquals(travel,delta(p.map{CursorPoint(1-it.x,it.y)},tapped.map{CursorPoint(1-it.x,it.y)}),.0001f)
        assertEquals(0f,delta(p,p.map{CursorPoint(it.x+.05f,it.y+.04f)}),.0001f)
    }
    @Test fun fiveFingerPoseRequiresAnExtendedThumbOnBothHands() {
        val hand=folded().apply{
            this[8]=CursorPoint(.43f,.33f);this[12]=CursorPoint(.5f,.30f)
            this[16]=CursorPoint(.56f,.34f);this[20]=CursorPoint(.63f,.37f)
        }
        for(mirror in listOf(false,true))for(open in listOf(false,true)){
            val p=hand.toMutableList().apply{if(!open)this[4]=this[3]}.map{if(mirror)CursorPoint(1-it.x,it.y) else it}
            assertEquals(open,observe(p).fiveFingerPose);assertTrue(observe(p).fourFingerPose)
            assertFalse(observe(p).twoFingerPose);assertEquals(FingerDirection.UP,observe(p).fingerDirection)
        }
    }

    @Test fun downPoseRequiresIndexAndMiddleWhileRingAndLittleStayFolded() {
        val p=folded().apply{this[8]=CursorPoint(.43f,.33f);this[12]=CursorPoint(.5f,.30f)}
        for(mirror in listOf(false,true)){
            val down=p.map{CursorPoint(if(mirror)1-it.x else it.x,1-it.y)}
            val h=observe(down)
            assertTrue(h.twoFingerPose);assertFalse(h.fourFingerPose);assertTrue(h.pagePose)
            assertFalse(h.pointerPose);assertFalse(h.homePose);assertEquals(FingerDirection.DOWN,h.fingerDirection)
        }
    }
}
