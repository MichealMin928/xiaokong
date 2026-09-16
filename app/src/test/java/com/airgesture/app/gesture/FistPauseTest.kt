package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class FistPauseTest {
    private val machine=GestureStateMachine()
    private var time=1000L
    private fun frame(fist:Boolean=false,pinch:Float=.8f,x:Float=.5f):GestureDecision {
        time+=100
        return machine.update(HandObservation(time,CursorPoint(x,.5f),CursorPoint(x,.5f),pinch,!fist,fist),time)
    }
    @Test fun holdOncePauseOpenThenHoldOnceResume() {
        repeat(4){frame()}
        var toggles=0
        repeat(25){if(frame(true).pauseChanged) toggles++}
        assertEquals(1,toggles);assertTrue(machine.paused)
        repeat(4){frame()}
        repeat(25){if(frame(true).pauseChanged) toggles++}
        assertEquals(2,toggles);assertFalse(machine.paused)
    }
    @Test fun initialFistTransientFistAndMovingFistDoNotToggle() {
        repeat(10){assertFalse(frame(true).pauseChanged)}
        repeat(4){frame()}
        frame(true);frame()
        assertFalse(machine.paused)
        repeat(4){frame()}
        frame(true)
        repeat(10){assertFalse(frame(true,x=.7f).pauseChanged)}
    }
    @Test fun pauseSuppressesClicksAndLossDoesNotResumeFromAnAlreadyHeldFist() {
        machine.setPaused(true)
        repeat(5){frame()};repeat(5){assertNull(frame(pinch=.2f).action)}
        machine.update(null,time+10)
        repeat(10){assertFalse(frame(true).pauseChanged)}
        assertTrue(machine.paused)
    }
}
