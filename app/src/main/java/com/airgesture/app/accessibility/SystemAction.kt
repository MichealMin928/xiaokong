package com.airgesture.app.accessibility

import com.airgesture.app.cursor.CursorPoint

enum class ActionKind { CLICK, DOUBLE_CLICK, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, BACK, HOME, RECENTS, PALM_FLIP, DRAG_START, DRAG_MOVE, DRAG_END, LIKE, DISLIKE }
val ActionKind.label:String get()=when(this){
    ActionKind.LIKE->"点赞";ActionKind.DISLIKE->"不喜欢"
    ActionKind.CLICK->"点击";ActionKind.DOUBLE_CLICK->"双击";ActionKind.SWIPE_UP->"向上翻页";ActionKind.SWIPE_DOWN->"向下翻页"
    ActionKind.SWIPE_LEFT->"向左滑动";ActionKind.SWIPE_RIGHT->"向右滑动";ActionKind.BACK->"返回";ActionKind.HOME->"桌面";ActionKind.RECENTS->"最近任务"
    ActionKind.PALM_FLIP->"翻掌";ActionKind.DRAG_START->"开始拖动";ActionKind.DRAG_MOVE->"拖动中";ActionKind.DRAG_END->"放下"
}
data class ScreenGeometry(val width: Int, val height: Int, val rotation: Int) {
    val valid get() = width > 1 && height > 1 && rotation in 0..3
    fun contains(point: CursorPoint) = valid && point.isFinite() &&
        point.x >= 0 && point.y >= 0 && point.x < width && point.y < height
}
data class NodeSelection(val snapshotId:Long,val number:Int)
data class SystemAction(val kind: ActionKind, val point: CursorPoint?, val screen: ScreenGeometry,
                        val detectedAt: Long,val nodeSelection:NodeSelection?=null)
enum class ActionResult { DISPATCHED, COMPLETED, CANCELLED, REJECTED, TIMED_OUT }
data class ActionReceipt(val id: Long, val kind: ActionKind, val result: ActionResult,
                         val reason: String, val elapsedMs: Long)

/** Serial, no queue and no retry. Late callbacks cannot release a newer action's lock. */
class ActionCoordinator(private val now: () -> Long, private val timeoutMs: Long = 1200,
                        private val receipt: (ActionReceipt) -> Unit) {
    private var sequence = 0L
    private var pending: Pair<Long, SystemAction>? = null
    val busy get() = pending != null

    fun submit(action: SystemAction, allowed: Boolean, currentScreen: ScreenGeometry,
               dispatch: (SystemAction, (ActionResult) -> Unit) -> Boolean): Boolean {
        expire()
        val id = ++sequence
        val reason = when {
            !allowed -> "CONTROL_UNAVAILABLE"
            pending != null -> "BUSY"
            now() - action.detectedAt !in 0..250 -> "STALE"
            !action.screen.valid || action.screen != currentScreen -> "DISPLAY_CHANGED"
            action.kind in setOf(ActionKind.CLICK,ActionKind.DOUBLE_CLICK,ActionKind.DRAG_START,ActionKind.DRAG_MOVE,ActionKind.DRAG_END) && (action.point == null || !action.screen.contains(action.point)) -> "INVALID_POINT"
            else -> null
        }
        if (reason != null) {
            receipt(ActionReceipt(id, action.kind, ActionResult.REJECTED, reason, 0)); return false
        }
        pending = id to action
        val accepted = try {
            dispatch(action) { result ->
                if (pending?.first == id) finish(result, "SYSTEM_CALLBACK")
            }
        } catch (_: Exception) { false }
        if (!accepted) {
            if (pending?.first == id) finish(ActionResult.REJECTED, "SYSTEM_REJECTED")
        } else if (pending?.first == id) {
            receipt(ActionReceipt(id, action.kind, ActionResult.DISPATCHED, "WAITING_CALLBACK", now() - action.detectedAt))
        }
        return accepted
    }
    fun expire() {
        pending?.second?.let { if (now() - it.detectedAt > if(it.kind==ActionKind.DISLIKE)3500 else timeoutMs) finish(ActionResult.TIMED_OUT, "NO_CALLBACK_NO_RETRY") }
    }
    /** Prevent new actions; an already dispatched Android stroke may finish (at most 280 ms). */
    fun reset() { if (pending != null) finish(ActionResult.CANCELLED, "SESSION_ENDED") }
    private fun finish(result: ActionResult, reason: String) {
        val (id, action) = pending ?: return
        pending = null
        receipt(ActionReceipt(id, action.kind, result, reason, (now() - action.detectedAt).coerceAtLeast(0)))
    }
}
