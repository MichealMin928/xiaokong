package com.airgesture.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.airgesture.app.session.*
import com.airgesture.app.service.*
import com.airgesture.app.ui.*
import com.airgesture.app.voice.*

class HomeActivity:ComponentActivity(){
    private lateinit var voice:Switch
    private lateinit var gestures:Switch
    private lateinit var status:TextView
    private lateinit var mode:Button
    private lateinit var recover:Button
    private var refreshing=false
    private var pendingAutoVoice=false
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val launchPrefs by lazy { getSharedPreferences("control_preferences",MODE_PRIVATE) }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        val root=page("小空",true)
        section(root,"动动口，动动手。")
        note(root,"两个开关，按需使用。")
        fun toggle(title:String,description:String,onChange:(Boolean)->Unit):Switch {
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=Brand.surface(this@HomeActivity,radius=22);setPadding(uiDp(20),uiDp(12),uiDp(20),uiDp(12))}
            val control=Switch(this).apply{text=title;textSize=21f;setTextColor(Brand.ink);minHeight=uiDp(60);showText=false;contentDescription=title;setOnCheckedChangeListener{_,checked->if(!refreshing)onChange(checked)}}
            card.addView(control,LinearLayout.LayoutParams(-1,-2));note(card,description)
            root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=uiDp(14)})
            return control
        }
        voice=toggle("语音控制","说指令控制手机，也能说「开启手势」。") { enabled->
            pendingAutoVoice=false
            launchPrefs.edit().putBoolean("launch_voice",enabled).apply()
            SessionStarter.channels(this,enabled,currentGestures());refresh()
        }
        gestures=toggle("手势控制","翻页、返回、鼠标点击。打开后使用前置相机。") { enabled->
            pendingAutoVoice=false
            SessionStarter.channels(this,ControlSessionState.current.voiceEnabled,enabled);refresh()
        }
        mode=button(root,""){VoicePreferencesDialog.activation(this){refresh()}}
        status=note(root,"")
        recover=button(root,"恢复控制"){ControlSessionState.request(com.airgesture.app.command.ControlCommand(com.airgesture.app.command.CommandKind.RESUME_CONTROL,android.os.SystemClock.uptimeMillis()))}
        feature(root,"语音指令","回到桌面、四向滑动、显示按钮…全部说法",AppIcon.VOICE){startActivity(Intent(this,VoiceCommandsActivity::class.java))}
        feature(root,"最近语音记录","有没有听到？为什么没有执行？",AppIcon.RECORD){startActivity(Intent(this,VoiceHistoryActivity::class.java))}
        feature(root,"识别调节与测试","换唤醒词、调灵敏度，检查漏听和误听",AppIcon.VOICE){startActivity(Intent(this,VoiceSettingsActivity::class.java))}
        feature(root,"帮助与问题反馈","手势指南、客户反馈、问题记录",AppIcon.RECORD){startActivity(Intent(this,FeedbackActivity::class.java))}
        note(root,"锁屏自动关闭两个开关。解锁不会自行录音或开启相机。")
        ControlSessionState.status.observe(this){refresh()};GlobalSession.status.observe(this){refresh()}
        // Only an explicit fresh launcher open uses the user's preferred default.
        // Returning from settings, unlocking, and configuration recreation never restart capture.
        pendingAutoVoice=savedInstanceState==null && intent.action==Intent.ACTION_MAIN && !GlobalSession.running && launchPrefs.getBoolean("launch_voice",true) && VoiceSettings(this).disclosureAccepted
    }
    private fun currentGestures()=ControlSessionState.current.gesturesEnabled || (GlobalSession.running && !ControlSessionState.current.running)
    private fun refresh(){
        if(!::status.isInitialized || ownNumberSelectionActive())return
        val s=ControlSessionState.current;val config=VoiceSettings(this).load()
        refreshing=true;voice.isChecked=s.voiceEnabled;gestures.isChecked=currentGestures();refreshing=false
        mode.updateTextIfChanged(if(config.activation==VoiceActivation.DIRECT)"安静环境 · 直接说指令" else "唤醒词模式 · ${config.wakeWord}")
        recover.visibility=if(s.running && s.mode==ControlMode.PAUSED)android.view.View.VISIBLE else android.view.View.GONE
        val heard=if(s.lastKeywordAt>0)"\n最近听到「${s.lastKeyword}」 · ${(android.os.SystemClock.uptimeMillis()-s.lastKeywordAt)/1000} 秒前" else ""
        status.updateTextIfChanged(if(s.running)s.message+heard else if(GlobalSession.running)GlobalSession.status.value?.message else "打开语音或手势开关即可开始。")
    }
    override fun onResume(){super.onResume();refresh()}
    override fun onWindowFocusChanged(hasFocus:Boolean){
        super.onWindowFocusChanged(hasFocus)
        if(hasFocus && pendingAutoVoice){
            val until=android.os.SystemClock.uptimeMillis()+5000
            fun attempt(){
                if(!pendingAutoVoice || !hasWindowFocus() || isFinishing || isDestroyed)return
                if(com.airgesture.app.accessibility.AccessibilityBridge.available){pendingAutoVoice=false;if(!GlobalSession.running)SessionStarter.channels(this,true,false)}
                else if(android.os.SystemClock.uptimeMillis()<until)handler.postDelayed({attempt()},150)
                else pendingAutoVoice=false
            }
            attempt()
        }
    }
    override fun onPause(){pendingAutoVoice=false;handler.removeCallbacksAndMessages(null);super.onPause()}
}
