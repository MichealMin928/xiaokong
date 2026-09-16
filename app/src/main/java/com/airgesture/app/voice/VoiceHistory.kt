package com.airgesture.app.voice

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded fixed-command metadata, persisted locally so a stopped session remains diagnosable. */
class VoiceHistory(context:Context){
    private val prefs=context.getSharedPreferences("voice_history",Context.MODE_PRIVATE)
    fun read()=runCatching{JSONArray(prefs.getString("events","[]"))}.getOrDefault(JSONArray())
    fun add(phrase:String,stage:String,detail:String){
        val old=read();val next=JSONArray()
        for(i in maxOf(0,old.length()-79) until old.length())next.put(old.getJSONObject(i))
        next.put(JSONObject().put("time",System.currentTimeMillis()).put("phrase",phrase).put("stage",stage).put("detail",detail))
        prefs.edit().putString("events",next.toString()).apply()
    }
    fun clear(){prefs.edit().remove("events").apply()}
}

object VoiceOutcome {
    fun gate(reason:String,wake:String)=when(reason){
        "PAUSED"->"控制已暂停，说「恢复控制」继续；麦克风不可用时请点首页恢复"
        "NEEDS_WAKE_WORD"->"这次未执行，请先说「$wake」；安静时可切换直接说"
        "COOLDOWN"->"这次间隔过短，请说完一句再说下一句"
        "STALE"->"这条指令到达过晚，已丢弃，请重说"
        "THRESHOLD"->"这次声音不够明确，请再说一次"
        "NOT_SELECTING"->"请先说「显示按钮」，再读屏幕上的编号"
        "NO_CHANGE"->"当前已是这个状态，可以继续说其他指令"
        "DIRECT_MODE"->"当前无需唤醒，直接说「上滑」或「显示按钮」"
        else->"未执行，请查看语音指令"
    }
    fun action(reason:String)=when(reason){
        "CONTROL_UNAVAILABLE"->"控制不可用，请检查无障碍服务或点恢复控制"
        "BUSY"->"上一条动作还在执行，请稍后再说"
        "STALE"->"指令到达过晚，请重说"
        "DISPLAY_CHANGED"->"屏幕方向已变化，请重说"
        "NO_CALLBACK_NO_RETRY"->"系统没有返回结果，请检查页面后重说"
        "SESSION_ENDED"->"操作被会话切换取消，请重说"
        else->"系统未完成操作，请检查当前页面或无障碍服务"
    }
}
