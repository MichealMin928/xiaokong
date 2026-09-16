package com.airgesture.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import com.airgesture.app.cursor.CursorPoint

/** One continued stroke at a time. Missing frames/session exit release the existing contact only. */
internal class DragDispatcher(private val service:AccessibilityService) {
    private val handler=Handler(Looper.getMainLooper())
    private var stroke:GestureDescription.StrokeDescription?=null
    private var endpoint:CursorPoint?=null
    private var busy=false
    private var releaseRequested=false
    private var geometry:ScreenGeometry?=null
    val active get()=stroke!=null || busy
    private val expiry=Runnable{release()}
    fun submit(action:SystemAction,callback:(ActionResult)->Unit):Boolean {
        if(action.kind==ActionKind.DRAG_END){release();callback(ActionResult.COMPLETED);return true}
        if(busy || releaseRequested)return false
        val point=action.point ?: return false
        if(!action.screen.contains(point))return false
        if(action.kind==ActionKind.DRAG_START){if(active)return false;geometry=action.screen}
        else if(stroke==null || geometry!=action.screen){release();return false}
        val previous=stroke
        val origin=endpoint ?: point
        val path=Path().apply{moveTo(origin.x,origin.y);lineTo(point.x,point.y)}
        val next=previous?.continueStroke(path,0,60,true) ?: GestureDescription.StrokeDescription(path,0,60,true)
        return send(next,point,callback)
    }
    private fun send(next:GestureDescription.StrokeDescription,point:CursorPoint,callback:(ActionResult)->Unit):Boolean {
        busy=true
        handler.removeCallbacks(expiry)
        val accepted=service.dispatchGesture(GestureDescription.Builder().addStroke(next).build(),object:AccessibilityService.GestureResultCallback(){
            override fun onCompleted(gestureDescription:GestureDescription?){
                busy=false;stroke=next.takeIf{it.willContinue()};endpoint=point
                callback(ActionResult.COMPLETED)
                if(stroke==null){clear();return}
                if(releaseRequested)release() else handler.postDelayed(expiry,250)
            }
            override fun onCancelled(gestureDescription:GestureDescription?){clear();callback(ActionResult.CANCELLED)}
        },handler)
        if(!accepted){clear();return false}
        return true
    }
    fun release(){
        handler.removeCallbacks(expiry)
        if(busy){releaseRequested=true;return}
        val previous=stroke ?: run{clear();return}
        val point=endpoint ?: run{clear();return}
        releaseRequested=true
        val path=Path().apply{moveTo(point.x,point.y)}
        send(previous.continueStroke(path,0,1,false),point){ }
    }
    private fun clear(){handler.removeCallbacks(expiry);stroke=null;endpoint=null;busy=false;releaseRequested=false;geometry=null}
}
