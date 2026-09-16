package com.airgesture.app.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.airgesture.app.MainActivity
import com.airgesture.app.accessibility.*
import com.airgesture.app.cursor.CoordinateMapper
import com.airgesture.app.gesture.GestureStateMachine
import com.airgesture.app.gesture.HandObservation
import com.airgesture.app.gesture.HybridGestureController
import com.airgesture.app.R
import com.airgesture.app.camera.CameraController
import com.airgesture.app.cursor.CursorController
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.cursor.GlobalCursorOverlay
import com.airgesture.app.debug.PerformanceSnapshot
import com.airgesture.app.debug.SessionHealth
import com.airgesture.app.settings.GestureConfig
import com.airgesture.app.settings.GestureSettings
import com.airgesture.app.vision.HandTrackingGate
import com.airgesture.app.vision.TrackingFrame
import com.airgesture.app.vision.TrackingState
import java.util.Locale
import com.airgesture.app.mode.*
import com.airgesture.app.eye.*
import com.airgesture.app.settings.VisionMode
import com.airgesture.app.command.*
import com.airgesture.app.session.*
import com.airgesture.app.voice.*

@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
class GestureForegroundService : Service(), LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val handler = Handler(Looper.getMainLooper())
    private var camera: CameraController? = null
    private var overlay: GlobalCursorOverlay? = null
    private val cursor = CursorController()
    private val gate = HandTrackingGate()
    private val gestures = GestureStateMachine()
    private val hybrid = HybridGestureController()
    private var latestGestureMessage = ""
    private var diagnostics = ""
    private var lastAction = "尚无操作"
    private var sceneFeedback=""
    private var sceneFeedbackUntil=0L
    private var pointerResult:ActionResult?=null
    private var pointerResultUntil=0L
    private var requestedActions = false
    private var mode=AssistantMode.MOUSE
    private var sessionMode=AssistantMode.MOUSE
    private var voiceRequested=false
    private var channelVoice=true
    private var channelGestures=false
    private var voiceSession:HybridControlSession?=null
    private var cameraPrepared=false
    private val usesHybrid get()=mode==AssistantMode.VIDEO || mode==AssistantMode.GLOBAL
    private lateinit var profileStore:ProfileStore
    private var profiles:List<AppProfile> = emptyList()
    private var activeProfile:AppProfile?=null
    private var captureAllowed=true
    private var lastGateDiagnostic:String?=null
    private val governor=PowerGovernor()
    private val reader=GazeReader()
    private var thermalStatus=0
    private var batteryTemperature=0f
    private var previousWorkState:WorkState?=null
    private var lastContextVersion=0L
    private var lastAccessibilityAvailable=false
    private var inferenceConfig = GestureConfig()
    private var normalFps = 20
    private var lastHealthLog = 0L
    private var health:SessionHealth?=null
    private var environmentSummary=""
    private val actions = ActionCoordinator({ SystemClock.uptimeMillis() }, receipt = { receipt ->
        health?.action(receipt.result)
        voiceSession?.receipt(receipt)
        GlobalSession.log.add("action_id=${receipt.id} action=${receipt.kind} result=${receipt.result} reason=${receipt.reason} latency_ms=${receipt.elapsedMs}")
        lastAction = "${receipt.kind} / ${receipt.result} / ${receipt.elapsedMs} ms"
        if(receipt.kind==ActionKind.CLICK){pointerResult=receipt.result;pointerResultUntil=SystemClock.uptimeMillis()+750}
        if(receipt.result!=ActionResult.DISPATCHED){
            sceneFeedback=if(receipt.result==ActionResult.COMPLETED)"已执行 · ${receipt.kind.label}" else "操作未完成 · 请重试"
            sceneFeedbackUntil=SystemClock.uptimeMillis()+1400
        }
    })
    private val systemActions=SystemActionController(actions)
    private var running = false
    private var paused = false
    private var stopping = false
    private var resourcesReleased = false
    private var lastFrame = 0L
    private var lastLog = 0L
    private var rotationChangedAt = 0L
    private var state: TrackingState? = null
    private var endMessage = "全局光标已停止，相机已释放"
    private var displayManager: DisplayManager? = null
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { finishSession("已锁屏，全局光标和相机已停止") }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) finishSession("显示器已断开")
        }
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY && running) {
                syncDisplay()
            }
        }
    }
    private var lastRotation = -1
    private var lastWidth = 0
    private var lastHeight = 0
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running || stopping) return
            if (!hasPermissions()) { finishSession("权限已关闭，全局光标已停止"); return }
            if (!isScreenAvailable()) { finishSession("已锁屏，全局光标和相机已停止"); return }
            syncDisplay()
            syncApp()
            voiceSession?.tick()
            if(voiceRequested && voiceSession?.let{!it.voiceEnabled && !it.gesturesEnabled}==true){finishSession("两个开关均已关闭");return}
            actions.expire()
            if (lastAccessibilityAvailable && !AccessibilityBridge.available && requestedActions) {
                GlobalSession.actionsEnabled = false
                actions.reset(); gestures.resetTracking()
                android.widget.Toast.makeText(this@GestureForegroundService,"无障碍已断开，操作已停止；请重新开启会话",android.widget.Toast.LENGTH_LONG).show()
            }
            lastAccessibilityAvailable=AccessibilityBridge.available
            if(lastContextVersion!=AccessibilityBridge.contextVersion) {
                lastContextVersion=AccessibilityBridge.contextVersion
                resetGestureContext()
            }
            val now = SystemClock.uptimeMillis()
            if(now-lastHealthLog>=10_000) {
                lastHealthLog=now
                val thermal=if(Build.VERSION.SDK_INT>=29)getSystemService(PowerManager::class.java).currentThermalStatus else 0
                thermalStatus=thermal

                val battery=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temperature=(battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0) ?: 0)/10f
                batteryTemperature=temperature
                inferenceConfig.maxInferenceFps=governor.fps(if(paused)minOf(normalFps,10) else normalFps,thermal,batteryTemperature)
                camera?.setLowPowerCapture(governor.state==WorkState.WATCH || paused || batteryTemperature>=42f)
                val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
                environmentSummary="电量 $level% · 电池温度 $temperature°C · 热状态 $thermal · 上限 ${inferenceConfig.maxInferenceFps} FPS"
                GlobalSession.log.add("${health?.summary(now)} battery_level=$level battery_temperature_c=$temperature thermal_status=$thermal inference_cap=${inferenceConfig.maxInferenceFps}")
            }
            if (now - lastFrame > 250 && !paused && governor.state==WorkState.ACTIVE) {
                resetTracking(TrackingState.STALE)
                publishTracking()
            }
            if (camera!=null && now - lastFrame > 10_000) {
                if(voiceRequested)voiceSession?.visualFailure("相机长时间没有结果")
                else {finishSession("相机长时间没有结果，已停止；请回到应用重新开始");return}
            }
            if(voiceRequested && camera==null){
                val s=ControlSessionState.current
                if(AccessibilityBridge.foregroundPackage==packageName || s.mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))overlay?.hide()
                else {
                    val text=when {
                        s.mode==ControlMode.PAUSED->"已暂停 · 通知中点继续"
                        s.audio.state=="STARTING"->"正在准备麦克风…"
                        now-s.lastKeywordAt<2500->when(s.lastDecision){"COMPLETED"->"已发送 · ${s.lastKeyword}";"COOLDOWN"->"间隔过短 · 请再说";"NEEDS_WAKE_WORD"->"请先说唤醒词";else->"听到 · ${s.lastKeyword}"}
                        s.audio.state=="ACTIVE"->if(voiceSession?.config?.activation==com.airgesture.app.voice.VoiceActivation.DIRECT)"语音就绪 · 直接说指令" else "请说 · ${voiceSession?.config?.wakeWord}"
                        else->"麦克风停止 · 请恢复"
                    }
                    overlay?.showSceneStatus(text,s.audio.state=="ACTIVE" && !paused,paused)
                }
            }
            handler.postDelayed(this, if(voiceRequested && camera==null)300 else 100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registry.currentState = Lifecycle.State.CREATED
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "全局光标运行状态", NotificationManager.IMPORTANCE_LOW))
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        displayManager = getSystemService(DisplayManager::class.java).also { it.registerDisplayListener(displayListener, handler) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHANNELS -> if(!stopping){
                channelVoice=intent.getBooleanExtra(EXTRA_VOICE,true)
                channelGestures=intent.getBooleanExtra(EXTRA_GESTURES,false)
                if(!channelVoice && !channelGestures){finishSession("语音和手势均已关闭");return START_NOT_STICKY}
                if(running && voiceRequested && voiceSession!=null){
                    try{check(hasPermissions() && isScreenAvailable());promoteForeground();voiceSession?.setChannels(channelVoice,channelGestures)}
                    catch(e:Exception){finishSession("开关未能启用，请回到权限管理检查")}
                }else{
                    val start=Intent(intent).setAction(ACTION_START).putExtra(EXTRA_UNIFIED,true)
                    return onStartCommand(start,flags,startId)
                }
            }
            ACTION_START -> if (!stopping) {
                voiceSession?.close();voiceSession=null
                channelVoice=intent.getBooleanExtra(EXTRA_VOICE,false)
                channelGestures=intent.getBooleanExtra(EXTRA_GESTURES,false)
                voiceRequested=channelVoice || intent.getBooleanExtra(EXTRA_UNIFIED,false)
                requestedActions = intent.getBooleanExtra(EXTRA_CONTROL, false)
                mode=AssistantMode.entries.firstOrNull{it.name==intent.getStringExtra(EXTRA_MODE)} ?: AssistantMode.MOUSE
                sessionMode=mode
                if(running){
                    try {
                    check(hasPermissions() && isScreenAvailable()){ "请解锁并检查控制权限" }
                    // A visible Activity may change foreground-service types. Re-establish
                    // microphone/camera eligibility there; never elevate from a voice callback.
                    promoteForeground()
                    if(voiceRequested)startVoiceSession()
                    paused=false
                    profiles=profileStore.profiles()
                    GlobalSession.log.add("session_restarted mode=$mode requested_actions=$requestedActions")
                    syncApp(force=true)
                    }catch(e:Exception){finishSession("控制未启动：${e.message ?: e.javaClass.simpleName}")}
                }else beginSession()
            }
            ACTION_PAUSE -> if (running && !stopping) {
                voiceSession?.let{it.execute(ControlCommand(if(it.paused)CommandKind.RESUME_CONTROL else CommandKind.PAUSE_CONTROL,SystemClock.uptimeMillis()));return START_NOT_STICKY}
                paused = !paused
                GlobalSession.actionsEnabled = requestedActions && !paused && captureAllowed && AccessibilityBridge.available
                resetTracking()
                gestures.setPaused(paused)
                actions.reset()
                GlobalSession.log.add("global_paused=$paused")
                GlobalSession.publish(GlobalStatus(true, paused, if (paused) "已暂停光标；相机仍运行，可从通知继续" else "请把整只手放入镜头并稳住片刻"))
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            } else stopSelf()
            ACTION_RELOAD -> if (running && !stopping) {
                cursor.configure(GestureSettings(this).load());profiles=profileStore.profiles();configureGestures();resetTracking();syncApp()
            } else stopSelf()
            ACTION_VIDEO_ASSIST -> if(running && !stopping && isScreenAvailable()){
                voiceSession?.toggleVideoAssist()
            } else if(!running)stopSelf()
            ACTION_STOP -> finishSession("全局光标已停止，相机已释放")
            else -> stopSelf() // Never restart capture after process death or a null sticky intent.
        }
        return START_NOT_STICKY
    }

    private fun beginSession() {
        try {
            // Enter foreground promptly even if permission was revoked between button tap and dispatch.
            promoteForeground()
            check(hasPermissions()) { if(voiceRequested)"麦克风或悬浮窗权限尚未开启" else "相机或悬浮窗权限尚未开启" }
            check(isScreenAvailable()) { "请解锁后从应用开始" }
            val globalOverlay = GlobalCursorOverlay(this).also { overlay = it; it.attach() }
            lastRotation = globalOverlay.rotation
            cursor.configure(GestureSettings(this).load())
            profileStore=ProfileStore(this);profiles=profileStore.profiles();configureGestures()
            running = true
            health=SessionHealth(SystemClock.uptimeMillis())
            GlobalSession.actionsEnabled = requestedActions && AccessibilityBridge.available
            lastContextVersion=AccessibilityBridge.contextVersion
            lastAccessibilityAvailable=AccessibilityBridge.available
            lastFrame = SystemClock.uptimeMillis()
            registry.currentState = Lifecycle.State.STARTED
            GlobalSession.publish(GlobalStatus(true, false, "正在启动全局光标，请保持整只手入镜"))
            val bounds = globalOverlay.bounds()
            lastWidth = bounds.x
            lastHeight = bounds.y
            cursor.setLandscape(bounds.x>bounds.y)
            if(voiceRequested)startVoiceSession()
            GlobalSession.log.add("global_started=1 width=${bounds.x} height=${bounds.y} rotation=$lastRotation actions_enabled=${GlobalSession.actionsEnabled} mode=$mode")
            syncApp(force=true)
            publishStatus()
            handler.postDelayed(watchdog, 300)
        } catch (e: Exception) {
            GlobalSession.log.add("global_start_failed=${e.javaClass.simpleName}")
            finishSession("全局光标启动失败：${e.message ?: e.javaClass.simpleName}；请回到应用重试")
        }
    }

    private fun onFrame(frame: TrackingFrame, metrics: PerformanceSnapshot) {
        if (!running || stopping || !captureAllowed) return
        syncApp();if(!captureAllowed)return
        val now = SystemClock.uptimeMillis()
        lastFrame = now
        if(lastContextVersion!=AccessibilityBridge.contextVersion){lastContextVersion=AccessibilityBridge.contextVersion;resetGestureContext()}
        val present=frame.hand!=null || frame.face!=null
        voiceSession?.handFrame(frame.hand!=null,frame.inferenceMs)
        val work=governor.update(now,captureAllowed,present,paused)
        inferenceConfig.maxInferenceFps=governor.fps(if(paused)minOf(normalFps,10) else normalFps,thermalStatus,batteryTemperature)
        camera?.setLowPowerCapture(work==WorkState.WATCH || paused || batteryTemperature>=42f)
        if(work!=previousWorkState){previousWorkState=work;GlobalSession.log.add("work_state=$work cap=${inferenceConfig.maxInferenceFps} mode=$mode")}
        health?.frame(now,now-frame.timestampMs,present)
        syncDisplay()
        if (frame.timestampMs <= rotationChangedAt + 250) return
        if(frame.source==VisionMode.FACE){onGaze(frame,now);return}
        if(mode==AssistantMode.READING && frame.hand!=null)reader.reset()
        val points = frame.hand?.normalized?.map { CursorPoint(it.x, it.y) }
        val accepted = gate.update(points, frame.timestampMs, now, frame.width, frame.height)
        val mapped = cursor.update(accepted, frame.timestampMs, now)
        val observation = if (accepted != null && mapped != null && points != null)
            HandObservation.from(points,frame.width,frame.height,frame.timestampMs,mapped) else null
        if(observation==null)AccessibilityBridge.releaseDrag()
        val combined=if(usesHybrid)hybrid.update(observation,now) else null
        val decision = combined?.gesture ?: gestures.update(observation,now)
        latestGestureMessage = decision.message
        if (decision.pauseChanged) {
            voiceSession?.let{
                // A gesture pause cannot disable the independent voice channel. With the
                // camera off, a second fist could never resume the old all-channel pause.
                it.execute(ControlCommand(CommandKind.STOP_MOUSE,SystemClock.uptimeMillis()))
                GlobalSession.log.add("gesture_pause=1 gesture_channel_off=1 voice_preserved=1")
                android.widget.Toast.makeText(this,"手势已关闭，语音继续；说开启手势可恢复",android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            paused = if(usesHybrid)hybrid.paused else gestures.paused
            GlobalSession.actionsEnabled = requestedActions && !paused && captureAllowed && AccessibilityBridge.available
            actions.reset();AccessibilityBridge.releaseDrag()
            GlobalSession.log.add("global_paused=$paused source=FIST")
            android.widget.Toast.makeText(this,if(paused) "隔空控制已暂停，张手后再握拳恢复" else "隔空控制已恢复",android.widget.Toast.LENGTH_SHORT).show()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification())
            publishStatus()
        }
        if(decision.action==ActionKind.CLICK){pointerResult=ActionResult.DISPATCHED;pointerResultUntil=now+1500}
        val point = if (paused) null else decision.frozenCursor ?: mapped
        val screenPoint = try {
            if(usesHybrid){
                val label=when {paused->"已暂停 · 握拳恢复";now<sceneFeedbackUntil->sceneFeedback
                    !GlobalSession.actionsEnabled->"仅识别 · 请重新开始"
                    observation!=null->hybrid.status;gate.state==TrackingState.EDGE->"整只手移回镜头";gate.state==TrackingState.ACQUIRING->"看到手了，请稳住";else->"未看到手，请入镜"}
                val confirmation=when{
                    decision.confirmation==1->1
                    now<pointerResultUntil && pointerResult!=null->when(pointerResult){ActionResult.COMPLETED->2;ActionResult.DISPATCHED->3;else->-1}
                    decision.confirmation==2->3
                    else->0
                }
                if(combined?.showPointer==true && !paused)overlay?.show(point,immediate=decision.frozenCursor!=null,confirmation=confirmation)
                else {overlay?.showSceneStatus(label,hybrid.ready && GlobalSession.actionsEnabled,paused);null}
            }else overlay?.show(point.takeIf{mode==AssistantMode.MOUSE})
        } catch (e: Exception) {
            finishSession("悬浮窗已失效，请回到应用重新开始"); return
        }
        val currentOverlay = overlay ?: return
        decision.action?.let { kind ->
            if(combined!=null){
                combined.mapped(activeProfile)?.let{(action,target)->dispatchAction(action,target,frame.timestampMs)}
            }else {
                val mappedAction=if(mode==AssistantMode.MOUSE)kind else ModeRouter.map(kind,activeProfile)?.kind
                if(mappedAction!=null){
                    val normalized=if(mode==AssistantMode.MOUSE)decision.point else CursorPoint(.5f,.5f)
                    dispatchAction(mappedAction,normalized,frame.timestampMs)
                }
            }
        }
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0 && usesHybrid){
            GlobalSession.log.add("gesture_pipeline t=${frame.timestampMs} accepted=${observation!=null} tracking=${gate.state} pose=${observation?.pagePose} point_pose=${observation?.pointerPose} four=${observation?.fourFingerPose} two=${observation?.twoFingerPose} fingers=${observation?.fingerDirection} thumb_only=${observation?.thumbOnly} thumb_local=${observation?.thumbLocal} home_pose=${observation?.homePose} pointer=${hybrid.pointer} pinch=${observation?.pinchRatio} palm_x=${observation?.palm?.x} palm_y=${observation?.palm?.y} cursor_x=${observation?.cursor?.x} cursor_y=${observation?.cursor?.y} ready=${hybrid.ready} confirmation=${decision.confirmation} frozen_x=${decision.frozenCursor?.x} frozen_y=${decision.frozenCursor?.y} action=${decision.action} enabled=${GlobalSession.actionsEnabled} age_ms=${now-frame.timestampMs} state=${decision.message}")
        }
        publishTracking()
        if (now - lastLog >= 1000) {
            lastLog = now
            GlobalSession.log.add(String.format(Locale.US,
                "global_frame tracking=%s hand=%d x=%s y=%s screen_x=%s screen_y=%s ai_fps=%.1f camera_fps=%.1f age_ms=%d inference_ms=%d prepare_ms=%d delivery_ms=%d cap=%d thermal=%d",
                gate.state.name, if (points == null) 0 else 1, point?.x, point?.y, screenPoint?.x, screenPoint?.y,
                metrics.inferenceFps, metrics.cameraFps, now - frame.timestampMs,frame.inferenceMs,
                frame.processingMs-frame.inferenceMs,now-frame.timestampMs-frame.processingMs,inferenceConfig.maxInferenceFps,thermalStatus))
            diagnostics = String.format(Locale.US,"Camera %.1f FPS | AI %.1f FPS\n推理 %d ms | 输入帧龄 %d ms\n分析 %d | 主动跳帧 %d\nPinch %.3f | %s\n%s",
                metrics.cameraFps,metrics.inferenceFps,frame.inferenceMs,now-frame.timestampMs,metrics.received,metrics.skipped,
                observation?.pinchRatio ?: -1f,gestures.pinchState,lastAction) + "\n挥动速度 ${gestures.lastSwipeVelocity}\n$environmentSummary"
            publishStatus()
        }
    }


    private fun configureGestures(){
        val settings=GestureSettings(this).loadActions()
        hybrid.configure(settings)
        gestures.configure(if(mode==AssistantMode.MOUSE)settings else settings.copy(pinch=mode==AssistantMode.VIDEO || activeProfile?.mappings?.get(GestureInput.PINCH)?.kind!=null,swipeUp=true,swipeDown=true,drag=false,doublePinch=mode==AssistantMode.VIDEO || activeProfile?.mappings?.get(GestureInput.DOUBLE_PINCH)?.kind!=null,
            palmFlip=mode==AssistantMode.VIDEO || activeProfile?.mappings?.get(GestureInput.PALM_FLIP)?.kind!=null,sceneSwipes=true,pushExperimental=false))
    }
    private fun startCapture(){
        val base=GestureSettings(this).loadPerformance().config()
        inferenceConfig=if(mode==AssistantMode.READING)base.copy(analysisWidth=640,analysisHeight=480,
            visionMode=VisionMode.FACE,handFallback=activeProfile?.handFallback==true,useGpu=false) else base
        normalFps=if(mode==AssistantMode.READING)minOf(15,base.maxInferenceFps) else base.maxInferenceFps
        lastFrame=SystemClock.uptimeMillis()
        val captureSession=voiceSession
        lateinit var capture:CameraController
        capture=CameraController(this,this,null,inferenceConfig,{message->if(voiceRequested){voiceSession?.visualFailure(message)}else finishSession(message)},::onFrame,{overlay?.rotation ?: 0},onResources={bound,model->
            // An older analyzer closes asynchronously. It must not overwrite the
            // resource state of a camera or voice session that has since replaced it.
            if(voiceSession===captureSession && (camera===capture || camera==null))captureSession?.resources(bound,model)
        })
        camera=capture;capture.start()
        GlobalSession.log.add("capture_started mode=$mode model=${inferenceConfig.visionMode} hand_fallback=${inferenceConfig.handFallback}")
    }
    private fun syncApp(force:Boolean=false){
        if(!running || stopping)return
        val packageName=AccessibilityBridge.foregroundPackage
        val profile=profiles.firstOrNull{it.enabled && it.packageName==packageName}
        val resolved=ModeRouter.resolve(sessionMode,profile,profileStore.autoModes)
        val modeChanged=resolved!=mode
        mode=resolved
        val allowed=(!voiceRequested || voiceSession?.visual==true) && ModeRouter.allowed(mode,packageName,profiles,profileStore.mouseWhitelist) &&
            (mode==AssistantMode.MOUSE || AccessibilityBridge.available)
        val diagnostic="package=$packageName mode=$mode profile=${profile?.name} allowed=$allowed accessibility=${AccessibilityBridge.available} enabled_profiles=${profiles.count{it.enabled}}"
        if(lastGateDiagnostic!=diagnostic){lastGateDiagnostic=diagnostic;GlobalSession.log.add("app_gate_context $diagnostic")}
        val changed=activeProfile?.packageName!=profile?.packageName
        activeProfile=profile
        if(voiceRequested)GlobalSession.actionsEnabled=requestedActions && AccessibilityBridge.available && voiceSession?.let{(it.micActive || it.visual) && !it.paused}==true
        if(!force && captureAllowed==allowed && !changed && !modeChanged)return
        val wasAllowed=captureAllowed
        captureAllowed=allowed
        if(force || modeChanged || changed)configureGestures()
        val restartCapture=force || modeChanged || wasAllowed!=allowed || (allowed && camera==null)
        if(restartCapture){resetTracking();actions.reset()} else resetGestureContext()
        GlobalSession.actionsEnabled=requestedActions && !paused && AccessibilityBridge.available && if(voiceRequested)voiceSession?.let{it.micActive || it.visual}==true else allowed
        if(restartCapture){
            camera?.close();camera=null
            governor.update(SystemClock.uptimeMillis(),allowed,false,paused)
            if(allowed)startCapture()
        }
        GlobalSession.log.add("app_gate allowed=$allowed profile=${profile?.name ?: "unlisted"} camera_active=${camera!=null}")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification())
        publishStatus()
    }
    private fun dispatchAction(kind:ActionKind,point:CursorPoint?,time:Long){
        val current=overlay ?: return
        val bounds=current.bounds();val geometry=ScreenGeometry(bounds.x,bounds.y,current.rotation)
        val target=point?.let{CoordinateMapper().toPixels(it,bounds.x,bounds.y,current.inset)}
        if(kind!=ActionKind.DRAG_MOVE)GlobalSession.log.add("gesture=$kind x=${target?.x} y=${target?.y} mode=$mode")
        if(kind==ActionKind.DRAG_END){AccessibilityBridge.releaseDrag();return}
        val accepted=systemActions.submit(ControlCommand.gesture(kind,point,time),GlobalSession.actionsEnabled && !paused && captureAllowed && isScreenAvailable(),geometry,current.inset,dispatch=AccessibilityBridge::dispatch)
        if(accepted && kind !in setOf(ActionKind.DRAG_MOVE,ActionKind.DRAG_START))
            android.widget.Toast.makeText(this,"${mode.label} · ${kind.label}",android.widget.Toast.LENGTH_SHORT).show()
    }
    private fun onGaze(frame:TrackingFrame,now:Long){
        val landscape=lastWidth>lastHeight
        val calibration=GazeStore(this).load(landscape)
        val sample=GazeFeatures.from(frame.face,frame.timestampMs,frame.width,frame.height,now)
        val region=if(sample!=null)calibration?.region(sample) ?: GazeRegion.UNKNOWN else GazeRegion.UNKNOWN
        overlay?.showReadingStatus(!paused && region!=GazeRegion.UNKNOWN)
        reader.dwellMs=activeProfile?.gazeDwellMs ?: 550
        val page=reader.update(if(paused)GazeRegion.UNKNOWN else region,frame.timestampMs,now)
        if(page && activeProfile?.gazeEnabled==true)dispatchAction(ActionKind.SWIPE_UP,null,frame.timestampMs)
        val message=if(calibration==null)"请先回应用完成当前方向的眼控校准" else if(region==GazeRegion.UNKNOWN)"请正对屏幕，保持校准时的距离和头部位置" else "${region.name} · ${reader.message}"
        if(now-lastLog>=500){
            lastLog=now
            GlobalSession.log.add("gaze_region=$region calibrated=${calibration!=null} frame_age=${now-frame.timestampMs} page_request=$page")
            GlobalSession.publish(GlobalStatus(true,paused,message,"${governor.state} · 上限 ${inferenceConfig.maxInferenceFps} FPS\n$environmentSummary\n$lastAction"))
        }
    }

    /** On OPPO the display rotation event arrives before WindowContext's new bounds.
     * Recheck both on subsequent callbacks; discard transition frames for each change. */
    private fun syncDisplay() {
        val current = overlay ?: return
        val rotation = current.rotation
        val bounds = current.bounds()
        if (rotation == lastRotation && bounds.x == lastWidth && bounds.y == lastHeight) return
        lastRotation = rotation
        lastWidth = bounds.x
        lastHeight = bounds.y
        rotationChangedAt = SystemClock.uptimeMillis()
        resetTracking()
        cursor.setLandscape(bounds.x>bounds.y)
        camera?.updateRotation(rotation)
        GlobalSession.log.add("global_display rotation=$rotation width=${bounds.x} height=${bounds.y}")
    }

    private fun publishTracking() {
        if (state == gate.state) return
        state = gate.state
        GlobalSession.log.add("global_tracking=${gate.state.name} cursor_allowed=${gate.state == TrackingState.TRACKING}")
        publishStatus()
    }
    private fun publishStatus() {
        voiceSession?.let{session->
            val s=ControlSessionState.current
            GlobalSession.publish(GlobalStatus(true,session.paused,s.message,"语音 ${s.audio.state} · 相机 ${if(s.cameraBound)"开启" else "关闭"}\nKWS ${s.audio.inferenceCount} 次 · 手部 ${s.handFrames} 帧\n$environmentSummary"));return
        }
        val message = if(!captureAllowed)"等待已启用的应用 · 相机已释放" else if (paused) {if(gestures.settings.fistPause) "已暂停 · 张手后再握拳恢复" else "已暂停 · 点应用或通知中的继续"} else if (gate.state != TrackingState.TRACKING) gate.state.message
            else latestGestureMessage + if(GlobalSession.actionsEnabled) "" else " · 仅光标"
        GlobalSession.publish(GlobalStatus(true,paused,message,diagnostics))
    }
    private fun resetGestureContext(){
        // A new target window cancels pending input, but the same tracked hand keeps its cursor.
        gestures.resetTracking();hybrid.resetTracking();reader.reset();AccessibilityBridge.releaseDrag()
        // An already dispatched tap can open this window before its completion callback.
        // Keep that receipt alive; there is no queued action to carry into the new window.
        GlobalSession.log.add("gesture_context_reset=1 cursor_tracking_preserved=1")
    }
    private fun resetTracking(reason: TrackingState = TrackingState.MISSING) {
        pointerResult=null;pointerResultUntil=0L
        cursor.reset(); gate.reset(reason); gestures.resetTracking(); gestures.setPaused(paused);hybrid.resetTracking();hybrid.setPaused(paused); reader.reset(); AccessibilityBridge.releaseDrag(); overlay?.hide()
    }
    private fun hasPermissions() = Settings.canDrawOverlays(this) &&
        (!voiceRequested || !channelVoice || ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) &&
        (voiceRequested && !channelGestures || ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)

    private fun promoteForeground(){
        cameraPrepared=ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(Build.VERSION.SDK_INT>=30){
            val types=if(voiceRequested) (if(channelVoice)ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) or (if(cameraPrepared)ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0) else ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID,notification(),types)
        }else startForeground(NOTIFICATION_ID,notification())
    }
    private fun startVoiceSession(){
        mode=AssistantMode.GLOBAL;sessionMode=mode;paused=false
        voiceSession=HybridControlSession(this,VoiceSettings(this).load(),{
            val current=checkNotNull(overlay);val bounds=current.bounds();ScreenGeometry(bounds.x,bounds.y,current.rotation)
        },{cameraPrepared && ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED},{_->
            paused=voiceSession?.paused==true;syncApp()
        },{command,selection->
            val current=checkNotNull(overlay);val bounds=current.bounds();val screen=ScreenGeometry(bounds.x,bounds.y,current.rotation)
            systemActions.submit(command,GlobalSession.actionsEnabled && isScreenAvailable(),screen,nodeSelection=selection,dispatch=AccessibilityBridge::dispatch)
        },{
            paused=voiceSession?.paused==true;syncApp();publishStatus()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification())
        },executeCommands=requestedActions,initialVoice=channelVoice,initialGestures=channelGestures).also{voiceSession=it;it.start()}
    }
    private fun isScreenAvailable() = getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun finishSession(message: String) {
        if (stopping) return
        stopping = true
        endMessage = message
        releaseResources()
        stopSelf()
    }
    private fun releaseResources() {
        if(resourcesReleased)return
        voiceSession?.close();voiceSession=null
        resourcesReleased=true
        running = false
        GlobalSession.actionsEnabled = false
        actions.reset();AccessibilityBridge.releaseDrag()
        health?.let{GlobalSession.log.add("session_final ${it.summary(SystemClock.uptimeMillis())}")};health=null
        handler.removeCallbacksAndMessages(null)
        camera?.close(); camera = null
        overlay?.close(); overlay = null
        cursor.reset(); gate.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        GlobalSession.publish(GlobalStatus(message = endMessage))
        GlobalSession.log.add("global_stopped=1 camera_release_requested=1")
    }
    override fun onDestroy() {
        releaseResources()
        unregisterReceiver(screenOff)
        displayManager?.unregisterDisplayListener(displayListener)
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, if(voiceRequested)com.airgesture.app.HomeActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String, requestCode: Int) = PendingIntent.getService(this, requestCode,
            Intent(this, GestureForegroundService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=Notification.Builder(this, CHANNEL).setSmallIcon(if(voiceRequested)R.drawable.ic_voice_notification else R.drawable.ic_air_gesture)
            .setContentTitle(if(voiceRequested)"小空 · ${voiceSession?.mode?.label ?: "正在准备语音"}" else if (paused) "${mode.label} · 已暂停" else "${mode.label} · ${if(captureAllowed) "识别中" else "等待已启用的应用"}")
            .setContentText(if(voiceRequested)if(paused)"控制暂停，点继续恢复" else if(voiceSession?.voiceEnabled==false)"仅手势开启 · 麦克风关闭 · 锁屏即停止" else if(captureAllowed)"语音和手势开启 · 说关闭手势释放相机" else "语音开启 · 相机关闭 · 锁屏即停止" else if (paused) "相机仍在本机识别；点停止可释放相机" else if(!captureAllowed)"相机已释放；进入已启用的应用后恢复，锁屏结束" else "点此返回应用，锁屏即停止")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, if (paused) "继续" else "暂停", action(ACTION_PAUSE, 1)).build())
            .addAction(Notification.Action.Builder(null, "停止", action(ACTION_STOP, 2)).build())
        if(voiceRequested && voiceSession?.voiceEnabled==true && !paused)builder.addAction(Notification.Action.Builder(null,if(ControlSessionState.current.videoAssist)"关闭外放辅助" else "外放辅助",action(ACTION_VIDEO_ASSIST,3)).build())
        if(voiceRequested && ControlSessionState.current.videoAssist)builder.setContentText("外放辅助开启 · ${voiceSession?.config?.wakeWord ?: "你好助手"}；关闭辅助可恢复原声")
        else if(voiceRequested && ControlSessionState.current.listening)builder.setContentText("小空在 · 请在 5 秒内说一个命令")
        return builder.build()
    }
    companion object {
        const val ACTION_START = "com.airgesture.app.START_GLOBAL"
        const val ACTION_STOP = "com.airgesture.app.STOP_GLOBAL"
        const val ACTION_PAUSE = "com.airgesture.app.PAUSE_GLOBAL"
        const val ACTION_RELOAD = "com.airgesture.app.RELOAD_GLOBAL"
        const val ACTION_VIDEO_ASSIST = "com.airgesture.app.VIDEO_ASSIST"
        const val ACTION_CHANNELS = "com.airgesture.app.SET_CHANNELS"
        const val EXTRA_UNIFIED = "unified_control_session"
        const val EXTRA_GESTURES = "gesture_channel"
        const val EXTRA_MODE = "assistant_mode"
        const val EXTRA_CONTROL = "enable_control_for_this_session"
        const val EXTRA_VOICE = "voice_control_session"
        private const val CHANNEL = "global_cursor"
        private const val NOTIFICATION_ID = 6
    }
}
