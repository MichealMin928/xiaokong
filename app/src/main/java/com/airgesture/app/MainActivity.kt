package com.airgesture.app

import android.Manifest
import android.app.AlertDialog
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.view.View
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import com.airgesture.app.camera.CameraController
import com.airgesture.app.debug.DebugLog
import com.airgesture.app.debug.DebugOverlay
import com.airgesture.app.debug.PerformanceSnapshot
import com.airgesture.app.settings.GestureConfig
import com.airgesture.app.settings.GestureSettings
import com.airgesture.app.settings.CursorSettings
import com.airgesture.app.settings.Sensitivity
import com.airgesture.app.settings.Smoothness
import com.airgesture.app.settings.SmoothingMethod
import com.airgesture.app.vision.GestureRecognizer
import com.airgesture.app.vision.HandTrackingGate
import com.airgesture.app.vision.TrackingState
import com.airgesture.app.vision.TrackingFrame
import com.airgesture.app.cursor.CursorController
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.cursor.CursorTestView
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.service.GlobalSession
import com.airgesture.app.accessibility.AccessibilityBridge
import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.gesture.GestureStateMachine
import com.airgesture.app.gesture.HandObservation
import com.airgesture.app.cursor.CalibrationSession
import com.airgesture.app.cursor.CalibrationStage
import com.airgesture.app.settings.PerformanceMode
import com.airgesture.app.settings.PerformanceSettings
import java.util.Locale

@androidx.annotation.OptIn(TransformExperimental::class)
class MainActivity : ComponentActivity() {
    private lateinit var camera: CameraController
    private lateinit var preview: PreviewView
    private lateinit var overlay: DebugOverlay
    private lateinit var cursorView: CursorTestView
    private val cursor = CursorController()
    private val trackingGate = HandTrackingGate()
    private var lastTrackingState: TrackingState? = null
    private lateinit var trackingHint: TextView
    private lateinit var preferences: GestureSettings
    private var lastCursorAge = 0L
    private var lastDrawLog = 0L
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var globalStart: Button
    private lateinit var globalPause: Button
    private lateinit var permissionSummary:TextView
    private lateinit var calibrationButton:Button
    private var calibration:CalibrationSession?=null
    private val localGestures=GestureStateMachine()
    private var practiceEvents=0
    private var practiceMessage="捏合只计数，不操作系统"
    private val isLandscape get()=resources.configuration.orientation==2
    private var globalPermissionRequest = false
    private var pendingGlobalStart = false
    private var config = GestureConfig()
    private val recognizer = GestureRecognizer(config)
    private val debugLog = DebugLog()
    private val handler = Handler(Looper.getMainLooper())
    private var requested = false
    private var lastFrame = 0L
    private var lastStats = 0L
    private var lastLog = 0L
    private val staleGuard = object : Runnable {
        override fun run() {
            if (lastFrame > 0 && SystemClock.uptimeMillis() - lastFrame > 250) {
                cursor.reset()
                cursorView.clear()
                trackingGate.reset(TrackingState.STALE)
                localGestures.resetTracking()
                calibration?.update(null,SystemClock.uptimeMillis())
                renderTrackingState()
            }
            if (lastFrame > 0 && SystemClock.uptimeMillis() - lastFrame > 600) {
                overlay.clear()
                cursor.reset()
                cursorView.clear()
                status.text = "暂未收到新的识别结果，旧关键点已清除。\n可停止后重新开始。"
                lastFrame = 0
            }
            if (requested) handler.postDelayed(this, 100)
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (globalPermissionRequest) {
            globalPermissionRequest = false
            if (granted) prepareGlobalMode()
            else status.text = "需要相机权限才能识别手部；请在权限设置中开启后重试。"
            return@registerForActivityResult
        }
        requested = granted
        if (granted) openCamera() else {
            start.text = "授权相机并开始"
            status.text = "未授权相机。可重新申请；若系统不再弹窗，请点权限设置。"
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) android.widget.Toast.makeText(this, "通知未开启，可回到应用暂停或停止", android.widget.Toast.LENGTH_LONG).show()
        pendingGlobalStart = true
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startGlobalMode()
    }
    private val exportLog = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                checkNotNull(contentResolver.openOutputStream(uri)).bufferedWriter().use {
                    it.write(debugLog.export() + "\nGLOBAL SESSION\n" + GlobalSession.log.export())
                }
                android.widget.Toast.makeText(this, "已导出数值日志，不含画面", android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "导出失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = GestureSettings(this)
        config=preferences.loadPerformance().config()
        cursor.configure(preferences.load())
        cursor.setLandscape(isLandscape)
        localGestures.configure(preferences.loadActions())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Restore a preview only during same-process Activity recreation, never after process death/update.
        requested = !GlobalSession.running && savedInstanceState?.getString("processToken") == processToken &&
            (savedInstanceState?.getBoolean("requested") ?: false)
        globalPermissionRequest = savedInstanceState?.getBoolean("globalPermissionRequest") ?: false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(com.airgesture.app.ui.Brand.background)
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(12) + bars.left, bars.top + dp(6), dp(12) + bars.right, bars.bottom + dp(6))
            insets
        }
        root.addView(TextView(this).apply { text = "鼠标校准与练习"; textSize = if(isLandscape)18f else 21f })
        root.addView(TextView(this).apply {
            text = "调好范围，瞄准会更轻松。相机画面仅在本机处理。"
            if(isLandscape)visibility=View.GONE
            textSize = 12f
            setPadding(0, dp(5), 0, dp(5))
        })
        permissionSummary=TextView(this).apply {textSize=11f;setTextColor(Color.rgb(40,90,130));setOnClickListener{showPermissionGuide()}}
        root.addView(permissionSummary)
        trackingHint = TextView(this).apply {
            text = "开始后，稳住整只手片刻再移动"
            textSize = 13f
            setTextColor(Color.rgb(140, 85, 0))
        }
        root.addView(trackingHint)
        start = Button(this).apply {
            text = if (hasCameraPermission()) "本机练习（不操作系统）" else "授权相机并练习"
            textSize=13f
            setOnClickListener {
                if (requested) {
                    requested = false
                    releaseCamera()
                    text = "开始光标测试"
                    status.text = "相机已停止并释放。"
                } else if (hasCameraPermission()) {
                    requested = true
                    openCamera()
                } else cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        if(!isLandscape)root.addView(start)
        calibrationButton=Button(this).apply {
            visibility=View.GONE;text="记录左上";textSize=13f
            setOnClickListener{calibration?.capture(SystemClock.uptimeMillis());isEnabled=false}
        }
        root.addView(calibrationButton,LinearLayout.LayoutParams(-1,dp(42)))
        root.addView(LinearLayout(this).apply {
            if(isLandscape)addView(start,LinearLayout.LayoutParams(0,dp(46),2f))
            globalStart = Button(this@MainActivity).apply {
                text = "开启全局控制"
                textSize = 13f
                setOnClickListener {
                    if (GlobalSession.running) {
                        stopService(Intent(this@MainActivity, GestureForegroundService::class.java))
                    } else prepareGlobalMode()
                }
            }
            addView(globalStart, LinearLayout.LayoutParams(0, dp(46), 2f))
            globalPause = Button(this@MainActivity).apply {
                text = "暂停"
                textSize = 13f
                isEnabled = false
                setOnClickListener { sendGlobalAction(GestureForegroundService.ACTION_PAUSE) }
            }
            addView(globalPause, LinearLayout.LayoutParams(0, dp(46), 1f))
        })
        root.addView(LinearLayout(this).apply {
            addView(Button(this@MainActivity).apply {
                text = "导出数值日志"
                textSize = 12f
                setOnClickListener { exportLog.launch("airgesture-debug-${System.currentTimeMillis()}.txt") }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(Button(this@MainActivity).apply {
                text = "设置"
                textSize = 12f
                setOnClickListener { showSettingsMenu() }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(Button(this@MainActivity).apply {
                text = "权限设置"
                textSize = 12f
                setOnClickListener {
                    showPermissionGuide()
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
        })
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        overlay = DebugOverlay(this)
        cursorView = CursorTestView(this).apply {
            onFrameDrawn = { timestamp, drawnAt ->
                lastCursorAge = drawnAt - timestamp
                if (drawnAt - lastDrawLog >= 1000) {
                    lastDrawLog = drawnAt
                    debugLog.add("cursor_draw=1 sample_age_ms=$lastCursorAge")
                }
            }
            onTargetChanged = { target -> debugLog.add("hover_target=$target action=NONE") }
        }
        val previewContainer = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(-1, -1))
            addView(this@MainActivity.overlay, FrameLayout.LayoutParams(-1, -1))
            addView(cursorView, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(previewContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            text = "相机尚未开启。\n请在系统弹窗主动确认相机权限，再将一只手放入画面。"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            minLines = 8
            setPadding(0, dp(5), 0, 0)
        }
        // Compact, scrollable diagnostics keep the preview usable in landscape.
        root.addView(android.widget.ScrollView(this).apply { addView(status) },
            LinearLayout.LayoutParams(-1, dp(if (resources.configuration.orientation == 2) 56 else 120)))
        fun styleControls(view:View){
            if(view is Button){view.isAllCaps=false;view.setTextColor(com.airgesture.app.ui.Brand.ink);view.background=com.airgesture.app.ui.Brand.surface(this,radius=12,border=true)}
            if(view is ViewGroup)for(i in 0 until view.childCount)styleControls(view.getChildAt(i))
        }
        styleControls(root)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        camera = CameraController(this, this, preview, config, ::onCameraError, ::renderFrame)
        GlobalSession.status.observe(this) { global ->
            if(com.airgesture.app.ui.ownNumberSelectionActive())return@observe
            start.isEnabled = !global.running
            globalStart.isEnabled = true
            globalStart.text = if (global.running) "停止全局控制" else if (Settings.canDrawOverlays(this)) "开启全局控制" else "授权悬浮窗并开启"
            globalPause.isEnabled = global.running
            globalPause.text = if (global.paused) "继续" else "暂停"
            if (global.running) {
                requested = false
                start.text = "应用内光标测试"
                trackingHint.text = global.message
                status.text = (if (global.paused) "已暂停，可从通知继续。" else if (GlobalSession.actionsEnabled) "食指瞄准、拇指靠近两次点击；四指上滑、两指下滑。" else "当前仅移动光标。权限设置中开启无障碍后，重新开始即可操作系统。") +
                    if(preferences.loadActions().debug) "\n"+global.diagnostics else "\n保持整只手入镜；通知可暂停、停止。"
                if(com.airgesture.app.session.ControlSessionState.current.running)status.text=com.airgesture.app.session.ControlSessionState.current.message
            } else if (!requested) {
                trackingHint.text = global.message
                status.text = "先做本机练习；统一测试时开启无障碍，再点“开启全局控制”。"
            }
        }
        AccessibilityBridge.connected.observe(this){updatePermissionSummary()}
        preview.previewStreamState.observe(this) { state ->
            if (requested && state == PreviewView.StreamState.STREAMING) {
                debugLog.add("uptime_ms=${SystemClock.uptimeMillis()} camera_preview=STREAMING")
            }
        }
        if(intent.getBooleanExtra("showPermissions",false))handler.post{showPermissionGuide()}
        debugLog.add("version=V04 model=0.10.35 requested_gpu=${config.useGpu} detection_min=0.6 presence_min=0.6 tracking_min=0.6")
    }

    private fun prepareGlobalMode() {
        if (!hasCameraPermission()) {
            globalPermissionRequest = true
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this).setTitle("允许显示全局光标")
                .setMessage("需要你在系统设置中允许“小空”显示悬浮窗，才能把光标显示在桌面和其他 App 上。\n光标不拦截手指触摸；点击和滑动还需另行开启无障碍。授权后返回，再点开启。")
                .setPositiveButton("去系统授权") { _, _ ->
                    requested = false
                    releaseCamera()
                    start.text = "开始光标测试"
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }.setNegativeButton("暂不", null).show()
            return
        }
        val noticePrefs = getSharedPreferences("permission_guidance", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !noticePrefs.getBoolean("notificationAsked", false)) {
            AlertDialog.Builder(this).setTitle("在通知栏暂停或停止")
                .setMessage("开启通知后，离开应用也能通过通知暂停光标或停止相机。接下来请在系统弹窗选择；不允许也可回到应用停止。")
                .setPositiveButton("继续") { _, _ ->
                    noticePrefs.edit().putBoolean("notificationAsked", true).apply()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }.setNegativeButton("暂不开始", null).show()
            return
        }
        startGlobalMode()
    }

    private fun startGlobalMode() {
        pendingGlobalStart = false
        if (GlobalSession.running || !hasCameraPermission() || !Settings.canDrawOverlays(this)) return
        if(!com.airgesture.app.ui.SessionStarter.canStart(this)){status.text="请解锁并保持应用在前台后再开始";return}
        requested = false
        releaseCamera()
        globalStart.isEnabled = false
        status.text = "正在启动全局光标…"
        try {
            ContextCompat.startForegroundService(this, Intent(this, GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
                .putExtra(GestureForegroundService.EXTRA_CONTROL, AccessibilityBridge.available))
        } catch (e: Exception) {
            globalStart.isEnabled = true
            status.text = "暂时无法启动：${e.javaClass.simpleName}，请保持应用在前台后重试。"
        }
    }

    private fun showPermissionGuide() {
        val labels = arrayOf("相机：${if(hasCameraPermission()) "已开启" else "未开启"}",
            "悬浮窗：${if(Settings.canDrawOverlays(this)) "已开启" else "未开启"}",
            "无障碍：${if(AccessibilityBridge.available) "已连接" else "待开启"}", "通知设置")
        AlertDialog.Builder(this).setTitle("权限状态").setItems(labels) { _,which ->
            requested=false; releaseCamera()
            stopService(Intent(this,GestureForegroundService::class.java))
            when(which) {
                0 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
                1 -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
                2 -> AlertDialog.Builder(this).setTitle("开启手势操作")
                    .setMessage("用于执行手势点击、滑动、返回等操作。仅在两次拇指向下要求不喜欢时，本机查找菜单按钮文字，不保存页面内容。\n请在系统无障碍设置中找到“小空免触控控制”并开启，随后回应用开始。")
                    .setPositiveButton("去系统设置") { _,_-> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .setNegativeButton("稍后",null).show()
                3 -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            }
        }.setNegativeButton("关闭",null).show()
    }

    private fun sendGlobalAction(action: String) {
        if (GlobalSession.running) startService(Intent(this, GestureForegroundService::class.java).setAction(action))
    }

    override fun onResume() {
        super.onResume()
        updatePermissionSummary()
        if (!GlobalSession.running) globalStart.text = if (Settings.canDrawOverlays(this)) "开启全局控制" else "授权悬浮窗并开启"
        if (pendingGlobalStart) startGlobalMode()
    }
    private fun updatePermissionSummary() {
        permissionSummary.text="相机 ${if(hasCameraPermission()) "✓" else "○"}  悬浮窗 ${if(Settings.canDrawOverlays(this)) "✓" else "○"}  无障碍 ${if(AccessibilityBridge.available) "✓" else "○"}  · 点此设置"
    }

    private fun showCursorSettings() {
        val current = cursor.settings
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), 0)
        }
        fun choices(title: String, options: List<String>, selected: Int): RadioGroup {
            panel.addView(TextView(this).apply { text = title; textSize = 15f })
            val group = RadioGroup(this)
            options.forEachIndexed { i, label ->
                group.addView(RadioButton(this).apply { id = View.generateViewId(); text = label; isChecked = i == selected })
            }
            panel.addView(group)
            return group
        }
        val speed = choices("移动速度", Sensitivity.entries.map { it.label }, current.sensitivity.ordinal)
        val smooth = choices("平滑强度", Smoothness.entries.map { it.label }, current.smoothness.ordinal)
        val method = choices("平滑方式（可在同一位置比较）", listOf("自适应 · One Euro", "固定平滑 · EMA", "关闭平滑"), current.method.ordinal)
        val acceleration=Switch(this).apply {text="动态速度：慢移精细，快移加速";isChecked=current.acceleration;panel.addView(this)}
        AlertDialog.Builder(this).setTitle("光标设置")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("应用") { _, _ ->
                fun index(group: RadioGroup) = (0 until group.childCount).first { group.getChildAt(it).id == group.checkedRadioButtonId }
                val updated = current.copy(sensitivity=Sensitivity.entries[index(speed)], smoothness=Smoothness.entries[index(smooth)], method=SmoothingMethod.entries[index(method)],acceleration=acceleration.isChecked)
                preferences.save(updated)
                cursor.configure(updated)
                trackingGate.reset()
                renderTrackingState()
                cursorView.clear()
                lastCursorAge = 0
                debugLog.add("cursor_settings filter=${updated.method.name} smooth=${updated.smoothness.name} sensitivity=${updated.sensitivity.name}")
                sendGlobalAction(GestureForegroundService.ACTION_RELOAD)
            }.setNegativeButton("取消", null).show()
    }

    private fun showSettingsMenu() {
        if (GlobalSession.running) stopService(Intent(this,GestureForegroundService::class.java))
        handler.postDelayed({
            if (!isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                AlertDialog.Builder(this).setTitle("设置").setItems(arrayOf("光标速度与平滑","手势开关","活动范围校准","性能与省电","权限状态","使用说明")) { _,which ->
                    when(which) {0->showCursorSettings();1->showGestureSettings();2->showCalibrationMenu();3->showPerformanceSettings();4->showPermissionGuide();5->showHelp()}
                }.setNegativeButton("关闭",null).show()
            }
        },350)
    }

    private fun showGestureSettings() {
        val current=preferences.loadActions()
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        fun option(label:String,checked:Boolean)=Switch(this).apply {
            text=label;isChecked=checked;setPadding(0,dp(10),0,dp(10));panel.addView(this)
        }
        val pinch=option("捏合：点击一次",current.pinch)
        val drag=option("捏住拖动：短捏松开点击，长捏拖动",current.drag)
        val up=option("上挥：向上滑动",current.swipeUp)
        val down=option("下挥：向下滑动",current.swipeDown)
        val fist=option("握拳 0.5 秒：暂停 / 恢复",current.fistPause)
        val back=option("左挥：返回（可选）",current.swipeLeftBack)
        val home=option("右挥：桌面（可选）",current.swipeRightHome)
        val push=option("前推点击（实验，默认关闭）",current.pushExperimental)
        push.setOnCheckedChangeListener {_,checked -> if(checked && !current.pushExperimental)
            AlertDialog.Builder(this).setTitle("实验性前推点击").setMessage("根据手掌和边框扩张判断前推，不能直接测量深度。建议先完成捏合真机验收，前推需单独检查误触。")
                .setPositiveButton("保留开启",null).setNegativeButton("关闭"){_,_->push.isChecked=false}.show()
        }
        val debug=option("显示调试数值",current.debug)
        AlertDialog.Builder(this).setTitle("手势开关").setView(ScrollView(this).apply {addView(panel)})
            .setPositiveButton("保存") {_,_->
                preferences.saveActions(current.copy(pinch=pinch.isChecked,swipeUp=up.isChecked,swipeDown=down.isChecked,
                    fistPause=fist.isChecked,swipeLeftBack=back.isChecked,swipeRightHome=home.isChecked,pushExperimental=push.isChecked,debug=debug.isChecked,drag=drag.isChecked))
                localGestures.configure(preferences.loadActions())
                sendGlobalAction(GestureForegroundService.ACTION_RELOAD)
            }.setNegativeButton("取消",null).show()
    }

    private fun showCalibrationMenu() {
        AlertDialog.Builder(this).setTitle("活动范围校准 · ${if(isLandscape) "横屏" else "竖屏"}")
            .setMessage("保持正常使用距离，依次记录食指舒适范围的左上和右下。整只手必须入镜。横屏、竖屏分别保存；校准期间只做本机练习。")
            .setPositiveButton("开始校准") {_,_->
                if(!hasCameraPermission()){status.text="请先授权相机并开始本机练习，再进入校准。";return@setPositiveButton}
                requested=false;releaseCamera();stopService(Intent(this,GestureForegroundService::class.java))
                handler.postDelayed({
                    if(!isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){
                        calibration=CalibrationSession();calibrationButton.visibility=View.VISIBLE;calibrationButton.isEnabled=true;calibrationButton.text="记录左上"
                        requested=true;openCamera()
                    }
                },350)
            }.setNeutralButton("恢复默认范围") {_,_->
                preferences.saveCalibration(null,isLandscape);cursor.configure(preferences.load());cursor.setLandscape(isLandscape)
                sendGlobalAction(GestureForegroundService.ACTION_RELOAD)
            }.setNegativeButton("取消",null).show()
    }

    private fun showPerformanceSettings() {
        val current=preferences.loadPerformance()
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        val modes=RadioGroup(this)
        PerformanceMode.entries.forEachIndexed{i,mode->modes.addView(RadioButton(this).apply{id=i+1;text=mode.label;isChecked=mode==current.mode})}
        panel.addView(modes)
        val gpu=Switch(this).apply{text="GPU 推理（可对比，失败退回 CPU）";isChecked=current.gpu;panel.addView(this)}
        panel.addView(TextView(this).apply{text="OPPO 空白图基准中 CPU 更快，默认保持 CPU。实际有手帧率需要另测。保存后停止会话，再手动开始。";textSize=12f})
        AlertDialog.Builder(this).setTitle("性能与省电").setView(panel).setPositiveButton("保存") {_,_->
            requested=false;releaseCamera();stopService(Intent(this,GestureForegroundService::class.java))
            preferences.savePerformance(PerformanceSettings(PerformanceMode.entries[modes.checkedRadioButtonId-1],gpu.isChecked))
            camera.close();config=preferences.loadPerformance().config()
            camera=CameraController(this,this,preview,config,::onCameraError,::renderFrame)
            start.text="本机练习（不操作系统）"
        }.setNegativeButton("取消",null).show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this).setTitle("使用方法").setMessage(
            "1. 手机固定，整只手入镜，先张手稳住片刻。\n2. 食指移动光标；拇指和食指捏合一次点击，松开后才能再点。两指开始靠拢时提前锁住原来的瞄准位置；短捏松开点击，持续捏住约 0.7 秒进入拖动，移手后松开放下。\n3. 手掌张开稳住，再快速向上或下挥一次；动作后稍停再挥。\n4. 张手后握拳约 0.5 秒暂停；再次张手、握拳恢复。一直握拳不会反复切换。\n5. 先用本机练习计数，再开启全局控制。无障碍未连接时只显示光标。\n6. 返回/桌面及前推在设置中单独开启，默认关闭。通知可暂停或停止，锁屏会结束相机会话。\n\n摄像头画面不保存、不上传。调试日志只有数值；部分系统页面会主动隐藏第三方光标。")
            .setPositiveButton("知道了",null).show()
    }

    private fun renderTrackingState() {
        val state = trackingGate.state
        if (lastTrackingState == state) return
        lastTrackingState = state
        trackingHint.text = state.message
        trackingHint.setTextColor(if (state == TrackingState.TRACKING) Color.rgb(0, 110, 65) else Color.rgb(140, 85, 0))
        debugLog.add("tracking_state=${state.name} cursor_allowed=${if (state == TrackingState.TRACKING) 1 else 0}")
    }

    private fun renderFrame(frame: TrackingFrame, metrics: PerformanceSnapshot) {
        if (!requested) return
        val now = SystemClock.uptimeMillis()
        lastFrame = now
        val hand = frame.hand
        val finger = hand?.normalized?.getOrNull(8)
        val points = hand?.normalized?.map { CursorPoint(it.x, it.y) }
        val acceptedTip = trackingGate.update(points, frame.timestampMs, now, frame.width, frame.height)
        renderTrackingState()
        overlay.show(frame, preview.outputTransform, cursor.region)
        if (trackingGate.state != TrackingState.TRACKING) overlay.hideHand()
        val posture = if (acceptedTip != null) recognizer.describe(frame)
            else com.airgesture.app.vision.Posture("IGNORED_${trackingGate.state.name}")
        val mappedPoint = cursor.update(acceptedTip, frame.timestampMs, now)
        val observation=if(acceptedTip!=null && mappedPoint!=null && points!=null)
            HandObservation.from(points,frame.width,frame.height,frame.timestampMs,mappedPoint) else null
        val decision=if(calibration==null)localGestures.update(observation,now) else {
            localGestures.resetTracking();com.airgesture.app.gesture.GestureDecision(message="活动范围校准")
        }
        if(decision.action!=null && decision.action !in setOf(ActionKind.DRAG_MOVE,ActionKind.DRAG_END)){
            practiceEvents++
            val target=if(decision.action==ActionKind.CLICK && decision.point!=null)cursorView.recordClick(decision.point) else 0
            practiceMessage="本机第 $practiceEvents 次：${decision.action}" + if(target>0) "，命中 $target 号" else ""
            debugLog.add("practice_gesture=${decision.action} target=$target count=$practiceEvents system_action=NONE")
        }
        if(decision.action==ActionKind.DRAG_END)practiceMessage="拖动练习已放下"
        if(decision.pauseChanged)android.widget.Toast.makeText(this,if(localGestures.paused)"本机练习已暂停" else "本机练习已恢复",android.widget.Toast.LENGTH_SHORT).show()
        val cursorPoint = if(localGestures.paused)null else decision.frozenCursor ?: mappedPoint
        cursorView.show(cursorPoint, frame.timestampMs)
        calibration?.let {session->
            session.update(acceptedTip?.let{CursorPoint(1-it.x,it.y)},now)
            calibrationButton.isEnabled=!session.collecting
            calibrationButton.text=if(session.collecting)"正在记录…" else if(session.stage==CalibrationStage.TOP_LEFT)"记录左上" else "记录右下"
            trackingHint.text=session.message
            session.result?.let {region->
                preferences.saveCalibration(region,isLandscape);cursor.configure(preferences.load());cursor.setLandscape(isLandscape)
                calibration=null;calibrationButton.visibility=View.GONE
                debugLog.add("calibration_saved landscape=$isLandscape min_x=${region.minX} max_x=${region.maxX} min_y=${region.minY} max_y=${region.maxY}")
                android.widget.Toast.makeText(this,"活动范围已保存",android.widget.Toast.LENGTH_SHORT).show()
            }
        } ?: run {if(trackingGate.state==TrackingState.TRACKING)trackingHint.text=decision.message+" · 本机练习"}
        val debugEnabled=preferences.loadActions().debug
        overlay.visibility=if(debugEnabled || calibration!=null)View.VISIBLE else View.INVISIBLE
        val world = hand?.world?.getOrNull(8)
        fun Float?.fmt() = this?.let { String.format(Locale.US, "%.3f", it) } ?: "—"
        val line = String.format(Locale.US,
            "t=%d hand=%d landmarks=%d world=%d posture=%s camera_fps=%.1f inference_fps=%.1f inference_ms=%d mean_ms=%.1f pipeline_ms=%d received=%d skipped=%d completed=%d handedness_score=%s index_x=%s index_y=%s pinch_ratio=%s",
            frame.timestampMs, if (hand == null) 0 else 1, hand?.normalized?.size ?: 0,
            hand?.world?.size ?: 0, posture.name, metrics.cameraFps, metrics.inferenceFps,
            frame.inferenceMs, metrics.meanInferenceMs, frame.processingMs, metrics.received,
            metrics.skipped, metrics.completed, hand?.handednessScore.fmt(), finger?.x.fmt(), finger?.y.fmt(), posture.pinchRatio.fmt())
        if (now - lastLog >= 1000) {
            lastLog = now
            debugLog.add(line + " cursor_x=${cursorPoint?.x.fmt()} cursor_y=${cursorPoint?.y.fmt()} filter=${cursor.settings.method.name} smooth=${cursor.settings.smoothness.name} sensitivity=${cursor.settings.sensitivity.name} tracking=${trackingGate.state.name} min_x=${points?.minOfOrNull { it.x }.fmt()} max_x=${points?.maxOfOrNull { it.x }.fmt()} min_y=${points?.minOfOrNull { it.y }.fmt()} max_y=${points?.maxOfOrNull { it.y }.fmt()}")
        }
        if (now - lastStats < 250) return
        lastStats = now
        if(!debugEnabled){status.text="$practiceMessage\n张手稳住，捏合只计数；上/下挥与握拳也只在本机练习。";return}
        status.text = String.format(Locale.US,
            "HAND: %s · %s · points %d / world %d\nGesture: %s（仅提示）\nCamera %.1f FPS | AI %.1f FPS | %d×%d\nInference %d ms | Avg %.1f ms | Pipeline %d ms\n分析收到 %d | 主动跳帧 %d | 完成 %d\n左右手分类: %s（非检测置信度）\nIndex X %s Y %s Z %s | World Z %s m\nPinch %s | 光标帧龄 ${lastCursorAge} ms\n${cursor.settings.method.label} / 平滑${cursor.settings.smoothness.label} / 速度${cursor.settings.sensitivity.label}",
            if (hand == null) "absent" else "detected", hand?.handedness ?: "—", hand?.normalized?.size ?: 0,
            hand?.world?.size ?: 0, posture.name, metrics.cameraFps, metrics.inferenceFps, frame.width, frame.height,
            frame.inferenceMs, metrics.meanInferenceMs, frame.processingMs, metrics.received, metrics.skipped,
            metrics.completed, hand?.handednessScore.fmt(), finger?.x.fmt(), finger?.y.fmt(), finger?.z.fmt(), world?.z.fmt(), posture.pinchRatio.fmt()) +
            "\n$practiceMessage\n状态 ${localGestures.pinchState} / ${if(localGestures.paused) "暂停" else "练习中"}"
    }

    override fun onStart() {
        super.onStart()
        if (requested && !GlobalSession.running && hasCameraPermission()) openCamera()
        else if (!hasCameraPermission()) requested = false
    }

    private fun openCamera() {
        if(GlobalSession.running){requested=false;status.text="请先结束正在运行的全局控制，再开始本机练习。";return}
        start.text = "停止本机练习"
        status.text = "正在启动前摄和本机模型…"
        preview.doOnLayout {
            if (requested && !GlobalSession.running && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lastFrame = 0
                lastStats = 0
                localGestures.setPaused(false)
                localGestures.configure(preferences.loadActions())
                practiceEvents=0;practiceMessage="捏合只计数，不操作系统";cursorView.resetClicks()
                camera.start()
                handler.removeCallbacks(staleGuard)
                handler.postDelayed(staleGuard, 300)
            }
        }
    }

    private fun onCameraError(message:String){requested=false;releaseCamera();status.text=message}

    private fun releaseCamera() {
        camera.stop()
        if(!requested)start.text="本机练习（不操作系统）"
        handler.removeCallbacks(staleGuard)
        overlay.clear()
        cursor.reset()
        localGestures.resetTracking()
        trackingGate.reset()
        if (!GlobalSession.running) renderTrackingState()
        cursorView.clear()
        lastFrame = 0
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        debugLog.add("uptime_ms=${SystemClock.uptimeMillis()} camera_release_requested=1")
    }

    override fun onStop() {
        if(!getSystemService(android.os.PowerManager::class.java).isInteractive ||
            getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)requested=false
        calibration=null;calibrationButton.visibility=View.GONE;releaseCamera();super.onStop()
    }
    override fun onDestroy() { camera.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requested)
        outState.putBoolean("globalPermissionRequest", globalPermissionRequest)
        outState.putString("processToken",processToken)
        super.onSaveInstanceState(outState)
    }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { private val processToken=java.util.UUID.randomUUID().toString() }
}
