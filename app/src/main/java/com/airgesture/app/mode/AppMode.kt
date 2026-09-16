package com.airgesture.app.mode

import com.airgesture.app.accessibility.ActionKind

enum class AssistantMode(val label:String) { VIDEO("应用内隔空操作"), READING("眼控阅读"), MOUSE("隔空鼠标"), GLOBAL("全局隔空控制") }
enum class GestureInput(val label:String) { UP("上挥"), DOWN("下挥"), LEFT("左挥"), RIGHT("右挥"), PINCH("单捏"), DOUBLE_PINCH("双捏"), PALM_FLIP("翻掌") }
enum class MappedAction(val label:String,val kind:ActionKind?) {
    NONE("关闭",null), NEXT("下一条 / 下一页",ActionKind.SWIPE_UP), PREVIOUS("上一条 / 上一页",ActionKind.SWIPE_DOWN),
    PLAY_PAUSE("点屏幕中心：播放 / 暂停",ActionKind.CLICK), LIKE("屏幕中心双击：点赞",ActionKind.DOUBLE_CLICK),
    BACK("返回",ActionKind.BACK), HOME("桌面",ActionKind.HOME), RECENTS("最近任务",ActionKind.RECENTS),
    SEEK_BACK("向右滑：快退",ActionKind.SWIPE_RIGHT), SEEK_FORWARD("向左滑：快进",ActionKind.SWIPE_LEFT)
}
data class AppProfile(val packageName:String,val name:String,val mode:AssistantMode=AssistantMode.VIDEO,
    val enabled:Boolean=true,val mappings:Map<GestureInput,MappedAction> = defaults(),val gazeEnabled:Boolean=true,
    val gazeDwellMs:Long=550,val handFallback:Boolean=false) {
    companion object {
        fun defaults()=mapOf(GestureInput.UP to MappedAction.NEXT,GestureInput.DOWN to MappedAction.PREVIOUS,
            GestureInput.PINCH to MappedAction.PLAY_PAUSE,GestureInput.DOUBLE_PINCH to MappedAction.LIKE,
            GestureInput.PALM_FLIP to MappedAction.BACK,GestureInput.LEFT to MappedAction.NONE,GestureInput.RIGHT to MappedAction.NONE)
        fun readingDefaults()=GestureInput.entries.associateWith{when(it){GestureInput.UP->MappedAction.NEXT;GestureInput.DOWN->MappedAction.PREVIOUS;else->MappedAction.NONE}}
        val presets=listOf(AppProfile("com.ss.android.ugc.aweme","抖音"),AppProfile("com.xingin.xhs","小红书"),
            AppProfile("tv.danmaku.bili","哔哩哔哩",mappings=defaults()+mapOf(GestureInput.LEFT to MappedAction.SEEK_FORWARD,GestureInput.RIGHT to MappedAction.SEEK_BACK)),
            AppProfile("com.smile.gifmaker","快手"),AppProfile("com.tencent.weread","微信读书",AssistantMode.READING,mappings=readingDefaults()),
            AppProfile("com.qidian.QDReader","起点读书",AssistantMode.READING,mappings=readingDefaults()))
    }
}
/** Fail closed for unknown apps in scene modes; manual mouse can explicitly cover any foreground app. */
object ModeRouter {
    fun resolve(session:AssistantMode,profile:AppProfile?,automatic:Boolean)=if(automatic && session !in listOf(AssistantMode.MOUSE,AssistantMode.GLOBAL) && profile?.enabled==true)profile.mode else session
    fun allowed(mode:AssistantMode,packageName:String?,profiles:List<AppProfile>,mouseWhitelist:Boolean):Boolean {
        if(mode==AssistantMode.GLOBAL)return true // Explicit session choice; keeps existing whitelist preference unchanged.
        if(mode==AssistantMode.MOUSE && !mouseWhitelist)return true
        return profiles.any{it.enabled && it.packageName==packageName && (mode==AssistantMode.MOUSE || it.mode==mode)}
    }
    fun map(kind:ActionKind,profile:AppProfile?):MappedAction? {
        val input=when(kind){ActionKind.CLICK->GestureInput.PINCH;ActionKind.DOUBLE_CLICK->GestureInput.DOUBLE_PINCH
            ActionKind.SWIPE_UP->GestureInput.UP;ActionKind.SWIPE_DOWN->GestureInput.DOWN
            ActionKind.SWIPE_LEFT->GestureInput.LEFT;ActionKind.SWIPE_RIGHT->GestureInput.RIGHT
            ActionKind.PALM_FLIP->GestureInput.PALM_FLIP;else->return null}
        return profile?.mappings?.get(input) ?: MappedAction.NONE
    }
}

enum class WorkState { SLEEP, WATCH, ACTIVE }
class PowerGovernor(private val idleMs:Long=2000) {
    var state=WorkState.WATCH; private set
    private var lastPresence:Long?=null
    fun update(now:Long,allowed:Boolean,present:Boolean,paused:Boolean=false):WorkState {
        if(!allowed){lastPresence=null;state=WorkState.SLEEP;return state}
        if(present)lastPresence=now
        state=if(lastPresence?.let{now-it<=idleMs}==true)WorkState.ACTIVE else WorkState.WATCH
        return state
    }
    fun fps(normal:Int,thermal:Int=0,batteryC:Float=0f):Int = minOf(when(state){WorkState.SLEEP->1;WorkState.WATCH->3;WorkState.ACTIVE->normal},
        if(thermal>=3 || batteryC>=44f)8 else if(thermal>=2 || batteryC>=42f)12 else normal)
}
