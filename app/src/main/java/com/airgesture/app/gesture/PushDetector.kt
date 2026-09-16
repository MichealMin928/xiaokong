package com.airgesture.app.gesture

/** Experimental optical expansion, off by default. Requires palm AND box growth, stable center,
 * stable shape ratio and a return toward the original size before rearming. It is not depth sensing. */
class PushDetector {
    private var rest:HandObservation?=null
    private var anchor:HandObservation?=null
    private var consumed=false
    private var growthFrames=0
    fun reset(){rest=null;anchor=null;consumed=false;growthFrames=0}
    fun update(hand:HandObservation):Boolean {
        if(!hand.openPalm || hand.fist){reset();return false}
        val base=anchor
        if(base==null){
            val old=rest
            if(old==null || distance(old.palm,hand.palm)>.035f || hand.palmSize/old.palmSize !in .96f..1.04f)rest=hand
            else if(hand.time-old.time>=300)anchor=hand
            return false
        }
        val growth=hand.palmSize/base.palmSize
        if(consumed){if(growth<1.08f)reset();return false}
        val area=hand.boxArea/base.boxArea
        if(distance(hand.palm,base.palm)>.055f || area/(growth*growth) !in .75f..1.25f){reset();return false}
        // Update the time origin while at rest, then require a short, continuous expansion.
        if(growth<1.06f){anchor=hand;growthFrames=0;return false}
        val dt=hand.time-base.time
        if(dt>600){reset();return false}
        if(growth>=1.25f && area>=1.5f && dt>=100){
            growthFrames++
            if(growthFrames>=2){consumed=true;return true}
        } else growthFrames=0
        return false
    }
}
