package com.airgesture.app.voice

/** A player may request focus for each new clip. Never keep fighting an audio owner. */
internal class VideoAssistRetryLimiter {
    private val losses=ArrayDeque<Long>()
    fun reset(){losses.clear()}
    fun allow(now:Long):Boolean {
        while(losses.isNotEmpty() && now-losses.first()>=10_000)losses.removeFirst()
        if(losses.size>=2)return false
        losses.addLast(now);return true
    }
}
