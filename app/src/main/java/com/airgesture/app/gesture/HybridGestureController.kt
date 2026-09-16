package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.mode.AppProfile
import com.airgesture.app.mode.ModeRouter
import com.airgesture.app.settings.ActionSettings

data class HybridDecision(val gesture:GestureDecision,val pointer:Boolean=false,val showPointer:Boolean=pointer){
    fun mapped(profile:AppProfile?):Pair<ActionKind,CursorPoint?>? {
        val kind=gesture.action ?: return null
        // A thumb confirmation clicks its aimed position; page mappings never replace it with screen center.
        if(pointer)return kind to gesture.point
        if(kind in listOf(ActionKind.BACK,ActionKind.HOME,ActionKind.LIKE,ActionKind.DISLIKE,ActionKind.SWIPE_LEFT,ActionKind.SWIPE_RIGHT))return kind to null
        return ModeRouter.map(kind,profile ?: AppProfile("","系统操作"))?.kind?.let{it to CursorPoint(.5f,.5f)}
    }
}

/** Exclusive pose channels. Motion is interpreted only inside the selected channel. */
class HybridGestureController {
    private val mouse=ThumbTapPointerController()
    private val pages=FingerPagingController()
    private val fists=FistDetector()
    private val thumbs=DoubleThumbDetector()
    private var settings=ActionSettings()
    private var configured=false
    private var homeSince:Long?=null
    private var homeAnchor:CursorPoint?=null
    private var homeConsumed=false
    private var homeReleasedAt:Long?=null
    private var candidateAt:Long?=null
    private var candidatePalm:CursorPoint?=null
    private var lastTime:Long?=null
    private var lastPointerAt=0L
    private var lastPointerPoint:CursorPoint?=null
    var pointer=false;private set
    var paused=false;private set
    var status="伸食指用鼠标 · 张手翻页";private set
    val ready get()=if(pointer)mouse.ready else pages.ready
    fun configure(value:ActionSettings){
        if(configured && value==settings)return
        configured=true
        settings=value
        mouse.dragEnabled=value.drag
        pointer=false;pages.reset();resetTracking()
    }
    fun setPaused(value:Boolean){paused=value;mouse.resetTracking();resetTracking()}
    fun resetTracking(){mouse.resetTracking();pages.breakTracking();thumbs.breakTracking();fists.reset();candidateAt=null;candidatePalm=null;lastTime=null;homeSince=null;homeAnchor=null;homeReleasedAt=null;lastPointerPoint=null}
    fun update(hand:HandObservation?,now:Long):HybridDecision {
        if(hand==null || !hand.valid || now-hand.time !in 0..250){
            val end=mouse.update(null,now);pages.update(null,now);fists.reset();candidateAt=null;candidatePalm=null;lastTime=null
            homeSince=null;homeAnchor=null;homeReleasedAt=null;thumbs.reset()
            val visible=pointer && !paused && end.action==null && lastPointerPoint!=null && now-lastPointerAt in 0..180
            status="整只手移回镜头";return HybridDecision(end.copy(message=status,frozenCursor=lastPointerPoint.takeIf{visible}),pointer,visible)
        }
        if(lastTime?.let{hand.time-it !in 1..250}==true){
            val end=mouse.cancel();resetTracking();thumbs.reset()
            if(end.action!=null)return HybridDecision(end,true,false)
        }
        lastTime=hand.time
        // A marginal index-extension frame may still move an already visible mouse.
        // It cannot enter mouse mode, confirm a click, or mask a distinct command pose.
        if(pointer && !mouse.busy && !hand.pointerPose && hand.pointerContinuationPose &&
            !hand.pagePose && !hand.fist && !hand.homePose && !hand.thumbOnly &&
            lastPointerPoint!=null && now-lastPointerAt in 0..180){
            mouse.cancel();pages.breakTracking();fists.reset();thumbs.reset();candidateAt=null
            status="鼠标 · 食指移动中"
            lastPointerPoint=hand.cursor
            return HybridDecision(GestureDecision(message=status),true,true)
        }
        // During an established confirmation, fingertip contact can bend the index.
        // Keep this short transition inside the mouse channel, rather than starting a thumb command.
        if(pointer && mouse.busy && hand.pinchRatio<.52f && !hand.pagePose && !hand.homePose){
            pages.breakTracking();fists.reset();thumbs.reset();candidateAt=null
            val decision=mouse.update(hand.copy(pointerPose=true,fist=false,thumbOnly=false),now)
            status="鼠标 · ${decision.message}"
            lastPointerAt=now;lastPointerPoint=decision.frozenCursor ?: hand.cursor
            return HybridDecision(decision.copy(message=status),true,true)
        }
        if(hand.pagePose || hand.fist || hand.homePose || hand.thumbOnly)lastPointerPoint=null
        val thumbSequence=hand.thumbOnly || thumbs.inProgress(hand.time)
        if(thumbSequence)fists.reset()
        if(settings.fistPause && !thumbSequence && fists.update(hand)){
            paused=!paused;mouse.resetTracking();resetTracking()
            status=if(paused)"已暂停 · 张手再握拳恢复" else "已恢复 · 伸食指或张手"
            return HybridDecision(GestureDecision(pauseChanged=true,message=status),pointer)
        }
        if(paused){status="已暂停 · 握拳恢复";return HybridDecision(GestureDecision(message=status),pointer,false)}
        val thumbAction=thumbs.update(hand)
        if(hand.thumbOnly){
            val end=mouse.cancel();pages.breakTracking();candidateAt=null
            if(end.action!=null)return HybridDecision(end,true,false)
            status=thumbs.status
            return HybridDecision(GestureDecision(thumbAction,message=status))
        }
        if(hand.homePose){
            homeReleasedAt=null;candidateAt=null;val end=mouse.cancel();pages.breakTracking()
            if(end.action!=null)return HybridDecision(end,true,false)
            if(!homeConsumed){
                if(homeSince==null || homeAnchor?.let{distance(it,hand.palm)>.06f}==true){homeSince=hand.time;homeAnchor=hand.palm}
                if(hand.time-checkNotNull(homeSince)>=500){homeConsumed=true;status="已返回桌面 · 请放松手指";return HybridDecision(GestureDecision(ActionKind.HOME,message=status))}
            }
            status=if(homeConsumed)"已返回桌面 · 请放松手指" else "手势 6 保持 · 返回桌面"
            return HybridDecision(GestureDecision(message=status))
        }else {
            homeSince=null;homeAnchor=null
            if(homeReleasedAt==null)homeReleasedAt=hand.time
            if(hand.time-checkNotNull(homeReleasedAt)>=200)homeConsumed=false
        }
        if(hand.fist){val end=mouse.update(hand,now);pages.breakTracking();candidateAt=null;status="握拳保持，准备暂停";return HybridDecision(end.copy(message=status),pointer,false)}
        val wantsSwitch=if(pointer)hand.pagePose && !mouse.busy else hand.pointerPose
        if(wantsSwitch){
            if(candidateAt==null || candidatePalm?.let{distance(it,hand.palm)>.05f}==true){candidateAt=hand.time;candidatePalm=hand.palm}
            if(hand.time-checkNotNull(candidateAt)>=if(pointer)300 else 180){
                pointer=!pointer;mouse.resetTracking();pages.breakTracking();candidateAt=null;candidatePalm=null
            }
        }else {candidateAt=null;candidatePalm=null}
        // Never run page recognition during a candidate pointing pose, or vice versa.
        val decision=if(pointer)mouse.update(hand,now) else if(hand.pointerPose){pages.breakTracking();GestureDecision(message="食指稳住，进入鼠标")} else pages.update(hand,now)
        status=if(pointer)"鼠标 · ${decision.message}" else decision.message
        val visible=pointer && (hand.pointerPose || (!hand.pagePose && (decision.frozenCursor!=null || decision.action in listOf(ActionKind.DRAG_START,ActionKind.DRAG_MOVE,ActionKind.DRAG_END))))
        if(visible){lastPointerAt=now;lastPointerPoint=decision.frozenCursor ?: hand.cursor}
        val grace=!visible && pointer && lastPointerPoint!=null && now-lastPointerAt in 0..180 && !hand.pagePose && !hand.fist && !hand.homePose && !hand.thumbOnly
        return HybridDecision(decision.copy(message=status,frozenCursor=if(grace)lastPointerPoint else decision.frozenCursor),pointer,visible || grace)
    }
}
