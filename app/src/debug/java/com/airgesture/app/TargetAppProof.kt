package com.airgesture.app

import android.content.Intent
import android.os.*
import androidx.core.content.ContextCompat
import com.airgesture.app.command.*
import com.airgesture.app.mode.AssistantMode
import com.airgesture.app.service.*
import com.airgesture.app.session.*
import com.airgesture.app.voice.*
import com.airgesture.app.accessibility.AccessibilityBridge
import org.json.*
import java.io.File

/** Basic navigation only in user-requested apps. No clicks, likes, comments or text entry. */
internal class TargetAppProof(private val activity:ActionTestActivity){
    private val handler=Handler(Looper.getMainLooper());private val samples=JSONArray();private val runId=java.util.UUID.randomUUID().toString()
    private val packages=listOf("com.ss.android.ugc.aweme","com.xingin.xhs","tv.danmaku.bili","com.heytap.browser","com.android.settings","com.android.launcher")
    private var index=0;private var done=false
    fun start(){
        activity.stopService(Intent(activity,GestureForegroundService::class.java))
        handler.postDelayed({ContextCompat.startForegroundService(activity,Intent(activity,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
            .putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name).putExtra(GestureForegroundService.EXTRA_CONTROL,true).putExtra(GestureForegroundService.EXTRA_VOICE,true))
            handler.postDelayed({next()},3000)},700)
    }
    private fun later(ms:Long,block:()->Unit){handler.postDelayed({if(!done)try{block()}catch(e:Exception){finish(false,e.message ?: "error")}},ms)}
    private fun command(kind:CommandKind){check(ControlSessionState.request(ControlCommand(kind,SystemClock.uptimeMillis())))}
    private fun next(){
        if(index>=packages.size){val ok=(0 until samples.length()).all{i->val r=samples.getJSONObject(i);r.optString("status")=="NOT_INSTALLED" || (listOf("up","down","back","home").all{r.optString(it)=="COMPLETED"} && r.optString("after_home")=="com.android.launcher")};finish(ok,"Basic system callbacks in requested installed apps; commands synthetic, no human acoustic/hand accuracy claim");return}
        val p=packages[index++];val launch=if(p=="com.android.launcher")Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(p) else activity.packageManager.getLaunchIntentForPackage(p)
        if(launch==null){samples.put(JSONObject().put("package",p).put("status","NOT_INSTALLED"));next();return}
        activity.startActivity(launch)
        later(3000){
            val currentPackage=AccessibilityBridge.proofActiveWindowPackage()
            val row=JSONObject().put("package",p).put("foreground",currentPackage)
            if(currentPackage!=p){row.put("status","FOREGROUND_MISMATCH");samples.put(row);next();return@later}
            command(CommandKind.SWIPE_UP)
            later(1000){row.put("up",ControlSessionState.current.lastDecision);command(CommandKind.SWIPE_DOWN)
                later(1000){row.put("down",ControlSessionState.current.lastDecision);command(CommandKind.BACK)
                    later(1200){row.put("back",ControlSessionState.current.lastDecision).put("after_back",AccessibilityBridge.foregroundPackage);command(CommandKind.HOME)
                        later(1200){row.put("home",ControlSessionState.current.lastDecision).put("after_home",AccessibilityBridge.proofActiveWindowPackage()).put("last_window_event_package",AccessibilityBridge.foregroundPackage).put("camera_active",ControlSessionState.current.cameraBound)
                            samples.put(row);write(false,"RUNNING");next()
                        }
                    }
                }
            }
        }
    }
    private fun write(passed:Boolean,message:String){File(activity.filesDir,"proof-target-apps.json").writeText(JSONObject().put("run_id",runId).put("passed",passed).put("message",message).put("samples",samples).toString(2))}
    private fun finish(passed:Boolean,message:String){done=true;handler.removeCallbacksAndMessages(null);write(passed,message);activity.stopService(Intent(activity,GestureForegroundService::class.java))}
}
