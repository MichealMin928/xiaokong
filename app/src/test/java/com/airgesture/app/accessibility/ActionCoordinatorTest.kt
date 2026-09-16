package com.airgesture.app.accessibility

import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class ActionCoordinatorTest {
    private var time=1000L
    private val receipts=mutableListOf<ActionReceipt>()
    private val controller=ActionCoordinator({time},receipt={receipts.add(it)})
    private val screen=ScreenGeometry(1080,2400,0)
    private fun action()=SystemAction(ActionKind.CLICK,CursorPoint(300f,800f),screen,time)
    @Test fun noPermissionStaleInvalidOrRotatedCannotDispatch() {
        var sends=0
        fun submit(a:SystemAction,allowed:Boolean=true,s:ScreenGeometry=screen) = controller.submit(a,allowed,s) { _,_-> sends++;true }
        submit(action(),false)
        submit(action().copy(detectedAt=time-251))
        submit(action().copy(point=CursorPoint(Float.NaN,0f)))
        submit(action().copy(point=CursorPoint(1080f,0f)))
        submit(action(),s=ScreenGeometry(2400,1080,1))
        assertEquals(0,sends);assertEquals(5,receipts.size)
    }
    @Test fun singleFlightNoQueueAndNoRetryOnCancellation() {
        var callback:((ActionResult)->Unit)?=null
        assertTrue(controller.submit(action(),true,screen) { _,c->callback=c;true })
        assertFalse(controller.submit(action(),true,screen) { _,_->fail("must not queue");true })
        callback!!(ActionResult.CANCELLED)
        assertFalse(controller.busy)
        assertEquals(listOf(ActionResult.DISPATCHED,ActionResult.REJECTED,ActionResult.CANCELLED),receipts.map {it.result})
    }
    @Test fun lateCallbackCannotCompleteAnotherAction() {
        var first:((ActionResult)->Unit)?=null
        controller.submit(action(),true,screen){_,c->first=c;true}
        time+=1300; controller.expire()
        assertEquals(ActionResult.TIMED_OUT,receipts.last().result)
        controller.submit(action(),true,screen){_,_->true}
        first!!(ActionResult.COMPLETED)
        assertTrue(controller.busy)
        controller.reset();assertFalse(controller.busy)
    }
}
