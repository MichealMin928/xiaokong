package com.airgesture.app.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.*

/** Quiet mode: small VAD runs continuously; Chinese ASR runs once per completed
 * utterance. One recognizer handles commands and numbers. PCM and arbitrary text
 * stay in memory and are discarded after each utterance. */
class DirectCommandRecognizer(context:Context,private val wakeWord:String):AutoCloseable {
    private val pronunciation=VoicePronunciation(context)
    private val debug=context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0
    private val vad=Vad(context.assets,VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(
        model="kws/silero_vad.onnx",threshold=.45f,minSilenceDuration=.25f,minSpeechDuration=.1f,maxSpeechDuration=8f),numThreads=1))
    private val model=OfflineRecognizer(context.assets,OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
        senseVoice=OfflineSenseVoiceModelConfig(model="speech/model.int8.onnx",language="zh",useInverseTextNormalization=true),
        tokens="speech/tokens.txt",numThreads=1,provider="cpu")))
    private val boundary=QuietSpeechBoundary()
    private val preRoll=ArrayDeque<FloatArray>()
    private val utterance=mutableListOf<FloatArray>()
    private var active=false
    private var oversized=false
    private var samples=0
    var unmatchedUtterances=0L;private set
    var inferenceCount=0L;private set
    var inferenceMs=0L;private set
    fun accept(pcm:FloatArray,vocabulary:VoiceVocabulary):List<String>{
        val begin=System.nanoTime()
        // Lift only VAD input; retain untouched microphone samples for speech recognition.
        val peak=pcm.maxOfOrNull{kotlin.math.abs(it)} ?: 0f
        val gain=minOf(8f,.95f/peak.coerceAtLeast(.0001f))
        vad.acceptWaveform(FloatArray(pcm.size){pcm[it]*gain})
        val (energyStart,energyEnd)=boundary.update(pcm,active)
        if(!active){
            preRoll.addLast(pcm);while(preRoll.size>6)preRoll.removeFirst()
            if(vad.isSpeechDetected() || energyStart){
                active=true;utterance.addAll(preRoll);samples=preRoll.sumOf{it.size};preRoll.clear()
            }
        }else if(!oversized){utterance.add(pcm);samples+=pcm.size}
        if(samples>160000){oversized=true;utterance.clear()}
        val result=mutableListOf<String>()
        if(!vad.empty() || active && energyEnd){
            if(active && !oversized && samples>=1600){
                val data=FloatArray(samples);var offset=0
                for(chunk in utterance){chunk.copyInto(data,offset);offset+=chunk.size}
                val stream=model.createStream()
                try{
                    stream.acceptWaveform(data,16000);model.decode(stream);inferenceCount++
                    val text=model.getResult(stream).text
                    val word=VoiceCommandCatalog.parseTextUtterance(text,vocabulary,wakeWord) ?: pronunciation.parse(text,vocabulary,wakeWord)
                    if(debug){
                        val value=text.replace(Regex("[\\p{P}\\s]"),"")
                        val category=when(value){
                            "上划","上花","上华","上滑"->"UP_SAME_SYLLABLES"
                            "上海"->"UP_DIFFERENT_VOWEL"
                            "下划","下花","下滑"->"DOWN_SAME_SYLLABLES"
                            "编好","变好","便好","编号"->"NUMBER_SAME_SYLLABLES"
                            "标号"->"NUMBER_SYNONYM"
                            "一号","1号","依靠","你好"->"DIGIT_CONFUSION"
                            else->"UNCLASSIFIED"
                        }
                        com.airgesture.app.service.GlobalSession.log.add("speech_candidate fixed_match=$word class=$category chars=${value.length} audio_ms=${samples/16}")
                    }
                    if(word!=null)result.add(word) else unmatchedUtterances++
                }finally{stream.release()}
            }
            active=false;oversized=false;utterance.clear();preRoll.clear();samples=0
            vad.reset();vad.clear();boundary.reset()
        }
        inferenceMs+=(System.nanoTime()-begin)/1_000_000
        return result
    }
    fun reset(){active=false;oversized=false;samples=0;utterance.clear();preRoll.clear();vad.reset();vad.clear();boundary.reset()}
    override fun close(){utterance.clear();preRoll.clear();model.release();vad.release()}
}
