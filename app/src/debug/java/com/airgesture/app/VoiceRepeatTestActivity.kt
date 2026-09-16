package com.airgesture.app

import android.content.Intent
import android.os.*
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import com.airgesture.app.service.*
import com.airgesture.app.session.*
import com.airgesture.app.ui.SessionStarter
import org.json.JSONObject

/** Own scroll content; microphone-only input. Does not inject any recognized keyword. */
class VoiceRepeatTestActivity:ComponentActivity(){
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var scroll:ScrollView
    private var clicks=0
    private val observer=Observer<ControlStatus>{write(it)}
    override fun onCreate(state:Bundle?){super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,80,24,60);setBackgroundColor(0xfff5f7f3.toInt())}
        root.addView(TextView(this).apply{text="语音连续翻页验收 · 本机内容";textSize=21f},LinearLayout.LayoutParams(-1,120))
        root.addView(Button(this).apply{text="测试按钮";setOnClickListener{clicks++;write(ControlSessionState.current)}},LinearLayout.LayoutParams(-1,160))
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        repeat(80){i->content.addView(TextView(this).apply{text="第 ${i+1} 行 · 上滑应向后翻，下滑应返回";textSize=20f;setBackgroundColor(if(i%2==0)0xffe6f0e8.toInt() else 0xffffffff.toInt())},LinearLayout.LayoutParams(-1,420))}
        scroll=ScrollView(this).apply{addView(content);setOnScrollChangeListener{_,_,_,_,_->write(ControlSessionState.current)}}
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        ControlSessionState.status.observeForever(observer)
        stopService(Intent(this,GestureForegroundService::class.java))
        val deadline=SystemClock.uptimeMillis()+10000
        fun ready(){
            if(hasWindowFocus() && com.airgesture.app.accessibility.AccessibilityBridge.available)SessionStarter.channels(this,true,false)
            else if(SystemClock.uptimeMillis()<deadline)handler.postDelayed({ready()},200)
        }
        handler.postDelayed({ready()},1000)
    }
    private fun write(s:ControlStatus){if(!::scroll.isInitialized)return
        val j=JSONObject().put("time",SystemClock.uptimeMillis()).put("scroll_y",scroll.scrollY).put("clicks",clicks).put("mode",s.mode.name)
            .put("audio",s.audio.state).put("decoder",s.audio.decoder).put("keyword",s.lastKeyword).put("keyword_at",s.lastKeywordAt).put("decision",s.lastDecision).put("message",s.message)
            .put("commands",s.commandCount).put("digits_ready",s.audio.digitsActive).put("choices",s.choices.size).put("camera",s.cameraBound).put("delay",s.audio.inputDelayMs).put("dropped",s.audio.droppedAudioMs)
            .put("audio_ms",s.audio.audioMs).put("inference_ms",s.audio.inferenceMs).put("actions_enabled",GlobalSession.actionsEnabled)
        val temporary=java.io.File(filesDir,"voice-repeat-live.tmp");temporary.writeText(j.toString(2));temporary.renameTo(java.io.File(filesDir,"voice-repeat-live.json"))
    }
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);ControlSessionState.status.removeObserver(observer);super.onDestroy()}
}
