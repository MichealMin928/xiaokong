package com.airgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.airgesture.app.ui.*
import com.airgesture.app.voice.*

object VoicePreferencesDialog {
    fun activation(activity:Activity,changed:()->Unit){
        val store=VoiceSettings(activity);val config=store.load()
        AlertDialog.Builder(activity).setTitle("怎样说指令")
            .setSingleChoiceItems(arrayOf("安静环境：直接说指令，无需唤醒","视频或交谈环境：先说唤醒词"),if(config.activation==VoiceActivation.DIRECT)0 else 1){d,i->
                store.save(store.load().copy(activation=if(i==0)VoiceActivation.DIRECT else VoiceActivation.WAKE_WORD,noiseProcessing=i!=0));d.dismiss();changed()
            }.setNegativeButton("取消",null).show()
    }
}
class VoiceSettingsActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);render()}
    override fun onResume(){super.onResume();render()}
    private fun render(){
        val root=page("识别调节",true);val store=VoiceSettings(this);val config=store.load()
        feature(root,"${if(config.activation==VoiceActivation.DIRECT)"直接说指令" else "先说唤醒词"}","安静时无需唤醒；播放视频时可选唤醒模式",AppIcon.VOICE){VoicePreferencesDialog.activation(this){render()}}
        feature(root,"唤醒词：${config.wakeWord}","选一个顺口的中文短语，再用自己的声音测试",AppIcon.VOICE){
            val words=VoiceCommandEngine.wakeWords.toTypedArray()
            AlertDialog.Builder(this).setTitle("选择中文唤醒词").setSingleChoiceItems(words,words.indexOf(config.wakeWord)){d,i->store.save(store.load().copy(wakeWord=words[i]));d.dismiss();render()}.show()
        }
        fun sensitivity(wake:Boolean){
            val value=if(wake)config.wakeThreshold else config.keywordThreshold
            AlertDialog.Builder(this).setTitle(if(wake)"唤醒灵敏度" else "指令灵敏度")
                .setSingleChoiceItems(arrayOf("更容易听到 · 漏听多时试用","均衡","减少误触 · 环境声音触发时试用"),if(value<.18f)0 else if(value<.30f)1 else 2){d,i->
                    val threshold=floatArrayOf(.12f,.20f,.35f)[i];val current=store.load()
                    store.save(if(wake)current.copy(wakeThreshold=threshold) else current.copy(keywordThreshold=threshold));d.dismiss();render()
                }.show()
        }
        fun label(value:Float)=if(value<.18f)"更容易听到" else if(value<.30f)"均衡" else "减少误触"
        feature(root,"唤醒灵敏度",label(config.wakeThreshold),AppIcon.SETTINGS){sensitivity(true)}
        if(config.activation==VoiceActivation.WAKE_WORD)feature(root,"指令灵敏度",label(config.keywordThreshold),AppIcon.SETTINGS){sensitivity(false)}
        else note(root,"直接模式按完整短句识别，说完稍停一下即可。它和唤醒词的关键词灵敏度是两条不同路径。")
        note(root,"提高灵敏度也可能更容易被视频或旁人的话触发。直接模式只听固定指令，不需要先喊名字；不会自动根据音量切换模式。修改后立即应用，麦克风短暂重启。")
        primaryButton(root,"用我的声音测试"){startActivity(Intent(this,VoiceDiagnosticsActivity::class.java))}
        section(root,"视频声音与麦克风")
        button(root,if(com.airgesture.app.session.ControlSessionState.current.videoAssist)"关闭视频外放辅助" else "开启视频外放辅助"){
            if(!com.airgesture.app.session.ControlSessionState.toggleVideoAssist())android.widget.Toast.makeText(this,"先在首页打开语音开关，等待麦克风就绪",android.widget.Toast.LENGTH_SHORT).show()
            render()
        }
        note(root,"外放辅助会临时压低媒体声，关闭后交还声音控制。")
        feature(root,"录音兼容方式",if(!config.noiseProcessing)"安静原声 · 关闭附加降噪" else if(config.communicationSource)"通话处理" else "语音识别",AppIcon.SETTINGS){
            AlertDialog.Builder(this).setTitle("麦克风处理方式").setSingleChoiceItems(arrayOf("安静原声 · 关闭附加降噪","语音识别音源 · 启用降噪","通话处理音源 · 启用降噪"),if(!config.noiseProcessing)0 else if(config.communicationSource)2 else 1){d,i->store.save(store.load().copy(noiseProcessing=i!=0,communicationSource=i==2));d.dismiss();render()}.show()
        }
        note(root,"不同手机的降噪效果不同。切换后再测试相同短语，对比漏听和误听记录。")
    }
}
