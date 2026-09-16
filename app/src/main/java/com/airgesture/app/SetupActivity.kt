package com.airgesture.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.widget.CheckBox
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.airgesture.app.accessibility.AccessibilityBridge
import com.airgesture.app.ui.*
import com.airgesture.app.voice.VoiceSettings

class SetupActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state)}
    override fun onResume(){super.onResume();render()}
    private fun granted(permission:String)=ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED
    private fun render(){
        val root=page("开始之前",true);val store=VoiceSettings(this)
        note(root,"你决定何时开始，也能随时结束。权限只用于本机免触控操作。")
        feature(root,"麦克风 · ${if(granted(Manifest.permission.RECORD_AUDIO))"已允许" else "待允许"}","开始控制后识别固定命令，不保存声音",AppIcon.VOICE){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)}
        feature(root,"相机 · ${if(granted(Manifest.permission.CAMERA))"已允许" else "可选"}","只在鼠标或手势模式开启，关闭后释放",AppIcon.HAND){requestPermissions(arrayOf(Manifest.permission.CAMERA),11)}
        feature(root,"悬浮窗 · ${if(Settings.canDrawOverlays(this))"已允许" else "待允许"}","显示光标、编号和网格",AppIcon.GRID){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}
        feature(root,"无障碍 · ${if(AccessibilityBridge.available)"已连接" else "待开启"}","在系统设置中开启「小空免触控控制」",AppIcon.POINTER){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        note(root,"无障碍用于执行你发出的点击、滑动、返回和主页操作；平时识别前台应用。你要求编号时，临时读取控件属性和位置；执行不喜欢时查找菜单。控件快照只保留在内存中，退出选择就清除，不记录页面文字。")
        val consent=CheckBox(this).apply{text="我已了解并同意上述本机处理方式";textSize=14f;setTextColor(Brand.ink);isChecked=store.disclosureAccepted;setOnCheckedChangeListener{_,checked->store.disclosureAccepted=checked}}
        root.addView(consent)
        val wantVoice=intent.getBooleanExtra("setup_voice",true);val wantGestures=intent.getBooleanExtra("setup_gestures",false)
        primaryButton(root,if(wantVoice)"开启语音控制" else "开启手势控制"){
            if(!store.disclosureAccepted){android.widget.Toast.makeText(this,"请先阅读并勾选处理说明",android.widget.Toast.LENGTH_SHORT).show();return@primaryButton}
            if((!wantVoice || granted(Manifest.permission.RECORD_AUDIO)) && (!wantGestures || granted(Manifest.permission.CAMERA)) && Settings.canDrawOverlays(this) && AccessibilityBridge.available){
                if(Build.VERSION.SDK_INT>=33 && !granted(Manifest.permission.POST_NOTIFICATIONS))requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),12)
                if(SessionStarter.channels(this,wantVoice,wantGestures)){startActivity(Intent(this,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish()}
            }else android.widget.Toast.makeText(this,"请先完成所选控制方式需要的权限",android.widget.Toast.LENGTH_LONG).show()
        }
        button(root,"查看完整隐私说明"){startActivity(Intent(this,PrivacyActivity::class.java))}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);render()}
}
