package com.airgesture.app.gesture

import com.airgesture.app.cursor.CursorPoint

/** Detect closing BEFORE contact. Finger flexion may be large; palm movement cancels an aim. */
class PinchAimLock {
    private val history = ArrayDeque<HandObservation>()
    var anchor: CursorPoint? = null; private set
    var palm: CursorPoint? = null; private set
    private var baseline = 0f
    private var started = 0L
    fun reset() { history.clear(); anchor=null; palm=null; baseline=0f; started=0 }
    fun release() { reset() }
    fun observe(hand: HandObservation, armed: Boolean): CursorPoint? {
        if (anchor != null) return anchor
        while(history.isNotEmpty() && hand.time-history.first().time>650) history.removeFirst()
        val previous=history.lastOrNull()
        if(armed && previous!=null) {
            val peak=history.maxOf {it.pinchRatio}
            if((peak-hand.pinchRatio>=.10f && hand.pinchRatio<.95f) || hand.pinchRatio<=.30f) {
                // Most recent stable target ending before the closing trend, not the already-bent fingertip.
                val candidates=history.filter {it.pinchRatio>=peak-.07f && distance(it.palm,hand.palm)<.065f}
                val stable=candidates.lastOrNull { end ->
                    val window=candidates.filter{it.time in (end.time-220)..end.time}
                    window.size>=2 && end.time-window.first().time>=90 && window.all{distance(it.cursor,end.cursor)<.035f}
                }
                val aim=stable ?: previous
                if(distance(aim.palm,hand.palm)<.065f){
                    val window=if(stable!=null)candidates.filter{it.time in (stable.time-220)..stable.time} else listOf(aim)
                    fun median(value:(HandObservation)->Float)=window.map(value).sorted().let{it[it.size/2]}
                    anchor=CursorPoint(median{it.cursor.x},median{it.cursor.y});palm=aim.palm;baseline=peak;started=hand.time
                }
            }
        }
        if(anchor==null)history.addLast(hand)
        return anchor
    }
    fun cancelled(hand:HandObservation) = anchor!=null &&
        (distance(checkNotNull(palm),hand.palm)>.085f || hand.time-started>1100 || hand.pinchRatio>=baseline-.04f)
}
