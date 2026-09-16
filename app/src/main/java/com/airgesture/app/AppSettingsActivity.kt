package com.airgesture.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.airgesture.app.mode.*
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.ui.*

class AppSettingsActivity:ComponentActivity(){
    private lateinit var store:ProfileStore
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);store=ProfileStore(this);stopService(Intent(this,GestureForegroundService::class.java));render()}
    private fun render(){
        val root=page("应用设置")
        note(root,"已停止当前会话。保存设置后重新开始。\n应用场景用于手势与阅读模式；语音会话默认全局运行。")
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f))
        val whitelist=Switch(this).apply{text="鼠标也仅在启用的应用中工作";isChecked=store.mouseWhitelist;setOnCheckedChangeListener{_,v->store.mouseWhitelist=v}}
        list.addView(whitelist)
        list.addView(Switch(this).apply{text="操作 / 阅读会话跟随应用切换模式";isChecked=store.autoModes;setOnCheckedChangeListener{_,v->store.autoModes=v}})
        store.profiles().forEach{profile->button(list,"${if(profile.enabled)"✓" else "○"} ${profile.name} · ${profile.mode.label}"){edit(profile)}}
        button(root,"添加其他已安装的应用"){
            val query=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps=packageManager.queryIntentActivities(query,0).distinctBy{it.activityInfo.packageName}.sortedBy{it.loadLabel(packageManager).toString()}
            AlertDialog.Builder(this).setTitle("选择应用").setItems(apps.map{it.loadLabel(packageManager).toString()}.toTypedArray()){_,i->
                val app=apps[i];edit(AppProfile(app.activityInfo.packageName,app.loadLabel(packageManager).toString()))
            }.setNegativeButton("取消",null).show()
        }
    }
    private fun edit(original:AppProfile){
        var mode=original.mode
        val mappings=original.mappings.toMutableMap()
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,12,30,12)}
        fun toggle(label:String,value:Boolean)=Switch(this).apply{text=label;isChecked=value;panel.addView(this)}
        val enabled=toggle("在这个应用中启用",original.enabled)
        val modeButton=button(panel,"模式：${mode.label}"){}
        modeButton.setOnClickListener{AlertDialog.Builder(this).setTitle("应用模式").setItems(AssistantMode.entries.map{it.label}.toTypedArray()){_,i->mode=AssistantMode.entries[i];modeButton.text="模式：${mode.label}"}.show()}
        GestureInput.entries.forEach{input->
            val b=button(panel,"${input.label} → ${mappings[input]?.label}"){}
            b.setOnClickListener{AlertDialog.Builder(this).setTitle(input.label).setItems(MappedAction.entries.map{it.label}.toTypedArray()){_,i->mappings[input]=MappedAction.entries[i];b.text="${input.label} → ${mappings[input]?.label}"}.show()}
        }
        val gaze=toggle("阅读模式：启用视线翻页",original.gazeEnabled)
        val fallback=toggle("阅读模式：手势备用翻页",original.handFallback)
        note(panel,"备用手势开启后，手与脸依次推理；发现手时短暂切到手势，避免同时翻两页。")
        var dwell=original.gazeDwellMs
        val sensitivity=button(panel,"视线停留确认：${dwell} 毫秒"){}
        sensitivity.setOnClickListener{val values=listOf(350L,550L,850L);AlertDialog.Builder(this).setTitle("翻页灵敏度").setItems(arrayOf("灵敏 · 350 毫秒","均衡 · 550 毫秒","稳健 · 850 毫秒")){_,i->dwell=values[i];sensitivity.text="视线停留确认：${dwell} 毫秒"}.show()}
        AlertDialog.Builder(this).setTitle(original.name).setView(ScrollView(this).apply{addView(panel)})
            .setPositiveButton("保存"){_,_->store.save(original.copy(mode=mode,enabled=enabled.isChecked,mappings=mappings,gazeEnabled=gaze.isChecked,handFallback=fallback.isChecked,gazeDwellMs=dwell));render()}
            .setNegativeButton("取消",null).show()
    }
}
