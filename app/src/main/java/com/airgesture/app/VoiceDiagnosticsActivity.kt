package com.airgesture.app

import android.Manifest
import android.content.Intent
import android.os.*
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.airgesture.app.feedback.LocalFeedback
import com.airgesture.app.service.*
import com.airgesture.app.ui.*
import com.airgesture.app.voice.*
import org.json.*

/** Guided, local acoustic trials. Observes recognizers without ever dispatching system input. */
class VoiceDiagnosticsActivity:ComponentActivity(){
    private var audio:AudioInputManager?=null
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var status:TextView
    private lateinit var prompt:TextView
    private lateinit var next:Button
    private lateinit var project:Spinner
    private lateinit var environment:Spinner
    private var diagnostics=AudioDiagnostics()
    private var sequence=listOf<String>()
    private var index=0
    private var waiting=false
    private var started=0L
    private var ready=false
    private var heard=mutableListOf<String>()
    private var rows=JSONArray()
    private var reportId:String?=null
    private var testConfig=VoiceCommandConfig()
    private var chosenEnvironment=""
    private var chosenProject=""
    private var beginPending=false
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)start() else status.text="未获得麦克风权限"}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        val root=page("用我的声音测试",true)
        note(root,"开始测试会关闭语音和手势控制。按提示说一次，查看漏听或误听；测试只记录结果，不操作手机，不保存声音。")
        project=Spinner(this).apply{adapter=ArrayAdapter(this@VoiceDiagnosticsActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("唤醒词 · 3 次","按钮与编号 · 4 次","基本指令 · 4 次","数字点击 · 3 次","不说话 · 检查误触发"))};root.addView(project)
        environment=Spinner(this).apply{adapter=ArrayAdapter(this@VoiceDiagnosticsActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("安静 · 近距离","安静 · 远一些","视频外放或音乐","交谈或其他噪声"))};root.addView(environment)
        prompt=section(root,"准备好后开始")
        status=note(root,"先选环境，保持平时使用手机的距离。")
        primaryButton(root,"开始这组测试"){permission.launch(Manifest.permission.RECORD_AUDIO)}
        next=button(root,"开始测试后可跟读"){beginTrial()}.apply{isEnabled=false;alpha=.5f}
        button(root,"标记上一条：我没说话却触发了"){
            if(rows.length()>0){rows.getJSONObject(rows.length()-1).put("user_verdict","FALSE_TRIGGER");save();status.text="已标记误触发。可以继续下一条或停止。"}
        }
        button(root,"停止并保存"){stop("测试已结束，结果保存在本机。")}
        button(root,"查看记录 / 反馈问题"){stop("测试已停止");startActivity(Intent(this,FeedbackActivity::class.java))}
        note(root,"记录用于对比设置，不会自动训练模型。离开页面会停止麦克风；原来的控制开关保持关闭。")
    }
    private fun start(){
        stop("正在准备测试");testConfig=VoiceSettings(this).load()
        sequence=when(project.selectedItemPosition){0->List(3){testConfig.wakeWord};1->listOf("显示按钮","编号","显示按钮","编号");2->listOf("上滑","返回","开启手势","关闭手势");3->listOf("点击一号","点击二号","点击三号");else->listOf("保持安静")}
        chosenProject=project.selectedItem.toString();chosenEnvironment=environment.selectedItem.toString()
        rows=JSONArray();reportId=null;index=0;ready=false;beginPending=true
        stopService(Intent(this,GestureForegroundService::class.java))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val deadline=SystemClock.uptimeMillis()+5000
        fun acquire(){
            if(!beginPending || isFinishing || isDestroyed)return
            if(GlobalSession.running){if(SystemClock.uptimeMillis()<deadline)handler.postDelayed({acquire()},150) else stop("控制未停止，请回到首页关闭后重试");return}
            audio=AudioInputManager(this,testConfig,{event->
                if(waiting){heard.add(event.keyword);status.text="听到：${heard.joinToString("、")}"}
            },{d->
                diagnostics=d;ready=d.state=="ACTIVE" && (project.selectedItemPosition!=3 || d.digitsActive)
                next.isEnabled=ready && !waiting && index<sequence.size;next.alpha=if(next.isEnabled)1f else .5f
                if(!waiting)next.text=if(ready)"说一次，开始计时" else "正在准备麦克风…"
                if(d.state in setOf("ERROR","INTERRUPTED"))stop(d.error ?: "麦克风中断")
                else if(!waiting)status.text=if(ready)"麦克风准备好了。点下方按钮，再说一次。" else "正在准备识别，请稍候…"
            }).also{it.vocabulary=if(project.selectedItemPosition==3)VoiceVocabulary.SELECTION else VoiceVocabulary.COMMANDS;it.start()}
            project.isEnabled=false;environment.isEnabled=false
            prompt.text="第 1 / ${sequence.size} 次：${sequence[0]}"
        }
        handler.postDelayed({acquire()},400)
    }
    private fun beginTrial(){
        if(!ready || waiting || index>=sequence.size)return
        waiting=true;heard.clear();started=SystemClock.uptimeMillis();next.isEnabled=false;next.alpha=.5f;next.text="正在听，请稍候…"
        val quiet=sequence[index]=="保持安静"
        prompt.text=if(quiet)"保持安静 20 秒，观察是否误触发" else "现在说一次：「${sequence[index]}」"
        status.text="正在听…"
        handler.postDelayed({finishTrial(false)},if(quiet)20000 else 6000)
    }
    private fun finishTrial(interrupted:Boolean){
        if(!waiting)return
        waiting=false
        val expected=sequence[index]
        val matched=when(expected){"显示按钮","编号"->heard.any{it in setOf("显示按钮","按钮","编号","显示编号")};"点击一号"->"一" in heard;"点击二号"->"二" in heard;"点击三号"->"三" in heard;"保持安静"->heard.isEmpty();else->expected in heard}
        val verdict=if(interrupted)"INTERRUPTED" else if(matched)"MATCH" else if(heard.isEmpty())"MISS" else "WRONG_OR_FALSE_TRIGGER"
        rows.put(JSONObject().put("expected",expected).put("heard",JSONArray(heard)).put("result",verdict).put("duration_ms",SystemClock.uptimeMillis()-started)
            .put("input_level_db",diagnostics.inputLevelDb).put("input_peak_db",diagnostics.inputPeakDb).put("dropped_audio_ms",diagnostics.droppedAudioMs).put("input_delay_ms",diagnostics.inputDelayMs))
        save();index++
        if(index>=sequence.size){stop("这组已完成。${summary()}。结果已保存；可调整设置后对比。")}
        else {prompt.text="${if(matched)"已听到目标" else "这次漏听或听错"} · 下一条：${sequence[index]}";status.text="第 ${index+1} / ${sequence.size} 次。准备好后点下方按钮。";next.isEnabled=ready;next.alpha=if(ready)1f else .5f;next.text="说一次，开始计时"}
    }
    private fun summary():String {
        var matched=0;for(i in 0 until rows.length())if(rows.getJSONObject(i).optString("result")=="MATCH")matched++
        return "${rows.length()} 次测试，${matched} 次符合预期"
    }
    private fun save(){if(rows.length()==0)return
        val report=JSONObject().put("kind","VOICE_TRIAL").put("category",chosenProject).put("environment",chosenEnvironment).put("created_at",System.currentTimeMillis())
            .put("summary",summary()).put("activation",testConfig.activation.name).put("wake_word",testConfig.wakeWord).put("threshold",testConfig.keywordThreshold).put("wake_threshold",testConfig.wakeThreshold)
            .put("noise_processing",testConfig.noiseProcessing).put("audio_source",diagnostics.source).put("aec",diagnostics.aecEnabled).put("ns",diagnostics.nsEnabled).put("attempts",rows).put("actions_dispatched",0).put("raw_audio_saved",false)
        runCatching{val store=LocalFeedback(this);reportId=reportId?.let{store.save(report,it)} ?: store.save(report)}.onFailure{status.text="记录保存失败，请重试"}
    }
    private fun stop(message:String){
        beginPending=false;handler.removeCallbacksAndMessages(null)
        if(waiting)finishTrial(true)
        audio?.close();audio=null;ready=false
        if(::next.isInitialized){next.isEnabled=false;next.alpha=.5f;next.text="麦克风已关闭"}
        if(::prompt.isInitialized)prompt.text=if(sequence.isNotEmpty() && index>=sequence.size)"本组测试已完成" else "测试已停止"
        if(::project.isInitialized){project.isEnabled=true;environment.isEnabled=true}
        if(::status.isInitialized)status.text=message
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    override fun onStop(){stop("已离开测试，麦克风关闭。结果保存在本机。");super.onStop()}
}
