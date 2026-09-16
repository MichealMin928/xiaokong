package com.airgesture.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Scoped ducking; never changes volume settings or communication routing.
 * Android releases focus on process death. The session owns any bounded clip transition.
 */
class MediaAudioFocus(context:Context,private val lost:()->Unit):AutoCloseable {
    private val manager=context.getSystemService(AudioManager::class.java)
    private var requested=false
    var state="OFF";private set
    private val listener=AudioManager.OnAudioFocusChangeListener { change ->
        if(requested && change in setOf(AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)){
            close();lost()
        }
    }
    private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(listener,Handler(Looper.getMainLooper())).build()
    fun mediaPlaying()=manager.mode==AudioManager.MODE_NORMAL && manager.isMusicActive
    fun setWanted(value:Boolean){
        if(!value){close();return}
        if(requested)return
        requested=true
        state=if(runCatching{manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED}.getOrDefault(false))"ACTIVE" else "DENIED"
    }
    override fun close(){
        if(requested)runCatching{manager.abandonAudioFocusRequest(request)}
        requested=false;state="OFF"
    }
}
