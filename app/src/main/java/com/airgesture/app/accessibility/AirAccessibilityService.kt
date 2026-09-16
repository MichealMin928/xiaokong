package com.airgesture.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.airgesture.app.service.GlobalSession
import java.lang.ref.WeakReference

/** Explicit voice/gesture actions; node snapshots exist only during numbered selection. */
class AirAccessibilityService : AccessibilityService() {
    private val drag by lazy { DragDispatcher(this) }
    private val social by lazy { SocialActionDispatcher(this) }
    private val scanner=AccessibilityNodeScanner()
    private var selectionBaseFlags:Int?=null
    override fun onServiceConnected() { super.onServiceConnected(); AccessibilityBridge.connect(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Scrolling or switching windows invalidates the whole selection. Ordinary
        // content updates (clocks, captions, counters) must not dismiss every choice;
        // the selected node's bounds, identity and action are revalidated at click time.
        val packageName=event?.packageName?.toString() ?: return
        if(packageName==this.packageName && event.className==com.airgesture.app.selection.SelectionOverlay.WINDOW_CLASS)return
        scanner.snapshot?.let{snap->
            if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className!="android.widget.Toast" && event.windowId>=0 && event.windowId!=snap.windowId ||
                event.windowId==snap.windowId && event.eventType==AccessibilityEvent.TYPE_VIEW_SCROLLED){
                clearNumbers();AccessibilityBridge.invalidateSelection()
            }
        }
        if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            if(event.eventTime<AccessibilityBridge.lastWindowEvent)return
            if(!com.airgesture.app.mode.WindowContextPolicy.changesForeground(packageName,event.className?.toString(),event.windowId,event.isFullScreen)){GlobalSession.log.add("transient_system_announcement_ignored=1");return}
            AccessibilityBridge.lastWindowEvent=event.eventTime
            if(GlobalSession.running)GlobalSession.log.add("window_context package=$packageName class=${event.className} window=${event.windowId} fullscreen=${event.isFullScreen} event_age_ms=${android.os.SystemClock.uptimeMillis()-event.eventTime}")
            AccessibilityBridge.foregroundPackage=packageName
            AccessibilityBridge.contextVersion++
            drag.release()
        }
    }
    override fun onInterrupt() { AccessibilityBridge.interrupt();clearNumbers();AccessibilityBridge.invalidateSelection();drag.release();social.cancel(); GlobalSession.actionsEnabled=false; GlobalSession.log.add("accessibility_interrupted=1 waiting_for_explicit_resume=1") }
    override fun onUnbind(intent: Intent?): Boolean { clearNumbers();AccessibilityBridge.invalidateSelection();AccessibilityBridge.disconnect(this); return super.onUnbind(intent) }
    override fun onDestroy() { clearNumbers();drag.release();social.cancel(); AccessibilityBridge.disconnect(this); super.onDestroy() }

    internal fun perform(action: SystemAction, callback: (ActionResult) -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!GlobalSession.running || GlobalSession.status.value?.paused == true || !GlobalSession.actionsEnabled ||
            !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        if(action.kind==ActionKind.CLICK && action.nodeSelection!=null){
            if(android.os.Build.VERSION.SDK_INT>=33)clearCache()
            val root=rootInActiveWindow ?: return false
            @Suppress("DEPRECATION") val windowId=try{root.windowId}finally{root.recycle()}
            val target=scanner.target(action.nodeSelection,windowId,AccessibilityBridge.contextVersion) ?: return false
            // Resolve bounds before ACTION_CLICK: a synchronous event may invalidate the snapshot.
            val center=com.airgesture.app.cursor.CursorPoint(target.second.exactCenterX(),target.second.exactCenterY())
            if(target.first.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){
                callback(ActionResult.COMPLETED);return true
            }
            if(scanner.snapshot?.id!=action.nodeSelection.snapshotId)return false
            return perform(action.copy(point=center,nodeSelection=null),callback)
        }
        if(action.kind==ActionKind.LIKE){
            if(AccessibilityBridge.foregroundPackage !in SocialActionDispatcher.supported)return false
            return perform(action.copy(kind=ActionKind.DOUBLE_CLICK,point=com.airgesture.app.cursor.CursorPoint(action.screen.width*.5f,action.screen.height*.5f)),callback)
        }
        if(action.kind==ActionKind.DISLIKE)return social.dislike(action,callback)
        val global = when (action.kind) {
            ActionKind.BACK -> GLOBAL_ACTION_BACK
            ActionKind.HOME -> GLOBAL_ACTION_HOME
            ActionKind.RECENTS -> GLOBAL_ACTION_RECENTS
            else -> null
        }
        if (global != null) {
            val success = performGlobalAction(global)
            callback(if (success) ActionResult.COMPLETED else ActionResult.REJECTED)
            return success
        }
        if(action.kind in setOf(ActionKind.DRAG_START,ActionKind.DRAG_MOVE,ActionKind.DRAG_END))return drag.submit(action,callback)
        if(drag.active || action.kind==ActionKind.PALM_FLIP)return false
        val path=Path()
        val builder=GestureDescription.Builder()
        if(action.kind in setOf(ActionKind.CLICK,ActionKind.DOUBLE_CLICK)) {
            val point=action.point ?: return false
            if(!action.screen.contains(point))return false
            path.moveTo(point.x,point.y)
            builder.addStroke(GestureDescription.StrokeDescription(path,0,65))
            if(action.kind==ActionKind.DOUBLE_CLICK)builder.addStroke(GestureDescription.StrokeDescription(path,145,65))
        } else {
            val swipe=SwipeConfig.forAction(action.kind) ?: return false
            if(!swipe.valid)return false
            val w=action.screen.width;val h=action.screen.height
            path.moveTo(w*swipe.startX,h*swipe.startY);path.lineTo(w*swipe.endX,h*swipe.endY)
            builder.addStroke(GestureDescription.StrokeDescription(path,0,swipe.duration))
        }
        return dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { callback(ActionResult.COMPLETED) }
            override fun onCancelled(gestureDescription: GestureDescription?) { callback(ActionResult.CANCELLED) }
        }, Handler(Looper.getMainLooper()))
    }
    internal fun proofActiveWindowPackage():String? {
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE==0)return null
        val root=rootInActiveWindow ?: return null
        @Suppress("DEPRECATION") return try{root.packageName?.toString()}finally{root.recycle()}
    }
    internal fun releaseDrag(){drag.release()}
    internal fun captureNumbers(screen:ScreenGeometry,maxTargets:Int=20):com.airgesture.app.selection.NumberSnapshot? {
        if(!GlobalSession.running || !GlobalSession.actionsEnabled || !getSystemService(PowerManager::class.java).isInteractive || getSystemService(KeyguardManager::class.java).isKeyguardLocked)return null
        // Custom feed cards are often omitted from the default accessibility tree.
        // Include their native nodes for this explicitly requested selection only.
        val info=serviceInfo
        if(selectionBaseFlags==null)selectionBaseFlags=info.flags
        serviceInfo=info.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
        // Idle mode does not subscribe to content updates. Discard cached loading
        // placeholders before an explicit request to show the current targets.
        if(android.os.Build.VERSION.SDK_INT>=33)clearCache()
        val result=scanner.scan(rootInActiveWindow,screen,AccessibilityBridge.contextVersion,maxTargets)
        if(result!=null)serviceInfo=serviceInfo.apply{eventTypes=eventTypes or AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED} else clearNumbers()
        return result
    }
    internal fun clearNumbers(){scanner.clear();serviceInfo?.let{serviceInfo=it.apply{eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;selectionBaseFlags?.let{original->flags=original}}};selectionBaseFlags=null}
    internal fun proveOwnMenu(action:SystemAction,callback:(ActionResult)->Unit):Boolean {
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE==0 || AccessibilityBridge.foregroundPackage!=packageName || !GlobalSession.running || !GlobalSession.actionsEnabled || action.kind!=ActionKind.DISLIKE)return false
        return social.dislike(action,callback,ownProof=true)
    }
}

object AccessibilityBridge {
    internal var lastWindowEvent=0L
    var foregroundPackage:String?=null
        internal set
    fun releaseDrag(){service.get()?.releaseDrag()}
    private val interruptVersion=MutableLiveData(0L)
    val interruptions:LiveData<Long> = interruptVersion
    internal fun interrupt(){interruptVersion.value=(interruptVersion.value ?: 0)+1}
    private val selectionVersion=MutableLiveData(0L)
    val selectionInvalidated:LiveData<Long> = selectionVersion
    internal fun invalidateSelection(){selectionVersion.value=(selectionVersion.value ?: 0)+1}
    internal fun proofActiveWindowPackage()=service.get()?.proofActiveWindowPackage()
    fun captureNumbers(screen:ScreenGeometry,maxTargets:Int=20)=service.get()?.captureNumbers(screen,maxTargets)
    fun clearNumbers(){service.get()?.clearNumbers()}
    var contextVersion=0L
        internal set
    private var service = WeakReference<AirAccessibilityService>(null)
    private val state = MutableLiveData(false)
    val connected: LiveData<Boolean> = state
    val available get() = state.value == true && service.get() != null
    internal fun connect(value: AirAccessibilityService) {
        service = WeakReference(value); state.value = true
        GlobalSession.log.add("accessibility_connected=1 node_lookup=explicit_selection_or_dislike_only")
    }
    internal fun disconnect(value: AirAccessibilityService) {
        if (service.get() !== value) return
        value.releaseDrag(); service.clear(); state.value = false; foregroundPackage=null
        GlobalSession.actionsEnabled = false
        GlobalSession.log.add("accessibility_connected=0")
    }
    fun dispatch(action: SystemAction, callback: (ActionResult) -> Unit) = service.get()?.perform(action, callback) ?: false
    internal fun proveOwnMenu(action:SystemAction,callback:(ActionResult)->Unit)=service.get()?.proveOwnMenu(action,callback) ?: false
}
