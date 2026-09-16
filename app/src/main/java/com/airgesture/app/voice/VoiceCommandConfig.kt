package com.airgesture.app.voice

enum class VoiceActivation { DIRECT, WAKE_WORD }
enum class KeywordSource { KWS, DIGIT_DECODER, COMMAND_DECODER }
enum class VoiceVocabulary { COMMANDS, SELECTION, PAUSED }
data class VoiceCommandConfig(
    val activation:VoiceActivation=VoiceActivation.WAKE_WORD,
    val cooldownMs:Long=700,
    val wakeWindowMs:Long=5000,
    val keywordThreshold:Float=.20f,
    val mouseSleepMs:Long=10_000,
    val communicationSource:Boolean=false,
    val wakeWord:String="你好助手",
    val wakeThreshold:Float=.20f,
    val noiseProcessing:Boolean=true,
)
data class KeywordEvent(val keyword:String,val timestamp:Long,val threshold:Float,val decodeMs:Long,
    // sherpa-onnx 1.13.8's KWS result has no per-result probability. Never invent one.
    val confidence:Float?=null,val decoderThresholdPassed:Boolean=true,val source:KeywordSource=KeywordSource.KWS)
