package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import kotlin.math.abs

/** Extended thumb: two physical strokes in the same direction, with a return between them. */
class DoubleThumbDetector {
    private var origin:CursorPoint?=null
    private var restAt=0L
    private var firstAt=0L
    private var lastTime:Long?=null
    private var releaseAt:Long?=null
    private var direction=0
    private var stage=0 // rest, first stroke, return, second stroke, consumed
    var status="伸出拇指，连续上移或下移两次";private set
    fun inProgress(time:Long)=stage in 2..3 && time-firstAt<=2200
    fun reset(){origin=null;lastTime=null;direction=0;stage=0;releaseAt=null}
    fun breakTracking(){if(stage!=4)reset() else {lastTime=null;releaseAt=null}}
    fun update(hand:HandObservation):ActionKind? {
        if(lastTime?.let{hand.time-it !in 1..250}==true)reset()
        lastTime=hand.time
        if(!hand.thumbOnly || hand.thumbTip?.isFinite()!=true){
            if(releaseAt==null)releaseAt=hand.time
            if(hand.time-checkNotNull(releaseAt)>=250)reset()
            return null
        }
        releaseAt=null
        val tip=checkNotNull(hand.thumbTip)
        if(stage==4){status="已识别 · 放松拇指后再操作";return null}
        if(stage in 2..3 && hand.time-firstAt>2200){stage=4;status="两次动作超时 · 放松后重试";return null}
        val anchor=origin
        if(stage==0 || anchor==null){
            if(anchor==null || distance(anchor,tip)>.025f){origin=tip;restAt=hand.time}
            else if(hand.time-restAt>=140)stage=1
            status="拇指稳住，准备两次移动";return null
        }
        val dx=tip.x-anchor.x;val dy=tip.y-anchor.y
        if(abs(dx)>.055f){stage=4;status="请沿上下方向移动拇指";return null}
        if(stage==1 && abs(dy)>=.065f && abs(dy)>abs(dx)*2){
            direction=if(dy<0)-1 else 1;firstAt=hand.time;stage=2
            status="第一次已识别 · 收回再移一次";return null
        }
        if(stage==2 && abs(dy)<=.022f){stage=3;status="再向同一方向移动一次";return null}
        if(stage==3 && dy*direction>=.065f && abs(dy)>abs(dx)*2){
            stage=4;status=if(direction<0)"两次上移 · 点赞" else "两次下移 · 不喜欢"
            return if(direction<0)ActionKind.LIKE else ActionKind.DISLIKE
        }
        return null
    }
}
