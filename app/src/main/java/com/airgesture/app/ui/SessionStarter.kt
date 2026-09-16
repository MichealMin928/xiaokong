package com.airgesture.app.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.airgesture.app.MainActivity
import com.airgesture.app.accessibility.AccessibilityBridge
import com.airgesture.app.mode.AssistantMode
import com.airgesture.app.service.GestureForegroundService

object SessionStarter {
    fun canStart(activity:Activity)=!activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus() &&
        activity.getSystemService(android.os.PowerManager::class.java).isInteractive &&
        !activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    fun channels(activity:Activity,voice:Boolean,gestures:Boolean):Boolean {
        if(!voice && !gestures){activity.stopService(Intent(activity,GestureForegroundService::class.java));return true}
        if(!canStart(activity))return false
        val ready=com.airgesture.app.voice.VoiceSettings(activity).disclosureAccepted && Settings.canDrawOverlays(activity) && AccessibilityBridge.available &&
            (!voice || ContextCompat.checkSelfPermission(activity,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) &&
            (!gestures || ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
        if(!ready){activity.startActivity(Intent(activity,com.airgesture.app.SetupActivity::class.java).putExtra("setup_voice",voice).putExtra("setup_gestures",gestures));return false}
        ContextCompat.startForegroundService(activity,Intent(activity,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_CHANNELS)
            .putExtra(GestureForegroundService.EXTRA_CONTROL,true).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name)
            .putExtra(GestureForegroundService.EXTRA_VOICE,voice).putExtra(GestureForegroundService.EXTRA_GESTURES,gestures))
        return true
    }
    fun startVoice(activity:Activity):Boolean {
        return channels(activity,true,com.airgesture.app.session.ControlSessionState.current.gesturesEnabled)
    }

    fun start(activity:Activity,mode:AssistantMode):Boolean {
        if(!canStart(activity))return false
        if(ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED || !Settings.canDrawOverlays(activity) || !AccessibilityBridge.available){
            AlertDialog.Builder(activity).setTitle("先完成控制权限")
                .setMessage("需要相机、悬浮窗和无障碍。识别在手机本地完成；无障碍用于前台识别和执行你发出的操作，编号选择时临时读取可点击控件，不记录页面内容。")
                .setPositiveButton("打开权限说明"){_,_->activity.startActivity(Intent(activity,MainActivity::class.java).putExtra("showPermissions",true))}
                .setNegativeButton("稍后",null).show();return false
        }
        ContextCompat.startForegroundService(activity,Intent(activity,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
            .putExtra(GestureForegroundService.EXTRA_CONTROL,true).putExtra(GestureForegroundService.EXTRA_MODE,mode.name))
        return true
    }
}
