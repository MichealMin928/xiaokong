package com.airgesture.app.voice

import kotlin.math.log10
import kotlin.math.sqrt

/** Quiet-mode fallback: relative level can detect a soft short word missed by neural VAD.
 * It only segments audio; it never authorizes an action. */
class QuietSpeechBoundary {
    private var floorDb=-65.0
    private var loudSamples=0
    private var quietSamples=0
    private var quietDbSamples=0.0
    private var peakDb=-120.0
    fun update(pcm:FloatArray,active:Boolean):Pair<Boolean,Boolean>{
        val rms=sqrt(pcm.sumOf{it.toDouble()*it}/pcm.size.coerceAtLeast(1))
        val db=20*log10(rms.coerceAtLeast(.000001))
        if(!active){
            loudSamples=if(db>maxOf(floorDb+10,-65.0))loudSamples+pcm.size else 0
            quietSamples=0;quietDbSamples=0.0
            if(loudSamples==0){
                peakDb=-120.0
                floorDb=if(db<floorDb)floorDb*.85+db*.15 else minOf(-38.0,floorDb+.03)
            }else peakDb=maxOf(peakDb,db)
        }else{
            peakDb=maxOf(peakDb,db)
            // A changed room floor must not hold a completed word open indefinitely.
            // Require a sustained drop from the utterance itself, never a fixed absolute silence.
            if(db<maxOf(floorDb+7,peakDb-12,-67.0)){
                quietSamples+=pcm.size;quietDbSamples+=db*pcm.size
            }else{quietSamples=0;quietDbSamples=0.0}
            if(quietSamples>=4000)floorDb=(quietDbSamples/quietSamples).coerceIn(-85.0,-38.0)
        }
        return (loudSamples>=1600) to (quietSamples>=4000)
    }
    fun reset(){loudSamples=0;quietSamples=0;quietDbSamples=0.0;peakDb=-120.0} // Keep the learned room floor between utterances.
}
