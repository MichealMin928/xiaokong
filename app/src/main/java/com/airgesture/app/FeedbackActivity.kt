package com.airgesture.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.airgesture.app.feedback.LocalFeedback
import com.airgesture.app.session.ControlSessionState
import com.airgesture.app.ui.*
import com.airgesture.app.voice.VoiceSettings
import org.json.JSONObject

class FeedbackActivity:ComponentActivity(){
    private var exportText=""
    private lateinit var detail:EditText
    private lateinit var result:TextView
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)runCatching{checkNotNull(contentResolver.openOutputStream(uri)).bufferedWriter().use{it.write(exportText)}}
            .onSuccess{result.text="已导出。你可以把这个文件交给开发者。"}.onFailure{result.text="导出失败，请重试。"}
    }
    override fun onCreate(state:Bundle?){super.onCreate(state)
        exportText=state?.getString("export") ?: ""
        val root=page("帮助与问题反馈",true)
        feature(root,"社区反馈 / 参与开发","打开 GitHub，报告问题或参与改进",AppIcon.RECORD){CommunityLinks.open(this,CommunityLinks.ISSUES)}
        note(root,"社区页面由浏览器打开，需要联网及 GitHub 账号。打开页面不会上传本机记录；提交内容公开可见，请先检查并去除个人信息。")
        feature(root,"使用指南","语音、翻页、返回和鼠标点击",AppIcon.HAND){startActivity(Intent(this,HelpActivity::class.java))}
        feature(root,"语音跟读测试","记录漏听和误听，便于调整识别",AppIcon.VOICE){startActivity(Intent(this,VoiceDiagnosticsActivity::class.java))}
        section(root,"留下遇到的问题")
        val types=arrayOf("唤醒没反应","指令没反应或听错","误触发操作","手势或鼠标问题","客户使用建议","其他问题")
        val type=Spinner(this).apply{adapter=ArrayAdapter(this@FeedbackActivity,android.R.layout.simple_spinner_dropdown_item,types)};root.addView(type)
        detail=EditText(this).apply{hint="当时在做什么？说了什么或做了什么动作？希望有什么改进？";minLines=4;gravity=android.view.Gravity.TOP;maxLines=8;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;filters=arrayOf(android.text.InputFilter.LengthFilter(2000));setText(state?.getString("detail") ?: intent.getStringExtra("detail") ?: "")};root.addView(detail)
        result=note(root,"下面的记录保存在本机，不会自动发送。可在检查内容后，通过上方社区入口提交问题。")
        primaryButton(root,"保存问题记录"){
            if(detail.text.isBlank()){detail.error="请简单描述遇到的问题";return@primaryButton}
            val s=ControlSessionState.current;val config=VoiceSettings(this).load()
            val report=JSONObject().put("kind","CUSTOMER_FEEDBACK").put("created_at",System.currentTimeMillis()).put("category",types[type.selectedItemPosition]).put("description",detail.text.toString())
                .put("activation",config.activation.name).put("wake_word",config.wakeWord).put("threshold",config.keywordThreshold).put("wake_threshold",config.wakeThreshold)
                .put("noise_processing",config.noiseProcessing).put("communication_source",config.communicationSource).put("voice_enabled",s.voiceEnabled).put("gestures_enabled",s.gesturesEnabled).put("audio_state",s.audio.state)
                .put("voice_history",com.airgesture.app.voice.VoiceHistory(this).read()).put("decoder",s.audio.decoder).put("last_fixed_keyword",s.lastKeyword).put("last_decision",s.lastDecision).put("input_delay_ms",s.audio.inputDelayMs).put("dropped_audio_ms",s.audio.droppedAudioMs)
            runCatching{LocalFeedback(this).save(report)}.onSuccess{result.text="已保存在本机：${it.take(8)}。可从下方查看或导出。"}.onFailure{result.text="保存失败，请重试。"}
        }
        button(root,"查看记录 / 导出"){
            val files=LocalFeedback(this).files()
            if(files.isEmpty()){result.text="还没有问题记录。";return@button}
            val labels=files.map{file->val j=runCatching{JSONObject(file.readText())}.getOrNull();val date=java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(file.lastModified()));"$date · ${j?.optString("category")?.takeIf{it.isNotBlank()} ?: "语音测试"}"}.toTypedArray()
            AlertDialog.Builder(this).setTitle("本机记录（最多保留 100 条）").setItems(labels){_,i->
                val file=files[i];val text=file.readText();val j=JSONObject(text)
                AlertDialog.Builder(this).setTitle(labels[i]).setMessage(j.optString("description").takeIf{it.isNotBlank()} ?: "${j.optString("summary")}\n完整结果会写入导出文件。")
                    .setPositiveButton("导出文件"){_,_->exportText=text;export.launch("xiaokong-feedback-${file.name}")}
                    .setNeutralButton("删除"){_,_->file.delete();result.text="该记录已删除"}.setNegativeButton("关闭",null).show()
            }.show()
        }
        note(root,"自动附带版本、识别设置和固定指令的处理结果，不含声音、画面、其他应用页面文字。请勿在描述中填写密码等敏感信息。")
    }
    override fun onSaveInstanceState(out:Bundle){out.putString("detail",detail.text.toString());out.putString("export",exportText);super.onSaveInstanceState(out)}
}
