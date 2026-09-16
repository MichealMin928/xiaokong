package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.ActionSettings
import org.junit.Assert.*
import org.junit.Test

class PinchAimRegressionTest {
    private var t=1000L
    private val aim=CursorPoint(.12f,.14f)
    private fun hand(r:Float,y:Float=.14f,palmY:Float=.5f):HandObservation {t+=60;return HandObservation(t,CursorPoint(.12f,y),CursorPoint(.5f,palmY),r,false,false)}
    private fun frame(m:GestureStateMachine,r:Float=.9f,y:Float=.14f,palmY:Float=.5f):GestureDecision {val h=hand(r,y,palmY);return m.update(h,t)}
    @Test fun naturallyBendingIndexDownFortyPercentStillClicksOriginalTarget(){
        val m=GestureStateMachine()
        repeat(8){frame(m)}
        frame(m,.86f,.16f)
        assertEquals(aim,frame(m,.73f,.23f).frozenCursor)
        frame(m,.55f,.34f);frame(m,.39f,.43f);frame(m,.24f,.54f);frame(m,.22f,.56f)
        val click=frame(m,.21f,.56f)
        assertEquals(ActionKind.CLICK,click.action);assertEquals(aim,click.point)
        repeat(15){assertNull(frame(m,.21f,.56f).action)}
    }
    @Test fun abortedApproachDoesNotClickAndWholeHandMotionCancels(){
        val m=GestureStateMachine();repeat(7){frame(m)}
        frame(m,.72f,.25f);assertNull(frame(m,.9f,.14f).action)
        repeat(8){frame(m)};frame(m,.65f,.3f);frame(m,.2f,.5f)
        repeat(8){assertNull(frame(m,.2f,.55f,palmY=.7f).action)}
    }
    @Test fun releaseClicksOnceAndLongHoldDragsWithoutPreClick(){
        val m=GestureStateMachine(ActionSettings(drag=true));repeat(8){frame(m)}
        frame(m,.2f,.4f);frame(m,.2f,.45f);assertNull(frame(m,.2f,.5f).action)
        val click=frame(m,.9f,.15f);assertEquals(ActionKind.CLICK,click.action);assertEquals(aim,click.point)
        repeat(12){frame(m)}
        frame(m,.2f,.4f);frame(m,.2f,.5f);frame(m,.2f,.5f)
        val decisions=(0..12).map{frame(m,.2f,.5f)}
        assertEquals(1,decisions.count{it.action==ActionKind.DRAG_START});assertFalse(decisions.any{it.action==ActionKind.CLICK})
        val move=frame(m,.2f,.7f,palmY=.6f);assertEquals(ActionKind.DRAG_MOVE,move.action);assertTrue(checkNotNull(move.point).y>aim.y)
        assertEquals(ActionKind.DRAG_END,frame(m,.9f,.7f).action)
    }
    @Test fun lossDuringPendingClickNeverClicksAfterReentry(){
        val m=GestureStateMachine(ActionSettings(drag=true));repeat(8){frame(m)}
        repeat(4){frame(m,.2f,.5f)};m.update(null,t+30)
        repeat(10){assertNull(frame(m,.9f).action)}
    }
    @Test fun quickDoublePinchEmitsDoubleWithoutSingleSideEffect(){
        val m=GestureStateMachine(ActionSettings(doublePinch=true));repeat(8){frame(m)}
        val result=mutableListOf<GestureDecision>()
        repeat(3){result+=frame(m,.2f)};repeat(3){result+=frame(m)}
        repeat(3){result+=frame(m,.2f)};repeat(15){result+=frame(m)}
        assertEquals(1,result.count{it.action==ActionKind.DOUBLE_CLICK});assertFalse(result.any{it.action==ActionKind.CLICK})
    }
    @Test fun externalTrackingResetCannotReleaseOldPendingClick(){
        val m=GestureStateMachine(ActionSettings(drag=true));repeat(8){frame(m)};repeat(4){frame(m,.2f,.5f)}
        m.setPaused(false);repeat(8){assertNull(frame(m).action)}
    }
}
