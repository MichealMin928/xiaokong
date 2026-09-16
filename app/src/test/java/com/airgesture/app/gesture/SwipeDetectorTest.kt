package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.ActionSettings
import com.airgesture.app.accessibility.ActionKind
import org.junit.Assert.*
import org.junit.Test

class SwipeDetectorTest {
    private val machine=GestureStateMachine(ActionSettings(pinch=false))
    private var time=1000L
    private fun frame(y:Float=.5f,x:Float=.5f,open:Boolean=true,dt:Long=100):GestureDecision {
        time+=dt
        return machine.update(HandObservation(time,CursorPoint(x,y),CursorPoint(x,y),.8f,open,false),time)
    }
    private fun rest(y:Float=.5f) { repeat(5){frame(y)} }
    @Test fun oneUpAndOneDownAfterRestAndCooldown() {
        rest();frame(.4f);assertEquals(ActionKind.SWIPE_UP,frame(.3f).action)
        repeat(10){assertNull(frame(.3f-it*.015f).action)}
        rest(.3f);frame(.4f);assertEquals(ActionKind.SWIPE_DOWN,frame(.5f).action)
    }
    @Test fun arrivingMovingSlowDriftPointingAndDiagonalCannotSwipe() {
        repeat(5){assertNull(frame(.7f-it*.06f).action)}
        rest();repeat(10){assertNull(frame(.5f-it*.015f).action)}
        rest();frame(.4f,x=.4f);assertNull(frame(.3f,x=.3f).action)
        rest();frame(.4f,open=false);assertNull(frame(.3f,open=false).action)
    }
    @Test fun lossAndStaleFrameBreakTrajectory() {
        rest();frame(.4f);machine.update(null,time+10)
        assertNull(frame(.3f).action)
        rest();frame(.4f);assertNull(frame(.3f,dt=400).action)
    }
    @Test fun directionCanBeDisabled() {
        machine.configure(ActionSettings(pinch=false,swipeUp=false))
        rest();frame(.4f);assertNull(frame(.3f).action)
    }
    @Test fun horizontalActionsAreOptInAndMirrorDirectionIsExplicit() {
        rest();frame(x=.4f);assertNull(frame(x=.3f).action)
        machine.configure(ActionSettings(pinch=false,swipeLeftBack=true,swipeRightHome=true))
        rest();frame(x=.4f);assertEquals(ActionKind.BACK,frame(x=.3f).action)
        repeat(8){frame()};rest();frame(x=.6f);assertEquals(ActionKind.HOME,frame(x=.7f).action)
    }
}
