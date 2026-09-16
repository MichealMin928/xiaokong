package com.airgesture.app.accessibility

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.airgesture.app.selection.*

/** Nodes stay in RAM for one explicit selection only. No labels, text, or tree enter logs. */
internal class AccessibilityNodeScanner {
    private data class Entry(val node:AccessibilityNodeInfo,val rect:Rect,val original:Rect,val priority:Int,val identity:String)
    private var entries=listOf<Entry>()
    var snapshot:NumberSnapshot?=null;private set
    private var sequence=0L
    private var created=0L
    @Suppress("DEPRECATION") fun clear(){entries.forEach{it.node.recycle()};entries=emptyList();snapshot=null}
    private fun actionable(node:AccessibilityNodeInfo)=node.isClickable || node.isEditable || node.actionList.any{it.id==AccessibilityNodeInfo.ACTION_CLICK}
    @Suppress("DEPRECATION") private fun identity(node:AccessibilityNodeInfo):String {
        val parts=ArrayList<String>();var count=0
        fun visit(n:AccessibilityNodeInfo,depth:Int){
            if(count++>=12)return
            parts.add("${n.viewIdResourceName}|${n.className}|${n.text?.take(256)}|${n.contentDescription?.take(256)}")
            if(depth<2)for(i in 0 until n.childCount){if(count>=12)break;val child=n.getChild(i) ?: continue;try{
                if(android.os.Build.VERSION.SDK_INT>=33 || child.refresh())visit(child,depth+1)
            }finally{child.recycle()}}
        }
        visit(node,0);return parts.joinToString("\n")
    }
    @Suppress("DEPRECATION") fun scan(root:AccessibilityNodeInfo?,screen:ScreenGeometry,version:Long,maxTargets:Int=20):NumberSnapshot? {
        clear();if(root==null || !screen.valid)return null
        val candidates=mutableListOf<Entry>();var visited=0
        val deadline=SystemClock.uptimeMillis()+180
        var truncated=false
        fun visit(node:AccessibilityNodeInfo,depth:Int){
            if(visited++>=500 || depth>48 || SystemClock.uptimeMillis()>deadline){truncated=true;return}
            if(android.os.Build.VERSION.SDK_INT<33 && !node.refresh())return
            if(node.isVisibleToUser && node.isEnabled){
                val rect=Rect();node.getBoundsInScreen(rect)
                val original=Rect(rect)
                val visible=rect.intersect(0,0,screen.width,screen.height)
                if(visible && rect.width()>10 && rect.height()>10 && rect.width().toLong()*rect.height()<screen.width.toLong()*screen.height*.85 &&
                    actionable(node)){
                    candidates.add(Entry(AccessibilityNodeInfo.obtain(node),Rect(rect),original,if(node.isEditable)4 else 3,identity(node)))
                }
            }
            // Some custom containers report themselves hidden/disabled while
            // exposing visible, enabled descendants. Test each target itself.
            for(i in 0 until node.childCount){if(visited>=500)break;val child=node.getChild(i) ?: continue;try{visit(child,depth+1)}finally{child.recycle()}}
        }
        val window=root.windowId
        try{visit(root,0)}finally{root.recycle()}
        val kept=mutableListOf<Entry>()
        for(entry in candidates.sortedWith(compareByDescending<Entry>{it.priority}.thenByDescending{it.rect.width().toLong()*it.rect.height()})){
            val duplicate=kept.any{other->
                val r=Rect(entry.rect);if(!r.intersect(other.rect))false else {
                    val intersection=r.width().toLong()*r.height()
                    val smaller=minOf(entry.rect.width().toLong()*entry.rect.height(),other.rect.width().toLong()*other.rect.height())
                    val larger=maxOf(entry.rect.width().toLong()*entry.rect.height(),other.rect.width().toLong()*other.rect.height())
                    intersection>=smaller*.9 && smaller>=larger*.75
                }
            }
            if(!duplicate && kept.size<maxTargets.coerceIn(1,80))kept.add(entry) else {if(!duplicate)truncated=true;entry.node.recycle()}
        }
        entries=kept.sortedWith(compareBy<Entry>{it.rect.top/screen.height.toFloat()}.thenBy{it.rect.left})
        if(entries.isEmpty())return null
        created=SystemClock.uptimeMillis()
        return NumberSnapshot(++sequence,window,version,entries.mapIndexed{i,e->NumberTarget(i+1,SelectionBounds(e.rect.left.toFloat(),e.rect.top.toFloat(),e.rect.right.toFloat(),e.rect.bottom.toFloat()))},truncated).also{snapshot=it}
    }
    fun target(selection:NodeSelection,currentWindow:Int,currentVersion:Long):Pair<AccessibilityNodeInfo,Rect>? {
        val snap=snapshot ?: return null
        if(snap.id!=selection.snapshotId || snap.windowId!=currentWindow || snap.contextVersion!=currentVersion || SystemClock.uptimeMillis()-created>20_000)return null
        val entry=entries.getOrNull(selection.number-1) ?: return null
        if(!entry.node.refresh() || !entry.node.isVisibleToUser || !entry.node.isEnabled || !actionable(entry.node) || identity(entry.node)!=entry.identity)return null
        val current=Rect();entry.node.getBoundsInScreen(current)
        if(current!=entry.original)return null
        return entry.node to Rect(entry.rect)
    }
}
