package com.airgesture.app

import android.content.Intent
import android.media.AudioManager
import android.os.*
import com.airgesture.app.command.*
import com.airgesture.app.service.*
import com.airgesture.app.session.*
import com.airgesture.app.ui.SessionStarter
import com.airgesture.app.voice.*
import org.json.*
import java.io.File

/** Real resource lifecycle; synthetic commands are confined to this DUMP-protected fixture. */
internal class ChannelProof(private val activity:ActionTestActivity){
    private val handler=Handler(Looper.getMainLooper())
    private val rows=JSONArray()
    private var stage=0
    private var done=false
    private var lastFrames=0L
    private var begin=0L
    private val store=VoiceSettings(activity)
    private val original=store.load()
    fun start(){
        activity.stopService(Intent(activity,GestureForegroundService::class.java))
        store.save(original.copy(activation=VoiceActivation.DIRECT,mouseSleepMs=0))
        val deadline=SystemClock.uptimeMillis()+8000
        fun ready(){
            if(done)return
            if(activity.hasWindowFocus() && com.airgesture.app.accessibility.AccessibilityBridge.available){begin=SystemClock.uptimeMillis();if(!SessionStarter.channels(activity,true,false))finish(false,"Start gate rejected fixture") else tick()}
            else if(SystemClock.uptimeMillis()<deadline)handler.postDelayed({ready()},200)
            else finish(false,"Fixture/accessibility not ready before starting")
        }
        handler.postDelayed({ready()},1000)
    }
    private fun record(name:String){val s=ControlSessionState.current;rows.put(JSONObject().put("stage",name).put("voice",s.voiceEnabled).put("gestures",s.gesturesEnabled).put("audio",s.audio.state).put("recorders",activity.getSystemService(AudioManager::class.java).activeRecordingConfigurations.size).put("camera",s.cameraBound).put("model",s.handModelLoaded).put("frames",s.handFrames).put("actions_enabled",GlobalSession.actionsEnabled).put("mode",s.mode.name));write(false,"RUNNING")}
    private fun say(word:String){check(activity.hasWindowFocus());ControlSessionState.keywordHandler?.invoke(KeywordEvent(word,SystemClock.uptimeMillis(),.2f,0))}
    private fun tick(){
        if(done)return
        if(!activity.hasWindowFocus()){finish(false,"Fixture left foreground");return}
        if(SystemClock.uptimeMillis()-begin>55000){finish(false,"Timed out at stage $stage: ${ControlSessionState.current}");return}
        val s=ControlSessionState.current
        when(stage){
            0->if(s.voiceEnabled && s.audio.state=="ACTIVE" && !s.cameraBound){record("voice_only");stage=1;say("开启手势")}
            1->if(s.gesturesEnabled && s.cameraBound && s.handFrames>0){record("voice_opens_gestures");lastFrames=s.handFrames;stage=2;SessionStarter.channels(activity,false,true)}
            2->if(!s.voiceEnabled && s.audio.state=="OFF" && s.cameraBound && s.handFrames>lastFrames+1 && activity.getSystemService(AudioManager::class.java).activeRecordingConfigurations.isEmpty()){
                if(!GlobalSession.actionsEnabled){finish(false,"Gesture-only actions incorrectly require the mic");return};record("gesture_only_mic_released");stage=3;SessionStarter.channels(activity,true,true)
            }
            3->if(s.voiceEnabled && s.audio.state=="ACTIVE" && s.gesturesEnabled){record("voice_reenabled_preserves_gestures");stage=4;say("显示按钮")}
            4->if(s.mode==ControlMode.NUMBER_SELECT && s.audio.digitsActive && !s.cameraBound){record("selection_temporarily_suspends_camera");stage=5;say("取消");lastFrames=s.handFrames}
            5->if(s.mode==ControlMode.VISUAL_MOUSE && s.cameraBound && s.handFrames>lastFrames){record("selection_exit_restores_gestures");stage=6;say("关闭手势")}
            6->if(!s.gesturesEnabled && !s.cameraBound && !s.handModelLoaded && s.audio.state=="ACTIVE"){
                record("voice_closes_gestures_preserves_mic");stage=7;store.save(store.load().copy(activation=VoiceActivation.WAKE_WORD,wakeWord="小空小空"))
            }
            7->if(s.audio.state=="ACTIVE"){
                record("live_setting_change");stage=8;say("开启手势")
                if(ControlSessionState.current.lastDecision!="NEEDS_WAKE_WORD"){finish(false,"Wake-mode update was not applied");return}
                say("小空小空");handler.postDelayed({say("开启手势")},300)
            }
            8->if(s.gesturesEnabled && s.cameraBound){record("selected_wake_authorizes_gestures");stage=9;SessionStarter.channels(activity,false,false)}
            9->if(!s.running && !GlobalSession.running && activity.getSystemService(AudioManager::class.java).activeRecordingConfigurations.isEmpty()){record("both_off_releases_all");finish(true,"Independent channel switches and voice aliases passed with real microphone/camera resources; command text injected on owned fixture, not acoustic acceptance")}
        }
        handler.postDelayed({tick()},150)
    }
    private fun write(passed:Boolean,message:String){File(activity.filesDir,"proof-channels.json").writeText(JSONObject().put("passed",passed).put("message",message).put("rows",rows).put("synthetic_commands",true).put("raw_audio_saved",false).toString(2))}
    private fun finish(passed:Boolean,message:String){if(done)return;done=true;handler.removeCallbacksAndMessages(null);activity.stopService(Intent(activity,GestureForegroundService::class.java));store.save(original);write(passed,message);activity.target.text=message;activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    fun close(){if(!done)finish(false,"Proof closed before completion")}
}
