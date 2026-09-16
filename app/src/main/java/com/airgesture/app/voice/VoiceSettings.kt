package com.airgesture.app.voice

import android.content.Context

class VoiceSettings(context:Context){
    private val prefs=context.getSharedPreferences("voice_settings",Context.MODE_PRIVATE)
    var disclosureAccepted:Boolean
        get()=prefs.getBoolean("disclosure_v1",false)
        set(value){prefs.edit().putBoolean("disclosure_v1",value).apply()}
    fun load()=VoiceCommandConfig(
        activation=VoiceActivation.entries.firstOrNull{it.name==prefs.getString("activation",null)} ?: VoiceActivation.WAKE_WORD,
        keywordThreshold=prefs.getFloat("threshold",.20f).coerceIn(.10f,.65f),
        mouseSleepMs=prefs.getLong("mouse_sleep_ms",10_000).takeIf{it in setOf(0L,5000L,10000L,30000L)} ?: 10000,
        communicationSource=prefs.getBoolean("communication_source",false),
        wakeWord=prefs.getString("wake_word",null)?.takeIf{it in VoiceCommandEngine.wakeWords} ?: "你好助手",
        wakeThreshold=prefs.getFloat("wake_threshold",.20f).coerceIn(.10f,.65f),
        noiseProcessing=prefs.getBoolean("noise_processing",true))
    fun save(value:VoiceCommandConfig){prefs.edit().putString("activation",value.activation.name).putFloat("threshold",value.keywordThreshold)
        .putLong("mouse_sleep_ms",value.mouseSleepMs).putBoolean("communication_source",value.communicationSource)
        .putString("wake_word",value.wakeWord).putFloat("wake_threshold",value.wakeThreshold).putBoolean("noise_processing",value.noiseProcessing).apply()
        com.airgesture.app.session.ControlSessionState.applyVoiceConfig(value)
    }
}
