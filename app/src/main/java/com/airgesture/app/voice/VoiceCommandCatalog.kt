package com.airgesture.app.voice

import com.airgesture.app.command.CommandKind

/** The recognizer, command router and user guide share one finite command list. */
object VoiceCommandCatalog {
    data class Entry(val kind:CommandKind,val title:String,val description:String,val phrases:List<String>)
    private fun entry(kind:CommandKind,title:String,description:String,vararg phrases:String)=
        Entry(kind,title,description,phrases.toList())
    val entries=listOf(
        entry(CommandKind.SWIPE_UP,"向上滑动","页面内容通常随上滑向上移动。","上滑","向上滑动"),
        entry(CommandKind.SWIPE_DOWN,"向下滑动","与上滑方向相反。","下滑","向下滑动"),
        entry(CommandKind.SWIPE_LEFT,"向左滑动","可用于桌面左右换页。","左滑","向左滑动"),
        entry(CommandKind.SWIPE_RIGHT,"向右滑动","可用于桌面左右换页。","右滑","向右滑动"),
        entry(CommandKind.BACK,"返回上一页","相当于系统返回键。","返回","返回上一页"),
        entry(CommandKind.HOME,"回到桌面","相当于系统主页键。","回到桌面","返回桌面","桌面","主页"),
        entry(CommandKind.RECENTS,"最近任务","显示最近打开的应用。","最近任务"),
        entry(CommandKind.SHOW_NUMBERS,"显示按钮编号","给当前屏幕真正可点击的控件标号，再说数字加「号」。","显示按钮","显示编号","编号","按钮"),
        entry(CommandKind.SHOW_GRID,"分区选择","只显示含按钮的区域；先说区域号，再说按钮号。","分区选择","屏幕分区","网格"),
        entry(CommandKind.START_MOUSE,"开启手势 / 隔空鼠标","食指瞄准，拇指靠近两次点击。","开启手势","打开手势","鼠标"),
        entry(CommandKind.STOP_MOUSE,"关闭手势","释放相机，保留语音。","关闭手势","关闭鼠标"),
        entry(CommandKind.CANCEL,"取消选择","隐藏按钮编号，退出当前选择。","取消","取消选择"),
        entry(CommandKind.PAUSE_CONTROL,"暂停控制","停止操作，保留麦克风以便恢复。","暂停控制"),
        entry(CommandKind.RESUME_CONTROL,"恢复控制","暂停后继续；麦克风已被占用时请用首页恢复按钮。","恢复控制")
    )
    val byPhrase=entries.flatMap{e->e.phrases.map{it to e.kind}}.toMap()
    fun parseTextUtterance(text:String,vocabulary:VoiceVocabulary,wakeWord:String):String? {
        val value=text.replace(Regex("[\\p{P}\\s]"),"")
        if(value==wakeWord)return wakeWord
        if(value in byPhrase)return value
        if(vocabulary!=VoiceVocabulary.SELECTION)return null
        val match=Regex("^(?:点击|选择|选|点|第)?([一二三四五六七八九十两0-9]{1,3})(?:号|个)?$").matchEntire(value) ?: return null
        val token=match.groupValues[1].replace("两","二")
        val number=token.toIntOrNull() ?: VoiceCommandEngine.numbers.indexOf(token).takeIf{it>=0}?.plus(1)
        return number?.takeIf{it in 1..20}?.let{VoiceCommandEngine.numbers[it-1]}
    }
}
