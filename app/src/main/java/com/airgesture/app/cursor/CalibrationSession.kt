package com.airgesture.app.cursor

enum class CalibrationStage { TOP_LEFT, BOTTOM_RIGHT, COMPLETE }

/** Captures stable medians, never saves a transient pose or silently reverses the corners. */
class CalibrationSession {
    var stage=CalibrationStage.TOP_LEFT;private set
    var collecting=false;private set
    var message="食指移动到舒适范围左上，再点记录";private set
    var result:ControlRegion?=null;private set
    private var first:CursorPoint?=null
    private val samples=ArrayDeque<Pair<Long,CursorPoint>>()
    private var startedAt=0L
    fun capture(now:Long) {
        if(stage==CalibrationStage.COMPLETE)return
        collecting=true;startedAt=now;samples.clear();message="保持整只手入镜，食指稳住约 1 秒"
    }
    fun update(point:CursorPoint?,now:Long) {
        if(!collecting)return
        if(now-startedAt>5000){collecting=false;samples.clear();message="未取得稳定位置，请重新记录";return}
        if(point==null || !point.isFinite()) {samples.clear();return}
        if(samples.lastOrNull()?.let {now-it.first>250 || now<=it.first}==true)samples.clear()
        if(samples.firstOrNull()?.let {kotlin.math.hypot(point.x-it.second.x,point.y-it.second.y)>.025f}==true)samples.clear()
        samples.add(now to point)
        if(samples.size<5 || now-samples.first().first<650)return
        val median=CursorPoint(samples.map{it.second.x}.sorted()[samples.size/2],samples.map{it.second.y}.sorted()[samples.size/2])
        collecting=false;samples.clear()
        if(stage==CalibrationStage.TOP_LEFT){first=median;stage=CalibrationStage.BOTTOM_RIGHT;message="食指移动到舒适范围右下，再点记录";return}
        val top=checkNotNull(first)
        if(median.x-top.x !in .12f.. .65f || median.y-top.y !in .12f.. .65f ||
            top.x<.05f || top.y<.05f || median.x>.95f || median.y>.95f){
            stage=CalibrationStage.TOP_LEFT;first=null;message="范围过小、过大或方向颠倒，请重新记录左上";return
        }
        result=ControlRegion(top.x,median.x,top.y,median.y);stage=CalibrationStage.COMPLETE;message="活动范围已记录"
    }
}
