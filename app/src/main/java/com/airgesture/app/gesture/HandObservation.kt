package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint
import kotlin.math.hypot
import kotlin.math.abs

enum class ThumbPose { TOGETHER, OPEN, UNKNOWN }
enum class ThumbCommandPose { NONE, UP, DOWN }
enum class FingerDirection { UP, DOWN, LEFT, RIGHT, UNKNOWN }

data class HandObservation(val time: Long, val cursor: CursorPoint, val palm: CursorPoint,
    val pinchRatio: Float, val openPalm: Boolean, val fist: Boolean,
    val palmSize: Float = .2f, val boxArea: Float = .12f, val palmFacing: Float = 0f,
    val swipePose: Boolean = openPalm,
    val pointerPose:Boolean=false,
    val pagePose:Boolean=swipePose,
    val thumbPose:ThumbPose=ThumbPose.UNKNOWN,
    val homePose:Boolean=false,
    val thumbCommand:ThumbCommandPose=ThumbCommandPose.NONE,
    val fingerDirection:FingerDirection=FingerDirection.UNKNOWN,
    val thumbLocal:CursorPoint?=null,
    val thumbTip:CursorPoint?=null,
    val thumbOnly:Boolean=thumbCommand!=ThumbCommandPose.NONE,
    val fourFingerPose:Boolean=pagePose,
    val twoFingerPose:Boolean=false,
    val indexLocal:CursorPoint?=null,
    val fiveFingerPose:Boolean=false,
    val pointerContinuationPose:Boolean=pointerPose) {
    val valid get() = cursor.isFinite() && palm.isFinite() && pinchRatio.isFinite() && pinchRatio >= 0 &&
        palmSize.isFinite() && palmSize > 0 && boxArea.isFinite() && boxArea > 0

    companion object {
        /** Geometric cues, not model confidence. Input must first pass HandTrackingGate. */
        fun from(points: List<CursorPoint>, width: Int, height: Int, time: Long, cursor: CursorPoint): HandObservation? {
            if (points.size != 21 || points.any { !it.isFinite() } || width <= 0 || height <= 0) return null
            fun d(a: Int, b: Int) = hypot((points[a].x-points[b].x)*width, (points[a].y-points[b].y)*height)
            val size = d(0, 9)
            if (size < 12) return null
            val ratios = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).map { (tip,pip) -> d(tip,0) / d(pip,0).coerceAtLeast(1f) }
            val pinch = d(4,8) / size
            val center = listOf(0,5,9,13,17).map { points[it] }
            val area = (points.maxOf { it.x } - points.minOf { it.x }) * (points.maxOf { it.y } - points.minOf { it.y })
            val ix=(points[8].x-points[5].x)*width;val iy=(points[8].y-points[5].y)*height
            val tx=(points[4].x-points[5].x)*width;val ty=(points[4].y-points[5].y)*height
            val thumbSpread=abs(ix*ty-iy*tx)/hypot(ix,iy).coerceAtLeast(1f)/d(5,17).coerceAtLeast(1f)
            val thumbExtended=d(4,2)/d(3,2).coerceAtLeast(1f)>1.45f && d(4,5)/d(5,17).coerceAtLeast(1f)>.35f
            val home=thumbExtended && ratios[3]>1.10f && ratios.take(3).all{it<1.08f}
            val thumbDx=(points[4].x-points[2].x)*width;val thumbDy=(points[4].y-points[2].y)*height
            val thumbCommand=if(thumbExtended && ratios.all{it<1.08f})when{
                thumbDy < -abs(thumbDx)*1.2f->ThumbCommandPose.UP
                thumbDy > abs(thumbDx)*1.2f->ThumbCommandPose.DOWN
                else->ThumbCommandPose.NONE
            }else ThumbCommandPose.NONE
            val four=ratios.all{it>1.08f}
            val two=ratios.take(2).all{it>1.10f} && ratios.drop(2).all{it<1.08f}
            val fingers=if(two)listOf(8 to 5,12 to 9) else listOf(8 to 5,12 to 9,16 to 13,20 to 17)
            val fx=fingers.map{(tip,base)->(points[tip].x-points[base].x)*width}.average().toFloat()
            val fy=fingers.map{(tip,base)->(points[tip].y-points[base].y)*height}.average().toFloat()
            val direction=when{
                fy < -abs(fx)*1.8f->FingerDirection.UP
                fy > abs(fx)*1.8f->FingerDirection.DOWN
                fx < -abs(fy)*1.8f->FingerDirection.RIGHT // mirror with the displayed cursor
                fx > abs(fy)*1.8f->FingerDirection.LEFT
                else->FingerDirection.UNKNOWN
            }
            // Palm-local thumb motion is invariant to left/right hand, translation and in-plane rotation.
            val bx=(points[17].x-points[5].x)*width;val by=(points[17].y-points[5].y)*height
            val span2=(bx*bx+by*by).coerceAtLeast(1f)
            val local=CursorPoint((tx*bx+ty*by)/span2,(tx* -by+ty*bx)/span2)
            return HandObservation(time,cursor,CursorPoint(1-center.map { it.x }.average().toFloat(),center.map { it.y }.average().toFloat()),
                pinch,ratios.all { it > 1.15f } && pinch > .5f,
                ratios.all { it < 1.02f } && pinch > .5f && d(4,5) / size < .85f,
                size / minOf(width,height),area,
                ((points[5].x-points[0].x)*(points[17].y-points[0].y)-(points[5].y-points[0].y)*(points[17].x-points[0].x)) / area.coerceAtLeast(.001f),
                // A relaxed little finger must not cancel a palm stroke. Pointing and pinching remain excluded.
                ratios.count { it > 1.08f } >= 3 && pinch > .5f,
                ratios[0]>1.10f && ratios.drop(1).all{it<1.08f},
                four || two,
                when {thumbSpread<=.28f->ThumbPose.TOGETHER;thumbSpread>=.50f->ThumbPose.OPEN;else->ThumbPose.UNKNOWN},home,thumbCommand,
                direction,local,CursorPoint(1-points[4].x,points[4].y),thumbExtended && ratios.all{it<1.08f},four,two,
                CursorPoint((ix*bx+iy*by)/span2,(ix* -by+iy*bx)/span2),four && thumbExtended,
                ratios[0]>1.03f && ratios.drop(1).all{it<1.08f})
        }
    }
}

internal fun distance(a: CursorPoint, b: CursorPoint) = hypot(a.x-b.x,a.y-b.y)
