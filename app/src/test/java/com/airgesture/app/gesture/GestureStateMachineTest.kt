package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class GestureStateMachineTest {
    private val machine=GestureStateMachine()
    private var time=1000L
    private fun frame(pinch:Float=.8f,x:Float=.5f,dt:Long=100,palmX:Float=.5f):GestureDecision {
        time+=dt
        return machine.update(HandObservation(time,CursorPoint(x,.5f),CursorPoint(palmX,.5f),pinch,false,false),time)
    }
    private fun arm() { repeat(4){frame()} }
    @Test fun heldPinchClicksExactlyOnceAndLocksPrePinchPosition() {
        arm(); assertNull(frame(.2f).action)
        val click=frame(.2f,x=.52f)
        assertEquals(ActionKind.CLICK,click.action);assertEquals(CursorPoint(.5f,.5f),click.point)
        repeat(30){assertNull(frame(.2f).action)}
        frame();frame();frame()
        frame(.2f);assertEquals(ActionKind.CLICK,frame(.2f).action)
    }
    @Test fun closedHandAtStartupAndAfterLossCannotClick() {
        repeat(10){assertNull(frame(.2f).action)}
        arm();frame(.2f)
        machine.update(null,time+50)
        repeat(10){assertNull(frame(.2f).action)}
    }
    @Test fun oneFramePinchAndDragDoNotClick() {
        arm();frame(.2f);assertNull(frame().action);frame();frame()
        frame(.2f);assertNull(frame(.2f,x=.65f,palmX=.65f).action)
        repeat(10){assertNull(frame(.2f).action)}
    }
    @Test fun largeFrameGapAndPauseRequireFreshOpenHand() {
        arm();frame(.2f)
        assertNull(frame(.2f,dt=400).action)
        arm();machine.setPaused(true)
        repeat(4){assertNull(frame(.2f).action)}
        machine.setPaused(false)
        repeat(4){assertNull(frame(.2f).action)}
    }
}
