package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import kotlin.math.abs

/** Pose selects allowed directions. Each new stroke can start anywhere, without a return-position latch. */
class FingerPagingController {
    private enum class Pose { FOUR_UP, TWO_UP, SIDEWAYS, NONE }
    private var pose=Pose.NONE
    private var poseAt=0L
    private var lastTime:Long?=null
    private var last:HandObservation?=null
    private var cooldownUntil=0L
    private val samples=ArrayDeque<HandObservation>()
    var status="四指向上滑 · 两指向下滑";private set
    var ready=false;private set
    fun reset(){breakTracking();cooldownUntil=0L}
    fun breakTracking(){pose=Pose.NONE;lastTime=null;last=null;samples.clear();ready=false}
    fun update(hand:HandObservation?,now:Long):GestureDecision {
        if(hand==null || !hand.valid || now-hand.time !in 0..250){breakTracking();status="请把手移回镜头";return GestureDecision(message=status)}
        if(lastTime?.let{hand.time-it !in 1..250}==true)breakTracking()
        lastTime=hand.time
        val next=when{
            hand.twoFingerPose && hand.fingerDirection==FingerDirection.UP->Pose.TWO_UP
            hand.fourFingerPose && hand.fingerDirection==FingerDirection.UP->Pose.FOUR_UP
            hand.fourFingerPose && hand.fingerDirection in listOf(FingerDirection.LEFT,FingerDirection.RIGHT)->Pose.SIDEWAYS
            else->Pose.NONE
        }
        if(next!=pose){pose=next;poseAt=hand.time;samples.clear();last=null;ready=false}
        if(pose==Pose.NONE){samples.clear();last=null;ready=false;status="指尖都朝上 · 四指上移 / 两指下移";return GestureDecision(message=status)}
        ready=hand.time-poseAt>=100
        // Reversal is simply the next stroke's baseline. No fixed origin or stationary wait is required.
        last?.let{previous->
            val dx=hand.palm.x-previous.palm.x;val dy=hand.palm.y-previous.palm.y
            val step=direction(dx,dy)
            if(step!=null && abs(dx)+abs(dy)>.012f && actionFor(step,hand)==null)samples.clear()
        }
        last=hand
        samples.addLast(hand)
        while(samples.size>1 && hand.time-samples.first().time>650)samples.removeFirst()
        status=when(pose){Pose.FOUR_UP->"四指上滑 · 五指张开左移";Pose.TWO_UP->"两指朝上 · 下移或右移";Pose.SIDEWAYS->"侧向横推返回";else->"等待手形"}
        if(!ready)return GestureDecision(message=status)
        for(first in samples){
            val dt=(hand.time-first.time)/1000f
            if(dt<.08f)continue
            val dx=hand.palm.x-first.palm.x;val dy=hand.palm.y-first.palm.y
            val direction=direction(dx,dy) ?: continue
            val travel=if(direction in listOf(SwipeDirection.UP,SwipeDirection.DOWN))abs(dy) else abs(dx)
            if(travel<.09f || travel/dt<.15f)continue
            val path=samples.filter{it.time>=first.time}.zipWithNext().sumOf{(a,b)->distance(a.palm,b.palm).toDouble()}.toFloat()
            if(path>0 && distance(first.palm,hand.palm)/path<.82f)continue
            // Opening the thumb cannot turn earlier four-finger travel into a left command.
            // Keep vertical history intact when the thumb opens or closes.
            if(direction==SwipeDirection.LEFT && pose==Pose.FOUR_UP &&
                samples.any{it.time>=first.time && !it.fiveFingerPose})continue
            val action=actionFor(direction,hand)
            samples.clear();samples.add(hand)
            if(action!=null && now>=cooldownUntil){
                cooldownUntil=now+320
                status="已滑动 · 继续挥动即可"
                return GestureDecision(action,message=status)
            }
            return GestureDecision(message=status)
        }
        return GestureDecision(message=status)
    }
    private fun direction(dx:Float,dy:Float):SwipeDirection?=when{
        abs(dy)>abs(dx)*1.8f->if(dy<0)SwipeDirection.UP else SwipeDirection.DOWN
        abs(dx)>abs(dy)*1.8f->if(dx<0)SwipeDirection.LEFT else SwipeDirection.RIGHT
        else->null
    }
    private fun actionFor(direction:SwipeDirection,hand:HandObservation):ActionKind?=when(direction){
        SwipeDirection.UP->ActionKind.SWIPE_UP.takeIf{pose==Pose.FOUR_UP}
        SwipeDirection.DOWN->ActionKind.SWIPE_DOWN.takeIf{pose==Pose.TWO_UP}
        SwipeDirection.LEFT->when(pose){Pose.FOUR_UP->ActionKind.SWIPE_LEFT.takeIf{hand.fiveFingerPose};Pose.SIDEWAYS->ActionKind.BACK;else->null}
        SwipeDirection.RIGHT->when(pose){Pose.TWO_UP->ActionKind.SWIPE_RIGHT;Pose.SIDEWAYS->ActionKind.BACK;else->null}
    }
}
