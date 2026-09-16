package com.airgesture.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.airgesture.app.service.GlobalSession

/** One explicit dislike command: open content menu, find exact action labels, then click once.
 * No background node scans, page-text logging, screenshots or network requests. */
internal class SocialActionDispatcher(private val service:AirAccessibilityService){
    private val handler=Handler(Looper.getMainLooper())
    private var finish:((ActionResult)->Unit)?=null
    fun cancel(){handler.removeCallbacksAndMessages(null);finish?.invoke(ActionResult.CANCELLED);finish=null}
    fun dislike(action:SystemAction,callback:(ActionResult)->Unit,ownProof:Boolean=false):Boolean {
        val pkg=AccessibilityBridge.foregroundPackage
        if((pkg !in supported && !(ownProof && pkg==service.packageName)) || finish!=null)return false
        finish=callback
        val start=SystemClock.uptimeMillis()
        fun complete(result:ActionResult){val cb=finish;finish=null;handler.removeCallbacksAndMessages(null);cb?.invoke(result)}
        fun available()=GlobalSession.running && GlobalSession.actionsEnabled && GlobalSession.status.value?.paused!=true && AccessibilityBridge.foregroundPackage==pkg &&
            service.getSystemService(android.os.PowerManager::class.java).isInteractive && !service.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        fun inspect(){
            if(!available()){complete(ActionResult.CANCELLED);return}
            val root=service.rootInActiveWindow
            if(root?.packageName?.toString()==pkg){
                // A second menu label distinguishes a menu from an ordinary caption using the same words.
                val menuVisible=listOf("举报","复制链接","分享").any{label->root.findAccessibilityNodeInfosByText(label).any{it.isVisibleToUser && it.text?.toString()?.trim()==label}}
                if(menuVisible)for(label in listOf("不感兴趣","不喜欢","减少此类推荐")){
                    for(node in root.findAccessibilityNodeInfosByText(label)){
                        if(!node.isVisibleToUser || node.text?.toString()?.trim()!=label)continue
                        var target:AccessibilityNodeInfo?=node
                        repeat(4){
                            val current=target
                            if(current!=null && current.isEnabled && current.isClickable){
                                complete(if(current.performAction(AccessibilityNodeInfo.ACTION_CLICK))ActionResult.COMPLETED else ActionResult.REJECTED);return
                            }
                            target=current?.parent
                        }
                    }
                }
            }
            if(SystemClock.uptimeMillis()-start>=2600){complete(ActionResult.REJECTED);return}
            handler.postDelayed({inspect()},100)
        }
        val path=Path().apply{moveTo(action.screen.width*.5f,action.screen.height*.5f)}
        val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,650)).build()
        val accepted=service.dispatchGesture(gesture,object:AccessibilityService.GestureResultCallback(){
            override fun onCompleted(gestureDescription:GestureDescription?){handler.postDelayed({inspect()},150)}
            override fun onCancelled(gestureDescription:GestureDescription?){complete(ActionResult.CANCELLED)}
        },handler)
        if(!accepted){finish=null;return false}
        return true
    }
    companion object {val supported=setOf("com.xingin.xhs","com.ss.android.ugc.aweme","com.smile.gifmaker","tv.danmaku.bili")}
}
