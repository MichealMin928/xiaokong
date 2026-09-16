package com.airgesture.app

import android.content.Intent
import android.media.AudioManager
import android.os.*
import com.airgesture.app.service.*
import com.airgesture.app.session.*
import com.airgesture.app.ui.SessionStarter
import com.airgesture.app.voice.*
import org.json.*

/** Rapid settings changes while recording, no injected speech or system actions. */
internal class AudioRestartProof(private val activity:ActionTestActivity){
    private val handler=Handler(Looper.getMainLooper())
    private val store=VoiceSettings(activity)
    private val original=store.load()
    private var done=false
    private val results=JSONArray()
    fun start(){
        activity.stopService(Intent(activity,GestureForegroundService::class.java))
        handler.postDelayed({
            store.save(original.copy(activation=VoiceActivation.DIRECT))
            if(!SessionStarter.channels(activity,true,false)){finish(false,"Start gate rejected");return@postDelayed}
            waitActive(SystemClock.uptimeMillis()+10000){burst(0)}
        },1200)
    }
    private fun waitActive(deadline:Long,next:()->Unit){
        if(done)return
        if(!activity.hasWindowFocus()){finish(false,"Own fixture left foreground");return}
        val s=ControlSessionState.current
        if(s.audio.state=="ACTIVE" && s.audio.audioMs>1000 && s.mode!=ControlMode.PAUSED){next();return}
        if(SystemClock.uptimeMillis()>deadline){finish(false,"Audio recovery deadline: ${s.audio.state} / ${s.message}");return}
        handler.postDelayed({waitActive(deadline,next)},150)
    }
    private fun burst(index:Int){
        if(done)return
        if(index<12){
            store.save(original.copy(activation=if(index%2==0)VoiceActivation.WAKE_WORD else VoiceActivation.DIRECT))
            handler.postDelayed({burst(index+1)},45)
        }else waitActive(SystemClock.uptimeMillis()+15000){
            val s=ControlSessionState.current
            val n=activity.getSystemService(AudioManager::class.java).activeRecordingConfigurations.size
            results.put(JSONObject().put("changes",12).put("active_recorders",n).put("decoder",s.audio.decoder).put("mode",s.mode.name).put("audio_ms",s.audio.audioMs))
            finish(n==1 && s.audio.decoder=="UTTERANCE","Rapid recorder handoff finished; command-free real audio resource test")
        }
    }
    private fun finish(passed:Boolean,message:String){if(done)return;done=true;handler.removeCallbacksAndMessages(null)
        activity.stopService(Intent(activity,GestureForegroundService::class.java));store.save(original)
        java.io.File(activity.filesDir,"proof-audio-restart.json").writeText(JSONObject().put("passed",passed).put("message",message).put("results",results).toString(2))
        activity.target.text=message;activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun close(){if(!done)finish(false,"Fixture closed")}
}
