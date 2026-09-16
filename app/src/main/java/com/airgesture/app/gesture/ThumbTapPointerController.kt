package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import kotlin.math.max

/** A closer thumb, partial reopening, then a second approach confirms the original aim immediately. */
class ThumbTapPointerController {
    private val history=ArrayDeque<HandObservation>()
    private var baseline:HandObservation?=null
    private var trough:HandObservation?=null
    private var aim:CursorPoint?=null
    private var palm:CursorPoint?=null
    private var startedAt:Long?=null
    private var lastTime:Long?=null
    private var closeAt:Long?=null
    private var firstPulse=false
    private var reopened=false
    private var firstDepth=0f
    private var firstPulseAt=0L
    private var cooldownUntil=0L
    private var dragging=false
    private var dragPalm:CursorPoint?=null
    private var dragPoint:CursorPoint?=null
    var dragEnabled=true
    val busy get()=startedAt!=null || dragging
    val ready get()=history.size>=2 && !busy
    fun resetTracking(){history.clear();baseline=null;trough=null;aim=null;palm=null;startedAt=null;lastTime=null;closeAt=null;firstPulse=false;reopened=false;firstDepth=0f;dragging=false;dragPalm=null;dragPoint=null}
    fun cancel():GestureDecision {
        val end=if(dragging)GestureDecision(ActionKind.DRAG_END,dragPoint,message="已放下") else GestureDecision()
        resetTracking();return end
    }
    private fun thumbTravel(a:HandObservation,b:HandObservation)=distance(checkNotNull(a.thumbLocal),checkNotNull(b.thumbLocal))
    private fun thumbApproach(a:HandObservation,b:HandObservation):Float {
        val thumb=checkNotNull(a.thumbLocal)
        val index=a.indexLocal ?: return thumbTravel(a,b)
        val length=distance(thumb,index).coerceAtLeast(.001f)
        val moved=checkNotNull(b.thumbLocal)
        val dx=moved.x-thumb.x;val dy=moved.y-thumb.y
        val towardTip=(dx*(index.x-thumb.x)+dy*(index.y-thumb.y))/length
        // Closing from beside the index also crosses towards its shaft. Motion along
        // the finger (e.g. changing palm tilt) alone must not lock the mouse.
        val shaftLength=distance(CursorPoint(0f,0f),index).coerceAtLeast(.001f)
        val nx=index.y/shaftLength;val ny=-index.x/shaftLength
        val side=thumb.x*nx+thumb.y*ny
        val towardShaft=-(dx*nx+dy*ny)*if(side>=0)1 else -1
        return minOf(towardTip,towardShaft*2)
    }
    private fun thumbOpening(a:HandObservation,b:HandObservation)=thumbApproach(b,a)
    fun update(hand:HandObservation?,now:Long):GestureDecision {
        if(hand==null || !hand.valid || now-hand.time !in 0..250 || lastTime?.let{hand.time-it !in 1..250}==true)return cancel().copy(message="等待食指入镜")
        lastTime=hand.time
        if(dragging){
            if(hand.time-(closeAt ?: hand.time)>8000)return cancel().copy(message="拖动到时 · 松开后重试")
            if(hand.pagePose || hand.fist || hand.pinchRatio>=.52f)return cancel()
            val start=checkNotNull(dragPalm);val anchor=checkNotNull(aim)
            dragPoint=CursorPoint((anchor.x+(hand.palm.x-start.x)*2).coerceIn(0f,1f),(anchor.y+(hand.palm.y-start.y)*2).coerceIn(0f,1f))
            return GestureDecision(ActionKind.DRAG_MOVE,dragPoint,dragPoint,message="拖动中 · 松开放下")
        }
        if(hand.pagePose || hand.fist || hand.thumbLocal?.isFinite()!=true || (!hand.pointerPose && !busy))return cancel().copy(message="单独伸食指显示鼠标")
        if(now<cooldownUntil)return GestureDecision(message="已点击 · 移动食指瞄准",confirmation=2)
        if(!busy){
            while(history.isNotEmpty() && hand.time-history.first().time>450)history.removeFirst()
            val peak=history.maxOfOrNull{it.pinchRatio}
            val start=peak?.let{v->history.lastOrNull{it.pinchRatio>=v-.025f}}
            val recent=history.filter{hand.time-it.time<=350}
            val steady=recent.size>=2 && recent.all{distance(it.cursor,recent.last().cursor)<.045f && distance(it.palm,recent.last().palm)<.015f}
            if(steady && start!=null && start.pinchRatio-hand.pinchRatio>=.04f && thumbApproach(start,hand)>=.035f && distance(start.palm,hand.palm)<.035f){
                baseline=start;startedAt=hand.time;palm=start.palm
                // Use the latest displayed aim, not an old gap maximum that can pull the cursor backwards.
                aim=history.last().cursor
            }else {history.addLast(hand);return GestureDecision(message="食指瞄准 · 拇指靠近两次")}
        }
        if((firstPulse && hand.time-firstPulseAt>2500) || distance(checkNotNull(palm),hand.palm)>.025f){
            return cancel().copy(message="已取消确认 · 移动食指重新瞄准")
        }
        if(hand.pinchRatio<=.28f){
            if(closeAt==null)closeAt=hand.time
            if(dragEnabled && !reopened && hand.time-checkNotNull(closeAt)>=700){
                dragging=true;dragPalm=hand.palm;dragPoint=aim
                return GestureDecision(ActionKind.DRAG_START,aim,aim,message="长捏拖动 · 松开放下")
            }
        }else closeAt=null
        val start=checkNotNull(baseline)
        val drop=start.pinchRatio-hand.pinchRatio
        if(!firstPulse){
            if(drop>=max(.065f,start.pinchRatio*.08f) && thumbApproach(start,hand)>=.06f){
                firstPulse=true;firstPulseAt=hand.time;firstDepth=drop;trough=hand
            }else if(hand.time-checkNotNull(startedAt)>220 || drop<.015f){
                return cancel().copy(message="继续移动食指瞄准")
            }
        }else if(!reopened){
            if(hand.pinchRatio<(trough?.pinchRatio ?: Float.MAX_VALUE)){trough=hand;firstDepth=max(firstDepth,drop)}
            val low=checkNotNull(trough)
            if(hand.pinchRatio-low.pinchRatio>=max(.035f,firstDepth*.30f) && thumbOpening(low,hand)>=.04f){
                reopened=true;baseline=hand;closeAt=null
            }
        }else {
            if(hand.pinchRatio>start.pinchRatio)baseline=hand
            else if(drop>=max(.065f,start.pinchRatio*.08f) && thumbApproach(start,hand)>=.06f){
                val point=aim;resetTracking();cooldownUntil=now+450
                return GestureDecision(ActionKind.CLICK,point,point,message="靠近 2/2 · 已点击",confirmation=2)
            }
        }
        return GestureDecision(frozenCursor=aim.takeIf{firstPulse},confirmation=if(firstPulse)1 else 0,
            message=when{reopened->"靠近 1/2 · 再靠近一次点击";firstPulse->"靠近 1/2 · 稍张开，再靠近";else->"食指移动中 · 正在确认拇指动作"})
    }
}
