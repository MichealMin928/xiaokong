package com.airgesture.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.airgesture.app.ui.*
import com.airgesture.app.voice.*

class VoiceCommandsActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state)
        val root=page("语音指令",true)
        val config=VoiceSettings(this).load()
        note(root,if(config.activation==VoiceActivation.DIRECT)"当前：直接说。说完一句，稍停一下，再说下一句。上滑可以连续说，不需要每次重新开启。" else "当前：唤醒模式。先说「${config.wakeWord}」，5 秒内说一个操作；下一次操作需重新唤醒。显示按钮、分区选择、取消、关闭手势、暂停与恢复可直接说。")
        section(root,"回到桌面，就说「回到桌面」")
        note(root,"下面是当前版本实际支持的全部语音操作。同一行的说法作用相同。")
        VoiceCommandCatalog.entries.forEach{entry->
            section(root,entry.title)
            note(root,entry.phrases.joinToString(" / ")+"\n"+entry.description)
        }
        section(root,"数字点击：1–20 号")
        note(root,"先显示按钮或分区，等数字识别就绪。说「一号」「点击五号」或「选择十二号」。只接受当前屏幕显示的编号；页面滚动或切换后需要重新显示。")
        button(root,"查看最近听到与执行记录"){startActivity(Intent(this,VoiceHistoryActivity::class.java))}
    }
}
class VoiceHistoryActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);render()}
    private fun render(){
        val root=page("最近语音记录",true)
        note(root,"按「听到 → 放行 → 提交系统 → 系统结果」检查。没有新的「听到」记录表示这次没有识别出固定指令。系统返回完成只代表动作已发出，页面没有可滚动内容时可能不会移动。仅保留最近 80 条本机记录，不含录音和任意语音转写。")
        button(root,"刷新记录"){render()}
        button(root,"保存问题并附带记录"){startActivity(Intent(this,FeedbackActivity::class.java))}
        button(root,"清空这些记录"){VoiceHistory(this).clear();render()}
        val rows=VoiceHistory(this).read()
        if(rows.length()==0)note(root,"还没有记录。打开语音控制后说一句指令。")
        val format=java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.CHINA)
        for(i in rows.length()-1 downTo 0){val row=rows.getJSONObject(i)
            section(root,"${format.format(java.util.Date(row.getLong("time")))} · ${row.optString("stage")} · ${row.optString("phrase")}")
            note(root,row.optString("detail"))
        }
    }
}
