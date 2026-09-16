package com.airgesture.app.voice

import com.airgesture.app.command.*
import com.airgesture.app.session.ControlMode

data class VoiceDecision(val command:ControlCommand?=null,val reason:String,val awakened:Boolean=false)

/** No Android calls: validated vocabulary -> explicit state/activation/cooldown -> one command. */
class VoiceCommandEngine(private val config:VoiceCommandConfig){
    private var wakeUntil=0L
    private var cooldownUntil=0L
    private var lastEvent:Long?=null
    fun reset(){wakeUntil=0;cooldownUntil=0;lastEvent=null}
    fun cancelWake(){wakeUntil=0}
    fun listening(now:Long)=wakeUntil>0 && now<=wakeUntil
    fun accept(event:KeywordEvent,mode:ControlMode,now:Long):VoiceDecision {
        if(now-event.timestamp !in 0..250 || lastEvent?.let{event.timestamp<it}==true)return VoiceDecision(reason="STALE")
        lastEvent=event.timestamp
        val threshold=if(event.keyword==config.wakeWord)config.wakeThreshold else config.keywordThreshold
        if(!event.decoderThresholdPassed || event.confidence?.let{!it.isFinite() || it<threshold}==true)return VoiceDecision(reason="THRESHOLD")
        val word=event.keyword
        if(word==config.wakeWord){
            if(config.activation==VoiceActivation.DIRECT)return VoiceDecision(reason="DIRECT_MODE")
            wakeUntil=now+config.wakeWindowMs;return VoiceDecision(reason="AWAKE",awakened=true)
        }
        val number=numbers.indexOf(word).takeIf{it>=0}?.plus(1) ?: word.toIntOrNull()?.takeIf{it in 1..20}
        val kind=VoiceCommandCatalog.byPhrase[word] ?: if(number!=null)CommandKind.SELECT_NUMBER else return VoiceDecision(reason="UNKNOWN")
        val selection=mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT)
        if(number!=null && !selection)return VoiceDecision(reason="NOT_SELECTING")
        if(mode==ControlMode.PAUSED && kind!=CommandKind.RESUME_CONTROL)return VoiceDecision(reason="PAUSED")
        if((kind==CommandKind.CANCEL && mode==ControlMode.VOICE_ONLY) ||
            (kind==CommandKind.RESUME_CONTROL && mode!=ControlMode.PAUSED))return VoiceDecision(reason="NO_CHANGE")
        // Cancellation, stopping the camera, and pausing must remain easy to reach.
        val escape=kind in setOf(CommandKind.CANCEL,CommandKind.PAUSE_CONTROL,CommandKind.STOP_MOUSE) || (mode==ControlMode.PAUSED && kind==CommandKind.RESUME_CONTROL)
        // Showing choices is read-only. It must not depend on a missed wake word.
        // A subsequent, separate numbered utterance is still required to click.
        val showChoices=kind in setOf(CommandKind.SHOW_NUMBERS,CommandKind.SHOW_GRID)
        if(config.activation==VoiceActivation.WAKE_WORD && !listening(now) && number==null && !escape && !showChoices)return VoiceDecision(reason="NEEDS_WAKE_WORD")
        if(now<cooldownUntil && !escape)return VoiceDecision(reason="COOLDOWN")
        cooldownUntil=now+config.cooldownMs
        if(number==null && !escape)wakeUntil=0
        return VoiceDecision(ControlCommand(kind,event.timestamp,number=number),"ACCEPTED")
    }
    companion object {
        val wakeWords=setOf("你好助手","小空小空","你好小空","小空")
        val numbers=listOf("一","二","三","四","五","六","七","八","九","十","十一","十二","十三","十四","十五","十六","十七","十八","十九","二十")
    }
}
