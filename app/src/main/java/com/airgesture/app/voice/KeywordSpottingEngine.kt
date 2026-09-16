package com.airgesture.app.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.*

/** One native model and stream, owned exclusively by the audio worker. PCM never leaves memory. */
class KeywordSpottingEngine(context:Context,private val config:VoiceCommandConfig):AutoCloseable {
    private val lines=context.assets.open("kws/keywords.txt").bufferedReader().use{it.readLines()}
    private val model=KeywordSpotter(context.assets,KeywordSpotterConfig(
        modelConfig=OnlineModelConfig(transducer=OnlineTransducerModelConfig(
            encoder="kws/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            decoder="kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            joiner="kws/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),
            tokens="kws/tokens.txt",modelType="zipformer2",numThreads=1,provider="cpu"),
        // createStream(keywords) APPENDS defaults in 1.13.8; an empty base is
        // essential for true mode isolation and for our per-stream threshold to win.
        keywordsFile="kws/base_keywords.txt",keywordsThreshold=config.keywordThreshold,numTrailingBlanks=2))
    private var vocabulary:VoiceVocabulary?=null
    private var stream:OnlineStream?=null
    var inferenceCount=0L;private set
    var inferenceMs=0L;private set
    private val numberWords=setOf("一","二","三","四","五","六","七","八","九","十","十一","十二","十三","十四","十五","十六","十七","十八","十九","二十")
    fun setVocabulary(value:VoiceVocabulary,force:Boolean=false){
        if(vocabulary==value && !force)return
        val selected=lines.filter{line->val word=line.substringAfter('@');
            if(word in VoiceCommandEngine.wakeWords && word!=config.wakeWord)return@filter false
            when(value){
            VoiceVocabulary.SELECTION->word !in numberWords // Endpoint-aware DigitRecognizer owns numbers.
            VoiceVocabulary.COMMANDS->word !in numberWords
            VoiceVocabulary.PAUSED->word !in numberWords // Keep hearing commands so PAUSED can explain why they were not executed.
        }}.joinToString("\n"){it.replace("#0.35","#${if(it.substringAfter('@')==config.wakeWord)config.wakeThreshold else config.keywordThreshold}")}
        stream?.release();stream=model.createStream(selected);vocabulary=value
    }
    fun accept(samples:FloatArray):List<String>{
        if(stream==null)setVocabulary(VoiceVocabulary.COMMANDS)
        val s=checkNotNull(stream);s.acceptWaveform(samples,16000)
        val results=mutableListOf<String>()
        while(model.isReady(s)){
            val begin=android.os.SystemClock.elapsedRealtime();model.decode(s)
            inferenceCount++;inferenceMs+=android.os.SystemClock.elapsedRealtime()-begin
            val result=model.getResult(s)
            if(result.keyword.isNotEmpty()){results.add(result.keyword);model.reset(s)}
        }
        return results
    }
    override fun close(){stream?.release();stream=null;model.release()}
}
