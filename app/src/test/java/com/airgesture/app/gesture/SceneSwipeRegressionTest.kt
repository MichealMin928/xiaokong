package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.ActionSettings
import org.junit.Assert.*
import org.junit.Test

class SceneSwipeRegressionTest {
    private val machine=GestureStateMachine(ActionSettings(sceneSwipes=true,doublePinch=true,palmFlip=true))
    private var time=1000L
    private fun frame(y:Float=.6f,pinch:Float=1f,open:Boolean=true,dt:Long=140,x:Float=.5f):GestureDecision {
        time+=dt
        return machine.update(HandObservation(time,CursorPoint(x,y),CursorPoint(x,y),pinch,open,false),time+90)
    }
    private fun rest(){repeat(6){frame()}}

    @Test fun moderatePalmStrokeAtSevenFpsEmitsOnePageWithoutClick() {
        rest()
        val actions=listOf(frame(.57f),frame(.53f),frame(.49f),frame(.45f)).mapNotNull{it.action}
        assertEquals(listOf(ActionKind.SWIPE_UP),actions)
        repeat(12){assertNull(frame(.45f-it*.009f).action)}
    }
    @Test fun thumbDistanceVariationMustNotStealAnOpenHandSwipe() {
        rest()
        val actions=listOf(frame(.57f,.82f),frame(.53f,.78f),frame(.49f,.80f),frame(.45f,.81f)).mapNotNull{it.action}
        assertEquals(listOf(ActionKind.SWIPE_UP),actions)
    }
    @Test fun arrivalSlowDriftDiagonalAndLostHandCannotTurnPages() {
        repeat(9){assertNull(frame(.8f-it*.03f).action)}
        machine.resetTracking();rest()
        repeat(20){assertNull(frame(.6f-it*.006f).action)}
        machine.resetTracking();rest()
        repeat(6){assertNull(frame(.6f-it*.03f,x=.5f-it*.03f).action)}
        machine.resetTracking();rest();frame(.55f);machine.update(null,time+100)
        assertNull(frame(.48f).action)
        machine.resetTracking();rest();frame(.55f);assertNull(frame(.48f,dt=400).action)
    }
    @Test fun realContactStillRequiresHoldAndDoesNotTurnIntoSwipe() {
        rest();assertNull(frame(pinch=.2f,open=false).action)
        assertNull(frame(pinch=.2f,open=false).action)
        val actions=List(6){frame(pinch=.2f,open=false)}.mapNotNull{it.action}
        assertEquals(listOf(ActionKind.CLICK),actions)
    }
    @Test fun armedPalmCanFlexBrieflyDuringStrokeButCannotArmWhileClosed() {
        rest();assertNull(frame(.57f,.7f).action)
        assertNull(frame(.53f,.36f,open=false).action)
        assertEquals(ActionKind.SWIPE_UP,frame(.49f,.39f,open=false).action)
        machine.resetTracking()
        repeat(8){assertNull(frame(.7f-it*.04f,.39f,open=false).action)}
    }
    @Test fun wristForeshorteningCannotEraseArmedMotion() {
        rest()
        val actions=mutableListOf<ActionKind>()
        for((y,size) in listOf(.57f to .16f,.53f to .12f,.49f to .11f)){
            time+=140
            machine.update(HandObservation(time,CursorPoint(.5f,y),CursorPoint(.5f,y),.8f,true,false,palmSize=size),time+90).action?.let(actions::add)
        }
        assertEquals(listOf(ActionKind.SWIPE_UP),actions)
    }
    @Test fun actualPinchAndSustainedClosedPostureCancelArmedSwipe() {
        rest();frame(.56f)
        assertNull(frame(.49f,.2f,open=false).action)
        machine.resetTracking();rest()
        repeat(5){assertNull(frame(.6f,.38f,open=false).action)}
        assertNull(frame(.48f,.38f,open=false).action)
    }
}
