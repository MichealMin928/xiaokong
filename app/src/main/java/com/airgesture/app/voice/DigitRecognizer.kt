package com.airgesture.app.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.text.Normalizer

/** Short-lived local decoder, only during Number/Grid selection. Arbitrary text is discarded. */
class DigitRecognizer(context:Context):AutoCloseable {
    private val model=OnlineRecognizer(context.assets,OnlineRecognizerConfig(
        modelConfig=OnlineModelConfig(transducer=OnlineTransducerModelConfig(
            encoder="kws/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            decoder="kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
            joiner="kws/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"),tokens="kws/tokens.txt",modelType="zipformer2",numThreads=1),
        enableEndpoint=true,endpointConfig=EndpointConfig(rule2=EndpointRule(true,.45f,0f),rule3=EndpointRule(false,0f,8f))))
    private val stream=model.createStream()
    var inferenceCount=0L;private set
    var inferenceMs=0L;private set
    fun accept(pcm:FloatArray):List<String>{
        stream.acceptWaveform(pcm,16000);val results=mutableListOf<String>()
        while(model.isReady(stream)){val start=System.nanoTime();model.decode(stream);inferenceCount++;inferenceMs+=(System.nanoTime()-start)/1_000_000}
        if(model.isEndpoint(stream)){
            parse(model.getResult(stream).text)?.let{results.add(VoiceCommandEngine.numbers[it-1])}
            model.reset(stream)
        }
        return results
    }
    override fun close(){stream.release();model.release()}
    fun reset(){model.reset(stream)}
    companion object {
        private val syllables=listOf("yi","er","san","si","wu","liu","qi","ba","jiu","shi","shiyi","shier","shisan","shisi","shiwu","shiliu","shiqi","shiba","shijiu","ershi")
        fun parse(text:String):Int? {
            var value=Normalizer.normalize(text,Normalizer.Form.NFD).replace(Regex("\\p{M}|\\s"),"").lowercase()
            for(prefix in listOf("xuanze","dianji","xuan","dian","di"))if(value.startsWith(prefix)){value=value.removePrefix(prefix);break}
            for(suffix in listOf("hao","ge"))if(value.endsWith(suffix)){value=value.removeSuffix(suffix);break}
            return syllables.indexOf(value).takeIf{it>=0}?.plus(1)
        }
    }
}
