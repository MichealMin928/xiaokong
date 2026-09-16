package com.airgesture.app

import android.content.Context
import com.airgesture.app.voice.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Deterministic synthetic speech only. Not a microphone, accent, echo, or human-accuracy test. */
internal object KeywordModelProof {
    fun run(context:Context):JSONArray {
        val words=JSONArray(context.assets.open("voice-fixtures/index.json").bufferedReader().readText())
        val result=JSONArray()
        for(i in 0 until words.length()){
            val bytes=context.assets.open("voice-fixtures/%02d.wav".format(i)).use{it.readBytes()}
            val samples=pcm(bytes)
            val hits=mutableListOf<String>()
            val padded=FloatArray(8000)+samples+FloatArray(16000)
            if(i<12)KeywordSpottingEngine(context,VoiceCommandConfig(wakeWord=words.getString(i).takeIf{it in VoiceCommandEngine.wakeWords} ?: "你好助手")).use{kws->
                kws.setVocabulary(VoiceVocabulary.COMMANDS)
                for(start in padded.indices step 1600)hits.addAll(kws.accept(padded.copyOfRange(start,minOf(start+1600,padded.size))))
                result.put(JSONObject().put("expected",words.getString(i)).put("hits",JSONArray(hits)).put("matched",words.getString(i) in hits)
                    .put("decode_count",kws.inferenceCount).put("decode_ms",kws.inferenceMs))
            }else DigitRecognizer(context).use{digits->
                for(start in padded.indices step 1600)hits.addAll(digits.accept(padded.copyOfRange(start,minOf(start+1600,padded.size))))
                val expected=words.getString(i).removeSuffix("号")
                result.put(JSONObject().put("expected",expected).put("spoken",words.getString(i)).put("hits",JSONArray(hits)).put("matched",expected in hits).put("decode_count",digits.inferenceCount))
            }
        }
        return result
    }
    fun pcm(bytes:ByteArray):FloatArray {
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);var pos=12
        while(pos+8<=bytes.size){
            val id=String(bytes,pos,4,Charsets.US_ASCII);val n=b.getInt(pos+4)
            require(n>=0 && pos+8+n<=bytes.size)
            if(id=="data")return FloatArray(n/2){b.getShort(pos+8+it*2)/32768f}
            pos+=8+n+(n%2)
        }
        error("No PCM data")
    }
}
