package com.airgesture.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.airgesture.app.camera.CameraController
import com.airgesture.app.debug.PerformanceSnapshot
import com.airgesture.app.eye.*
import com.airgesture.app.mode.AssistantMode
import com.airgesture.app.service.*
import com.airgesture.app.settings.*
import com.airgesture.app.ui.*
import com.airgesture.app.vision.TrackingFrame

@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
class EyeActivity:ComponentActivity(){
    private var camera:CameraController?=null
    private var fullLayer:FrameLayout?=null
    private var fullField:GazeField?=null
    private var fullStatus:TextView?=null
    private lateinit var state:TextView
    private lateinit var field:GazeField
    private var calibrator:GazeCalibrator?=null
    private val reader=GazeReader()
    private var pages=0
    private val handler=Handler(Looper.getMainLooper())
    private var lastFrame=0L
    private val landscape get()=resources.configuration.orientation==2
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startLocal()else state.text="未授权相机，眼控未开始"}
    private val stale=object:Runnable{override fun run(){
        if(camera==null)return
        if(SystemClock.uptimeMillis()-lastFrame>350){
            reader.reset();field.region=GazeRegion.UNKNOWN;field.invalidate()
            fullField?.let{it.region=GazeRegion.UNKNOWN;it.invalidate()}
            state.text="等待稳定的人脸和睁开的双眼";fullStatus?.text=state.text
        }
        handler.postDelayed(this,200)
    }}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=page("眼控阅读 · 视线区域实验版")
        note(root,"固定手机，正对屏幕，保持约 30–50 厘米。校准时只移动眼睛看标记，保持头部位置。")
        state=note(root,"请先进行三点校准。练习翻页只增加本页计数。")
        val row=LinearLayout(this);root.addView(row)
        fun small(text:String,action:()->Unit){row.addView(Button(this).apply{this.text=text;textSize=12f;setOnClickListener{action()}},LinearLayout.LayoutParams(0,-2,1f))}
        small("三点校准"){calibrator=GazeCalibrator();startLocal()}
        small("区域练习"){calibrator=null;startLocal()}
        small("停止"){stopLocal();stopService(Intent(this,GestureForegroundService::class.java));state.text="已停止，相机已释放"}
        field=GazeField();root.addView(field,LinearLayout.LayoutParams(-1,0,1f))
        button(root,"开始全局眼控阅读"){
            if(GazeStore(this).load(landscape)==null){state.text="当前方向还未校准，请先完成三点校准";return@button}
            stopLocal();stopService(Intent(this,GestureForegroundService::class.java))
            handler.postDelayed({if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))SessionStarter.start(this,AssistantMode.READING)},350)
        }
        button(root,"选择阅读应用 / 设置"){startActivity(Intent(this,AppSettingsActivity::class.java))}
        GlobalSession.status.observe(this){if(!ownNumberSelectionActive() && it.running && camera==null)state.updateTextIfChanged(it.message)}
    }
    private fun startLocal(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){permission.launch(Manifest.permission.CAMERA);return}
        stopService(Intent(this,GestureForegroundService::class.java));stopLocal();showFullScreenPractice()
        handler.postDelayed({
            if(!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))return@postDelayed
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lastFrame=SystemClock.uptimeMillis();reader.reset();pages=0
            camera=CameraController(this,this,null,GestureConfig(maxInferenceFps=15,visionMode=VisionMode.FACE),{state.text=it;stopLocal()},::onFrame,{@Suppress("DEPRECATION") windowManager.defaultDisplay.rotation}).also{it.start()}
            handler.postDelayed(stale,350)
        },350)
    }
    private fun onFrame(frame:TrackingFrame,metrics:PerformanceSnapshot){
        if(camera==null)return
        val now=SystemClock.uptimeMillis();lastFrame=now
        val sample=GazeFeatures.from(frame.face,frame.timestampMs,frame.width,frame.height,now)
        val calibrationSession=calibrator
        if(calibrationSession!=null){
            calibrationSession.update(sample,now);field.target=calibrationSession.stage
            state.text="${calibrationSession.message}\n视线朝标记移动，不要跟着标记转头。"
            calibrationSession.result?.let{GazeStore(this).save(it,landscape);calibrator=null;field.target=null;state.text="校准已保存，试着看上、中、下"}
        }else{
            val calibration=GazeStore(this).load(landscape)
            val region=sample?.let{calibration?.region(it)} ?: GazeRegion.UNKNOWN
            field.region=region;field.target=null
            if(reader.update(region,frame.timestampMs,now)){pages++;GlobalSession.log.add("eye_practice_page=$pages system_action=NONE")}
            state.text="${if(calibration==null) "请先校准" else region.name} · ${reader.message}\n练习翻页 $pages 次 · AI ${"%.1f".format(metrics.inferenceFps)} FPS"
        }
        field.invalidate()
        fullField?.let{it.region=field.region;it.target=field.target;it.invalidate()}
        fullStatus?.text=state.text
    }
    private fun stopLocal(){fullLayer?.let{(it.parent as? android.view.ViewGroup)?.removeView(it)};fullLayer=null;fullField=null;fullStatus=null;if(::field.isInitialized){field.region=GazeRegion.UNKNOWN;field.target=null;field.invalidate()};handler.removeCallbacksAndMessages(null);camera?.close();camera=null;reader.reset();window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    override fun onStop(){if(!GlobalSession.running)state.text="眼控相机已停止，回到页面后点区域练习或校准重新开始";stopLocal();super.onStop()}
    override fun onDestroy(){stopLocal();super.onDestroy()}
    private fun showFullScreenPractice(){
        // Calibration belongs to physical screen regions, not the small space below the controls.
        val layer=FrameLayout(this).apply{setBackgroundColor(Color.rgb(246,249,250))}
        val canvas=GazeField().also{fullField=it}
        layer.addView(canvas,FrameLayout.LayoutParams(-1,-1))
        val caption=TextView(this).apply{text="看标记，只移动眼睛，保持头部不动";textSize=14f;gravity=android.view.Gravity.CENTER}
        fullStatus=caption
        layer.addView(caption,FrameLayout.LayoutParams(-1,-2,android.view.Gravity.TOP).apply{topMargin=(100*resources.displayMetrics.density).toInt()})
        val exit=Button(this).apply{text="结束练习";textSize=12f;setOnClickListener{calibrator=null;stopLocal();state.text="练习已结束，校准成功的数据已保留"}}
        layer.addView(exit,FrameLayout.LayoutParams((100*resources.displayMetrics.density).toInt(),-2,android.view.Gravity.TOP or android.view.Gravity.RIGHT).apply{
            topMargin=(44*resources.displayMetrics.density).toInt();rightMargin=(8*resources.displayMetrics.density).toInt()
        })
        findViewById<FrameLayout>(android.R.id.content).addView(layer,FrameLayout.LayoutParams(-1,-1));fullLayer=layer
    }
    private inner class GazeField:View(this@EyeActivity){
        var region=GazeRegion.UNKNOWN
        var target:GazeRegion?=null
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas:Canvas){
            super.onDraw(canvas)
            listOf(GazeRegion.TOP,GazeRegion.CENTER,GazeRegion.BOTTOM).forEachIndexed{i,item->
                val y=height*(.10f+i*.40f)
                val selected=target==item || (target==null && region==item)
                paint.color=if(selected)Color.rgb(0,135,110)else Color.rgb(90,110,120)
                canvas.drawCircle(width*.5f,y,if(selected)22f else 10f,paint)
                paint.textSize=resources.displayMetrics.scaledDensity*18
                paint.textAlign=Paint.Align.CENTER
                canvas.drawText(item.name,width*.5f,y+52f,paint)
            }
        }
    }
}
