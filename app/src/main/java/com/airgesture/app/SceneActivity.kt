package com.airgesture.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.airgesture.app.mode.*
import com.airgesture.app.service.*
import com.airgesture.app.ui.*

class SceneActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);val root=page("隔空操作",true)
        note(root,"翻页和鼠标在同一会话中使用。\n这个模式会持续开启前摄；锁屏或停止后关闭。")
        primaryButton(root,"开始全局手势控制"){SessionStarter.start(this,AssistantMode.GLOBAL)}
        val status=note(root,"")
        button(root,"暂停 / 继续"){if(GlobalSession.running)startService(Intent(this,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_PAUSE))}
        button(root,"结束手势控制"){stopService(Intent(this,GestureForegroundService::class.java))}
        section(root,"抬手就能操作")
        feature(root,"翻页与返回","四指向上、两指向下；横向指尖推手返回",AppIcon.HAND){startActivity(Intent(this,HelpActivity::class.java))}
        feature(root,"精准点击","单独伸食指瞄准，拇指靠近两次确认",AppIcon.POINTER){startActivity(Intent(this,MainActivity::class.java))}
        feature(root,"动作训练与回放","检查不同角度的识别结果",AppIcon.RECORD){startActivity(Intent(this,TrainingActivity::class.java))}
        button(root,"选择应用并开始"){
            val profiles=ProfileStore(this).profiles().filter{it.enabled && it.mode==AssistantMode.VIDEO && packageManager.getLaunchIntentForPackage(it.packageName)!=null}
            if(profiles.isEmpty())Toast.makeText(this,"请先在应用设置中启用一个应用",Toast.LENGTH_LONG).show()
            else android.app.AlertDialog.Builder(this).setTitle("选择应用").setItems(profiles.map{it.name}.toTypedArray()){_,i->
                packageManager.getLaunchIntentForPackage(profiles[i].packageName)?.let{if(SessionStarter.start(this,AssistantMode.GLOBAL))startActivity(it)}
            }.show()
        }
        button(root,"应用与手势映射"){startActivity(Intent(this,AppSettingsActivity::class.java))}
        GlobalSession.status.observe(this){if(!ownNumberSelectionActive())status.updateTextIfChanged(it.message)}
    }
}
