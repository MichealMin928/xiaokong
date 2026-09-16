package com.airgesture.app.eye

import kotlin.math.abs
import kotlin.math.hypot

data class FacePoint(val x:Float,val y:Float,val z:Float=0f)
data class GazeSample(val time:Long,val vertical:Float,val yaw:Float,val pitch:Float,val roll:Float,val scale:Float)
enum class GazeRegion { TOP, CENTER, BOTTOM, UNKNOWN }

/** Iris position in each eye's local lid axis: roll-normalized and independent of screen mirror. */
object GazeFeatures {
    fun from(points:List<FacePoint>?,time:Long,width:Int,height:Int,now:Long=time):GazeSample? {
        if(now-time !in 0..250)return null
        if(points==null || points.size<478 || points.any{!it.x.isFinite() || !it.y.isFinite()} || width<=0 || height<=0)return null
        fun p(i:Int)=points[i].let{FacePoint(it.x*width,it.y*height,it.z)}
        fun eye(iris:Int,upper:Int,lower:Int,left:Int,right:Int):Float? {
            val a=p(upper);val b=p(lower);val c=p(iris)
            val dx=b.x-a.x;val dy=b.y-a.y;val h=hypot(dx,dy)
            val w=hypot(p(right).x-p(left).x,p(right).y-p(left).y)
            if(w<14 || h/w<.14f || h/w>.65f)return null // blink, too small or damaged mesh
            return ((c.x-a.x)*dx+(c.y-a.y)*dy)/(h*h)
        }
        val a=eye(468,159,145,33,133) ?: return null
        val b=eye(473,386,374,362,263) ?: return null
        if(a !in -.3f..1.3f || b !in -.3f..1.3f || abs(a-b)>.25f)return null
        val l=p(33);val r=p(263);val n=p(1);val chin=p(152)
        val span=hypot(r.x-l.x,r.y-l.y)
        if(span<45 || abs(r.y-l.y)/span>.3f)return null
        val midX=(l.x+r.x)/2;val midY=(l.y+r.y)/2
        val yaw=(n.x-midX)/span
        val pitch=(n.y-midY)/(chin.y-midY).coerceAtLeast(1f)
        if(abs(yaw)>.22f || pitch !in .2f.. .8f)return null
        return GazeSample(time,(a+b)/2,yaw,pitch,(r.y-l.y)/span,span/minOf(width,height))
    }
}

data class GazeCalibration(val top:Float,val center:Float,val bottom:Float,
    val yaw:Float,val pitch:Float,val roll:Float,val scale:Float) {
    val valid get()=listOf(top,center,bottom,yaw,pitch,roll,scale).all{it.isFinite()} &&
        abs(top-center)>=.035f && abs(bottom-center)>=.035f && (top-center)*(bottom-center)<0 && scale>0
    fun region(sample:GazeSample):GazeRegion {
        if(!valid || abs(sample.yaw-yaw)>.09f || abs(sample.pitch-pitch)>.09f || abs(sample.roll-roll)>.12f || sample.scale/scale !in .72f..1.4f)return GazeRegion.UNKNOWN
        val sign=if(bottom>top)1 else -1
        val value=(sample.vertical-center)*sign
        return when { value>(abs(bottom-center)*.55f)->GazeRegion.BOTTOM
            value< -abs(top-center)*.55f->GazeRegion.TOP
            else->GazeRegion.CENTER }
    }
}

class GazeCalibrator {
    var stage=GazeRegion.TOP;private set
    var message="看顶部标记，保持头部稳定";private set
    var result:GazeCalibration?=null;private set
    private var stageAt=0L
    private var lastTime=0L
    private val samples=ArrayDeque<GazeSample>()
    private val recorded=mutableMapOf<GazeRegion,GazeSample>()
    fun update(sample:GazeSample?,now:Long) {
        if(result!=null)return
        if(stageAt==0L)stageAt=now
        if(sample==null || now-sample.time !in 0..250){samples.clear();return}
        if(lastTime>0 && sample.time-lastTime !in 1..300)samples.clear()
        lastTime=sample.time
        if(now-stageAt<1500)return // time to move eyes to the new marker
        samples.addLast(sample)
        while(samples.isNotEmpty() && now-samples.first().time>1200)samples.removeFirst()
        if(samples.size<8 || now-samples.first().time<850)return
        fun median(f:(GazeSample)->Float)=samples.map(f).sorted().let{it[it.size/2]}
        val median=median{it.vertical}
        if(samples.count{abs(it.vertical-median)>.045f}>samples.size/5 || samples.any{abs(it.yaw-sample.yaw)>.05f || abs(it.pitch-sample.pitch)>.05f}){
            message="视线或头部在动，请看住当前标记";return
        }
        recorded[stage]=GazeSample(now,median,median{it.yaw},median{it.pitch},median{it.roll},median{it.scale})
        samples.clear();stageAt=now
        if(stage==GazeRegion.BOTTOM){
            val c=recorded.getValue(GazeRegion.CENTER)
            val calibration=GazeCalibration(recorded.getValue(GazeRegion.TOP).vertical,c.vertical,recorded.getValue(GazeRegion.BOTTOM).vertical,c.yaw,c.pitch,c.roll,c.scale)
            val samePose=recorded.values.all{abs(it.yaw-c.yaw)<.055f && abs(it.pitch-c.pitch)<.055f && abs(it.roll-c.roll)<.08f && it.scale/c.scale in .9f..1.1f}
            if(calibration.valid && samePose){result=calibration;message="校准完成，试着依次看上、中、下"}
            else{stage=GazeRegion.TOP;recorded.clear();message="上下视线差异不足，请保持头部不动重新校准"}
        }else{stage=if(stage==GazeRegion.TOP)GazeRegion.CENTER else GazeRegion.BOTTOM;message=if(stage==GazeRegion.CENTER)"看中间标记" else "看底部标记"}
    }
}

class GazeReader(var dwellMs:Long=550) {
    var region=GazeRegion.UNKNOWN;private set
    var message="先看中间，开始阅读";private set
    private var since=0L
    private var lastTime=0L
    private var armed=false
    private var bottomAt:Long?=null
    private var cooldownUntil=0L
    fun reset(){region=GazeRegion.UNKNOWN;since=0;lastTime=0;armed=false;bottomAt=null;message="先看中间，开始阅读"}
    fun update(value:GazeRegion,time:Long,now:Long=time):Boolean {
        if(now-time !in 0..250 || value==GazeRegion.UNKNOWN || (lastTime>0 && time-lastTime !in 1..350)){reset();return false}
        lastTime=time
        if(region!=value){region=value;since=time}
        if(time<cooldownUntil)return false
        if(bottomAt!=null && time-checkNotNull(bottomAt)>10_000){bottomAt=null;armed=false}
        if(time-since<dwellMs)return false
        when(value){
            GazeRegion.CENTER->{armed=true;if(bottomAt==null)message="阅读中 · 看到底部可准备翻页"}
            GazeRegion.BOTTOM->if(armed){bottomAt=bottomAt ?: time;message="准备翻页 · 视线回顶部"}
            GazeRegion.TOP->if(armed && bottomAt!=null){armed=false;bottomAt=null;cooldownUntil=time+1800;message="已请求下一页 · 回中间继续";return true}
            else->Unit
        }
        return false
    }
}
