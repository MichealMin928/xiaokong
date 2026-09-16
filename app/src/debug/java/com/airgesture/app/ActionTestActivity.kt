package com.airgesture.app

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.graphics.Color

/** Debug APK only, no launcher and DUMP-protected shell entry: an isolated surface for real Accessibility gesture receipts. */
class ActionTestActivity:ComponentActivity(){
    lateinit var target:Button
    lateinit var dragSurface:View
    private var proof:DeviceProof?=null
    private var power:VoicePowerProof?=null
    var clicks=0
    var dislikeSelections=0
    val touchEvents=mutableListOf<Int>()
    val touchRawXs=mutableListOf<Float>()
    val touchRawYs=mutableListOf<Float>()
    private var audioSource:AudioSourceProof?=null
    private var channels:ChannelProof?=null
    private var audioRestart:AudioRestartProof?=null
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        target=Button(this).apply{text="本机系统触摸验收";setOnClickListener{clicks++;if(intent.getStringExtra("proof")=="hybrid")startActivity(android.content.Intent(this@ActionTestActivity,ProofChildActivity::class.java))}}
        root.addView(target,LinearLayout.LayoutParams(-1,300))
        dragSurface=View(this).apply{setBackgroundColor(Color.LTGRAY);setOnTouchListener{_,event->touchEvents+=event.actionMasked;touchRawYs+=event.rawY;touchRawXs+=event.rawX;true}}
        root.addView(dragSurface,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        if(intent.getStringExtra("proof")=="menu"){
            dragSurface.setOnTouchListener{_,_->false}
            dragSurface.setOnLongClickListener{
                android.app.AlertDialog.Builder(this).setTitle("本应用菜单验收").setItems(arrayOf("不感兴趣","举报")){_,index->if(index==0)dislikeSelections++}.show();true
            }
        }
        if(intent.getStringExtra("proof") in setOf("audio_comm","audio_recognition"))audioSource=AudioSourceProof(this,intent.getStringExtra("proof")=="audio_comm").also{it.start()}
        else if(intent.getStringExtra("proof")=="locked_start")LockedStartProof(this).start()
        else if(intent.getStringExtra("proof")=="audio_restart")audioRestart=AudioRestartProof(this).also{it.start()}
        else if(intent.getStringExtra("proof")=="channels")channels=ChannelProof(this).also{it.start()}
        else if(intent.getStringExtra("proof")=="target_apps")TargetAppProof(this).start()
        else if(intent.getStringExtra("proof")?.startsWith("power_")==true)power=VoicePowerProof(this,intent.getStringExtra("proof")!!.removePrefix("power_")).also{it.start()}
        else if(intent.hasExtra("proof"))proof=DeviceProof(this,intent.getStringExtra("proof") ?: "touch").also{it.start()}
    }
    override fun onDestroy(){audioRestart?.close();channels?.close();audioSource?.close();power?.close();super.onDestroy()}
    override fun onStop(){proof?.onStopped();super.onStop()}
}
