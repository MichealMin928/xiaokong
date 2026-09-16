package com.airgesture.app.accessibility
import org.junit.Assert.*
import org.junit.Test
class SwipeConfigTest {
    @Test fun preservesFourDirectionsAndNormalizesGeometry(){
        val up=checkNotNull(SwipeConfig.forAction(ActionKind.SWIPE_UP));val down=checkNotNull(SwipeConfig.forAction(ActionKind.SWIPE_DOWN))
        val left=checkNotNull(SwipeConfig.forAction(ActionKind.SWIPE_LEFT));val right=checkNotNull(SwipeConfig.forAction(ActionKind.SWIPE_RIGHT))
        assertEquals(.74f,up.startY,.00001f);assertEquals(.26f,up.endY,.00001f);assertEquals(280,up.duration)
        assertTrue(up.startY>up.endY && down.startY<down.endY && left.startX>left.endX && right.startX<right.endX)
        for(s in listOf(up,down,left,right))assertTrue(s.valid)
        assertNull(SwipeConfig.forAction(ActionKind.CLICK))
    }
    @Test fun unsafeCoordinatesAreRejected(){assertFalse(SwipeConfig(Float.NaN,0f,0f,0f).valid);assertFalse(SwipeConfig(.5f,.8f,.5f,.2f,10000).valid)}
}
