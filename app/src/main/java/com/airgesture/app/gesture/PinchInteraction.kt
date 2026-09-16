package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.ActionSettings

/** Mouse: short pinch releases a click; a 550 ms hold starts drag without a preceding click.
 * Scene: resolve double pinch before emitting single, so liking never first toggles playback. */
class PinchInteraction {
    private var held:CursorPoint?=null
    private var heldPalm:CursorPoint?=null
    private var since=0L
    private var dragging=false
    private var lastPoint:CursorPoint?=null
    private var pendingSingle:GestureDecision?=null
    private var pendingAt=0L
    private var lastTime=0L
    fun reset(){held=null;heldPalm=null;dragging=false;lastPoint=null;pendingSingle=null;lastTime=0}
    fun process(decision:GestureDecision,hand:HandObservation?,now:Long,settings:ActionSettings,paused:Boolean):GestureDecision {
        if(hand==null || !hand.valid || now-hand.time !in 0..250 || (lastTime>0 && hand.time-lastTime !in 1..250) || paused || decision.pauseChanged || hand.fist){
            val end=if(dragging)GestureDecision(ActionKind.DRAG_END,lastPoint,message="已松开拖动") else decision.copy(action=null)
            reset();return end
        }
        lastTime=hand.time
        if(settings.drag){
            if(decision.action==ActionKind.CLICK){held=decision.point;heldPalm=hand.palm;since=now}
            held?.let{anchor->
                if(!dragging && heldPalm?.let{distance(it,hand.palm)>.085f}==true){reset();return GestureDecision(message="手掌移动，取消本次点击")}
                if(hand.pinchRatio>=.50f){
                    val result=GestureDecision(if(dragging)ActionKind.DRAG_END else ActionKind.CLICK,if(dragging)lastPoint else anchor,anchor,message=if(dragging)"拖动已放下" else "松开点击")
                    reset();return result
                }
                if(now-since>=8000){val end=GestureDecision(ActionKind.DRAG_END,lastPoint ?: anchor,message="拖动已到时，请松开");reset();return end}
                if(!dragging && now-since>=550){dragging=true;heldPalm=hand.palm;lastPoint=anchor;return GestureDecision(ActionKind.DRAG_START,anchor,anchor,message="正在拖动 · 移动整只手，松开放下")}
                if(dragging){
                    val origin=checkNotNull(heldPalm)
                    val point=CursorPoint((anchor.x+(hand.palm.x-origin.x)*2.5f).coerceIn(0f,1f),(anchor.y+(hand.palm.y-origin.y)*2.5f).coerceIn(0f,1f))
                    lastPoint=point;return GestureDecision(ActionKind.DRAG_MOVE,point,point,message="正在拖动 · 松开放下")
                }
                return decision.copy(action=null,frozenCursor=anchor,message="松开点击 · 继续捏住可拖动")
            }
        }
        if(settings.doublePinch){
            if(decision.action==ActionKind.CLICK){
                if(pendingSingle!=null && now-pendingAt<=550){pendingSingle=null;return decision.copy(action=ActionKind.DOUBLE_CLICK,message="双捏")}
                pendingSingle=decision;pendingAt=now;return decision.copy(action=null,message="单捏待确认 · 再捏一次为双捏")
            }
            if(pendingSingle!=null && now-pendingAt>550){val result=checkNotNull(pendingSingle);pendingSingle=null;return result.copy(message="单捏")}
            if(decision.action!=null)pendingSingle=null
        }
        return decision
    }
}

/** Mirror/handedness invariant sign reversal, held at both ends; fingers must stay open. */
class PalmFlipDetector {
    private var sign=0
    private var since=0L
    private var armed=false
    private var palm:CursorPoint?=null
    private var reverseAt:Long?=null
    fun reset(){sign=0;since=0;armed=false;palm=null;reverseAt=null}
    fun update(h:HandObservation):Boolean {
        if(!h.openPalm || h.pinchRatio<.6f){reset();return false}
        val next=if(h.palmFacing>.08f)1 else if(h.palmFacing<-.08f)-1 else 0
        if(next==0)return false
        if(sign==0){sign=next;since=h.time;palm=h.palm;return false}
        if(distance(checkNotNull(palm),h.palm)>.1f){reset();return false}
        if(!armed){if(next!=sign){reset();return false};if(h.time-since>=250)armed=true;return false}
        if(next==sign){reverseAt=null;return false}
        if(reverseAt==null)reverseAt=h.time
        if(h.time-checkNotNull(reverseAt)>=150){reset();return true}
        return false
    }
}
