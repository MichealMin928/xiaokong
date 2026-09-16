package com.airgesture.app.command

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint

enum class CommandKind { CLICK, DOUBLE_CLICK, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, BACK, HOME, RECENTS,
    PALM_FLIP, DRAG_START, DRAG_MOVE, DRAG_END, LIKE, DISLIKE,
    START_MOUSE, STOP_MOUSE, SHOW_NUMBERS, SHOW_GRID, SELECT_NUMBER, CANCEL, PAUSE_CONTROL, RESUME_CONTROL }
data class ControlCommand(val kind:CommandKind,val detectedAt:Long,val point:CursorPoint?=null,val number:Int?=null){
    val systemAction get()=ActionKind.entries.firstOrNull{it.name==kind.name}
    companion object {
        fun gesture(kind:ActionKind,point:CursorPoint?,time:Long)=ControlCommand(CommandKind.valueOf(kind.name),time,point)
    }
}
