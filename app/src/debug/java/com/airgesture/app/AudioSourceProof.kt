package com.airgesture.app

import android.content.Intent
import android.os.*
import android.view.WindowManager
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.voice.*
import org.json.*
import java.io.File

/** Visible-activity microphone comparison. Fixed keyword metadata only; never dispatches an action. */
internal class AudioSourceProof(private val activity:ActionTestActivity,private val communication:Boolean){
    private val handler=Handler(Looper.getMainLooper())
    private val runId=java.util.UUID.randomUUID().toString()
    private val kind=if(communication)"audio_comm" else "audio_recognition"
    private var audio:AudioInputManager?=null
    private var status=AudioDiagnostics()
    private var started=0L
    private var done=false
    private val words=JSONArray()
    private val config=VoiceSettings(activity).load().copy(communicationSource=communication,noiseProcessing=!activity.intent.getBooleanExtra("quiet_input",false),
        wakeWord=activity.intent.getStringExtra("wake_word")?.takeIf{it in VoiceCommandEngine.wakeWords} ?: "你好助手")
    private val duration=activity.intent.getLongExtra("duration_ms",70000).coerceIn(10000,120000)
    fun start(){
        activity.stopService(Intent(activity,GestureForegroundService::class.java));write(false,"RUNNING")
        handler.postDelayed({
            audio=AudioInputManager(activity,config,{e->
                words.put(JSONObject().put("keyword",e.keyword).put("timestamp",e.timestamp).put("elapsed_ms",e.timestamp-started).put("decode_ms",e.decodeMs));write(false,"RUNNING")
            },{s->
                status=s
                if(s.state=="ACTIVE" && started==0L)started=SystemClock.uptimeMillis()
                if(s.state in setOf("ERROR","INTERRUPTED"))finish(false,s.error ?: s.state) else if(!done)write(false,"RUNNING")
            }).also{it.start()}
            tick(SystemClock.uptimeMillis()+10000)
        },1000)
    }
    private fun tick(deadline:Long){
        if(done)return
        if(!activity.hasWindowFocus()){finish(false,"Own comparison surface left foreground");return}
        if(started==0L && SystemClock.uptimeMillis()>deadline){finish(false,"Microphone did not start");return}
        if(started>0 && SystemClock.uptimeMillis()-started>=duration){finish(status.inferenceCount>20,"Microphone source comparison; inspect acoustic matches separately");return}
        handler.postDelayed({tick(deadline)},500)
    }
    private fun write(passed:Boolean,message:String){
        val j=JSONObject().put("run_id",runId).put("passed",passed).put("message",message).put("source",status.source).put("state",status.state)
            .put("started_uptime_ms",started).put("elapsed_ms",if(started>0)SystemClock.uptimeMillis()-started else 0)
            .put("aec_supported",status.aecSupported).put("aec_enabled",status.aecEnabled).put("ns_supported",status.nsSupported).put("ns_enabled",status.nsEnabled)
            .put("inference_count",status.inferenceCount).put("inference_ms",status.inferenceMs).put("keywords",words).put("actions_dispatched",0).put("raw_audio_saved",false)
            .put("wake_word",config.wakeWord).put("threshold",config.keywordThreshold).put("wake_threshold",config.wakeThreshold)
            .put("dropped_audio_ms",status.droppedAudioMs).put("input_delay_ms",status.inputDelayMs).put("input_peak_db",status.inputPeakDb)
        File(activity.filesDir,"proof-$kind.json").writeText(j.toString(2))
    }
    private fun finish(passed:Boolean,message:String){if(done)return;done=true;handler.removeCallbacksAndMessages(null);audio?.close();audio=null;write(passed,message);activity.target.text=message;activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    fun close(){if(!done)finish(false,"Comparison closed early")}
}
