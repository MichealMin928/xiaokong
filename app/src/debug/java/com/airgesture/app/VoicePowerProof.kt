package com.airgesture.app

import android.content.*
import android.os.*
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.airgesture.app.command.*
import com.airgesture.app.mode.AssistantMode
import com.airgesture.app.service.*
import com.airgesture.app.session.*
import com.airgesture.app.voice.*
import org.json.*
import java.io.File

/** 30 minute real microphone runs; commands in C must arrive acoustically, not through this harness. */
internal class VoicePowerProof(private val activity:ActionTestActivity,private val phase:String){
    private val handler=Handler(Looper.getMainLooper())
    private val runId=java.util.UUID.randomUUID().toString()
    private val samples=JSONArray()
    // Leave setup margin so actual third-party playback can exceed 30 full minutes.
    private val isMedia=phase in setOf("media","media_actions")
    private val actionsEnabled=phase in setOf("c","media_actions")
    private val durationSeconds=if(isMedia)1860 else 1800
    private var begin=0L;private var cpu=0L;private var lastSample=0L
    private var done=false;private var mouse=false
    private val store=VoiceSettings(activity)
    private val original=store.load()
    fun start(){
        activity.stopService(Intent(activity,GestureForegroundService::class.java))
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keep D's scheduled three minute camera interval independent of absent human hands.
        store.save(original.copy(activation=VoiceActivation.WAKE_WORD,mouseSleepMs=if(phase=="d")0 else original.mouseSleepMs))
        handler.postDelayed({
            ContextCompat.startForegroundService(activity,Intent(activity,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
                .putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name).putExtra(GestureForegroundService.EXTRA_VOICE,true)
                .putExtra(GestureForegroundService.EXTRA_CONTROL,actionsEnabled))
            handler.postDelayed({ready(SystemClock.uptimeMillis()+10000)},250)
        },1000)
    }
    private fun ready(deadline:Long){
        if(ControlSessionState.current.audio.state=="ACTIVE" && ControlSessionState.current.mode==ControlMode.VOICE_ONLY){
            begin=SystemClock.uptimeMillis();cpu=android.os.Process.getElapsedCpuTime();GlobalSession.log.add("power_run=$runId phase=$phase");tick()
            // Explicit short duplex diagnostic: give the operator time to open a video.
            if(isMedia && activity.intent.getBooleanExtra("video_assist",false))handler.postDelayed({if(!done)ControlSessionState.toggleVideoAssist()},15000)
        }else if(SystemClock.uptimeMillis()>deadline)finish(false,"Microphone/control mode not ready: ${ControlSessionState.current.audio.state}/${ControlSessionState.current.mode}") else handler.postDelayed({ready(deadline)},200)
    }
    private fun tick(){
        if(done)return
        val s=ControlSessionState.current;val now=SystemClock.uptimeMillis();val seconds=(now-begin)/1000
        if(!s.running || s.audio.state!="ACTIVE"){finish(false,"Session interrupted before 30 minutes");return}
        if(s.mode==ControlMode.PAUSED){finish(false,"Control paused before completion");return}
        if(!isMedia && !activity.hasWindowFocus()){finish(false,"Power surface left foreground");return}
        if(phase=="d"){
            val shouldMouse=seconds in 300..479
            if(shouldMouse!=mouse){mouse=shouldMouse;ControlSessionState.request(ControlCommand(if(mouse)CommandKind.START_MOUSE else CommandKind.STOP_MOUSE,now))}
        }
        if(lastSample==0L || now-lastSample>=30000 || seconds>=durationSeconds){lastSample=now
            val battery=activity.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            samples.put(JSONObject().put("elapsed_seconds",seconds).put("cpu_ms",android.os.Process.getElapsedCpuTime()-cpu).put("pss_kb",Debug.getPss())
                .put("temperature_c",(battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0) ?: 0)/10f)
                .put("battery_level",battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)).put("plugged",battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1))
                .put("charge_counter_uah",activity.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
                .put("music_active",activity.getSystemService(android.media.AudioManager::class.java).isMusicActive)
                .put("control_mode",s.mode.name)
                .put("thermal",if(Build.VERSION.SDK_INT>=29)activity.getSystemService(PowerManager::class.java).currentThermalStatus else -1)
                .put("voice_ms",s.voiceMs).put("camera_ms",s.cameraMs).put("model_ms",s.handModelMs).put("camera_active",s.cameraBound).put("model_loaded",s.handModelLoaded)
                .put("kws_count",s.audio.inferenceCount).put("kws_ms",s.audio.inferenceMs).put("digit_active",s.audio.digitsActive)
                .put("hand_count",s.handFrames).put("hand_ms",s.handInferenceMs).put("commands",s.commandCount).put("last_keyword",s.lastKeyword).put("last_decision",s.lastDecision)
                .put("last_action_ms",s.lastActionMs).put("aec",s.audio.aecEnabled).put("ns",s.audio.nsEnabled))
            write(false,"RUNNING")
        }
        if(seconds>=durationSeconds){
            val valid=s.mode==ControlMode.VOICE_ONLY && s.audio.inferenceCount>100 && when(phase){"d"->s.cameraMs in 120000..310000 && !s.cameraBound && !s.handModelLoaded;else->s.cameraMs==0L && s.handFrames==0L}
            finish(valid,"${durationSeconds/60} minute real-microphone $phase completed; inspect acoustic counts and conditions separately");return
        }
        handler.postDelayed({tick()},500)
    }
    private fun write(passed:Boolean,message:String){
        val log=GlobalSession.log.export().lineSequence().dropWhile{it!="power_run=$runId phase=$phase"}.drop(1).filter{it.startsWith("voice_") || it.startsWith("action_") || it.startsWith("session_final")}.toList()
        val data=JSONObject().put("run_id",runId).put("phase",phase).put("passed",passed).put("message",message).put("elapsed_ms",if(begin>0)SystemClock.uptimeMillis()-begin else 0)
            .put("started_uptime_ms",begin).put("started_wall_time_ms",if(begin>0)System.currentTimeMillis()-(SystemClock.uptimeMillis()-begin) else 0)
            .put("audio_source",if(original.communicationSource)"VOICE_COMMUNICATION" else "VOICE_RECOGNITION").put("keyword_threshold",original.keywordThreshold)
            .put("activation","WAKE_WORD").put("target_seconds",durationSeconds)
            .put("actions_enabled",actionsEnabled).put("mouse_schedule",if(phase=="d")"one 180 second camera interval; no deliberate hands" else "none")
            .put("command_source",if(actionsEnabled)"external loudspeaker -> physical OPPO microphone -> KWS -> shared dispatcher" else if(phase=="media")"third-party playback; observe-only, zero dispatch" else "none")
            .put("samples",samples).put("events",JSONArray(log)).put("raw_audio_saved",false).put("raw_images_saved",false).put("real_hand",false)
        File(activity.filesDir,"power-$phase.json").writeText(data.toString(2))
    }
    private fun finish(passed:Boolean,message:String){if(done)return;done=true;handler.removeCallbacksAndMessages(null);write(passed,message)
        activity.stopService(Intent(activity,GestureForegroundService::class.java));store.save(original)
        activity.target.text="${if(passed)"完成" else "失败"}：$message";activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    fun close(){if(!done)finish(false,"Test closed early")}
}
