package com.airgesture.app.session

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.airgesture.app.voice.AudioDiagnostics

data class ControlStatus(val running:Boolean=false,val mode:ControlMode=ControlMode.VOICE_ONLY,val message:String="控制尚未启动",
    val audio:AudioDiagnostics=AudioDiagnostics(),val cameraBound:Boolean=false,val handModelLoaded:Boolean=false,
    val lastKeyword:String="—",val lastKeywordAt:Long=0,val lastDecision:String="—",val keywordThreshold:Float=.2f,
    val commandCount:Long=0,val voiceMs:Long=0,val cameraMs:Long=0,val handModelMs:Long=0,
    val handFrames:Long=0,val handInferenceMs:Long=0,val lastActionMs:Long?=null,
    val videoAssist:Boolean=false,val mediaFocus:String="OFF",val listening:Boolean=false,
    val selectionPhase:String="NONE",val choices:List<com.airgesture.app.selection.NumberTarget> = emptyList(),
    val voiceEnabled:Boolean=false,val gesturesEnabled:Boolean=false)
object ControlSessionState {
    internal var commandHandler:((com.airgesture.app.command.ControlCommand)->Unit)?=null
    internal var keywordHandler:((com.airgesture.app.voice.KeywordEvent)->Unit)?=null
    internal var videoAssistHandler:(()->Unit)?=null
    internal var configHandler:((com.airgesture.app.voice.VoiceCommandConfig)->Unit)?=null
    private val mutable=MutableLiveData(ControlStatus())
    val status:LiveData<ControlStatus> = mutable
    val current get()=mutable.value ?: ControlStatus()
    fun applyVoiceConfig(config:com.airgesture.app.voice.VoiceCommandConfig){configHandler?.invoke(config)}
    internal fun publish(value:ControlStatus){mutable.value=value}
    fun toggleVideoAssist():Boolean {
        if(!current.running || current.mode==ControlMode.PAUSED || current.audio.state!="ACTIVE")return false
        val handler=videoAssistHandler ?: return false;handler();return true
    }
    fun request(command:com.airgesture.app.command.ControlCommand):Boolean {
        if(!current.running)return false
        val handler=commandHandler ?: return false;handler(command);return true
    }
}

internal class RuntimeCounter {
    private var activeAt:Long?=null
    private var total=0L
    fun active(value:Boolean,now:Long){if(value && activeAt==null)activeAt=now else if(!value){activeAt?.let{total+=now-it};activeAt=null}}
    fun elapsed(now:Long)=total+(activeAt?.let{now-it} ?: 0)
}
