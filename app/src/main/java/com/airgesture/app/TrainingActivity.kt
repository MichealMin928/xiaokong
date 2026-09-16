package com.airgesture.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.accessibility.label
import com.airgesture.app.camera.CameraController
import com.airgesture.app.cursor.*
import com.airgesture.app.debug.DebugOverlay
import com.airgesture.app.gesture.*
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.settings.GestureSettings
import com.airgesture.app.settings.VisionMode
import com.airgesture.app.training.*
import com.airgesture.app.ui.*
import com.airgesture.app.vision.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Guided, explicitly started numerical capture. This Activity has no system-action dispatcher. */
@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
class TrainingActivity:ComponentActivity(){
    private val handler=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private lateinit var preview:PreviewView
    private lateinit var skeleton:DebugOverlay
    private lateinit var replayView:TrainingReplayView
    private lateinit var simulator:CursorTestView
    private lateinit var prompt:TextView
    private lateinit var feedback:TextView
    private lateinit var progress:TextView
    private lateinit var primary:Button
    private lateinit var repeatButton:Button
    private lateinit var library:Button
    private lateinit var handButton:Button
    private var camera:CameraController?=null
    private var controller=HybridGestureController()
    private val cursor=CursorController()
    private val gate=HandTrackingGate()
    private var index=0
    private var chosenHand="右手"
    private var session=System.currentTimeMillis().toString()
    private var recording=false
    private var saving=false
    private var completed=false
    private var record:TrainingRecord?=null
    private var file:File?=null
    private var prepareUntil=0L
    private var recordUntil=0L
    private var recordPhase=false
    private var previousFrame=0L
    private var lastFrame=0L
    private var epoch=0
    private var openingAt=0L
    @Volatile private var writeFailed=false
    private var frameCount=0
    private var acceptedCount=0
    private var actions=mutableMapOf<String,Int>()
    private val preparationActions=mutableMapOf<String,Int>()
    private val folder get()=File(filesDir,"training")
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)startSegment() else feedback.text="需要相机权限才能采集；尚未录制。"}

    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        stopService(Intent(this,GestureForegroundService::class.java))
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root=page("动作采集与回放")
        note(root,"只在这里模拟，不操作其他软件。保存关键点与动作记录，不保存相机画面。左右手分别采集，每种动作含 3 个角度。")
        prompt=note(root,"").apply{textSize=17f}
        val stack=FrameLayout(this)
        preview=PreviewView(this).apply{implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FIT_CENTER}
        skeleton=DebugOverlay(this);replayView=TrainingReplayView(this).apply{visibility=View.GONE}
        stack.addView(preview,FrameLayout.LayoutParams(-1,-1));stack.addView(skeleton,FrameLayout.LayoutParams(-1,-1));stack.addView(replayView,FrameLayout.LayoutParams(-1,-1))
        root.addView(stack,LinearLayout.LayoutParams(-1,0,1f))
        simulator=CursorTestView(this).apply{setBackgroundColor(Color.rgb(36,49,58))}
        root.addView(simulator,LinearLayout.LayoutParams(-1,0,1f))
        progress=note(root,"尚未开始，先选择惯用手。")
        feedback=note(root,"小圆点显示黄色 1：已靠近一次；稍张开再靠近，显示绿色 2 并点击。采集后可回放。")
        primary=button(root,"开始本段（3 秒准备＋6 秒采集）"){
            if(recording){finishSegment(true);return@button}
            if(completed){file?.let{TrainingRecord.annotate(it,true)};index++;completed=false
                if(index>=TrainingPlan.count){index=0;session=System.currentTimeMillis().toString();feedback.text="本组已确认完成，可切换另一只手。"}
                renderPrompt()
            }else startSegment()
        }
        val row=LinearLayout(this)
        root.addView(row)
        repeatButton=Button(this).apply{text="重录本段";setOnClickListener{if(!recording && !saving){file?.let{TrainingRecord.annotate(it,false)};completed=false;startSegment()}}}
        handButton=Button(this).apply{text="当前：右手";setOnClickListener{
            if(!recording && !saving){chosenHand=if(chosenHand=="右手")"左手" else "右手";text="当前：$chosenHand";index=0;completed=false;session=System.currentTimeMillis().toString();file=null;renderPrompt()}
        }}
        row.addView(repeatButton,LinearLayout.LayoutParams(0,-2,1f));row.addView(handButton,LinearLayout.LayoutParams(0,-2,1f))
        library=button(root,"选动作、角度 / 已保存记录与回放"){
            android.app.AlertDialog.Builder(this).setTitle("采集与回放").setItems(arrayOf("选择要采集的动作和角度","查看已保存记录与回放")){_,which->
                if(which==1)showLibrary() else android.app.AlertDialog.Builder(this).setTitle("选择动作与角度").setItems((0 until TrainingPlan.count).map{"${TrainingPlan.step(it).label} · ${TrainingPlan.angle(it)}"}.toTypedArray()){_,selected->index=selected;completed=false;file=null;renderPrompt()}.show()
            }.show()
        }
        renderPrompt()
    }
    private fun actionLabels(values:Map<String,Int>)=values.entries.joinToString{(ActionKind.entries.firstOrNull{a->a.name==it.key}?.label ?: "暂停切换")+" ×"+it.value}
    private fun renderPrompt(){
        val step=TrainingPlan.step(index)
        prompt.text="${index+1}/${TrainingPlan.count} · $chosenHand · ${TrainingPlan.angle(index)}\n${step.instruction}"
        primary.text=if(completed)"确认按提示做完，下一项" else "开始本段（3 秒准备＋6 秒采集）"
    }
    private fun configure(){
        val prefs=GestureSettings(this)
        cursor.configure(prefs.load());cursor.setLandscape(resources.configuration.orientation==2);cursor.reset();gate.reset()
        controller=HybridGestureController().apply{configure(prefs.loadActions())}
    }
    private fun controls(enabled:Boolean){primary.isEnabled=enabled || recording;repeatButton.isEnabled=enabled;handButton.isEnabled=enabled;library.isEnabled=enabled}
    private fun startSegment(){
        if(recording || saving)return
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){permission.launch(Manifest.permission.CAMERA);return}
        renderPrompt()
        epoch++;handler.removeCallbacksAndMessages(null)
        stopService(Intent(this,GestureForegroundService::class.java))
        camera?.close();camera=null
        configure();simulator.resetClicks();simulator.clear();replayView.visibility=View.GONE
        skeleton.visibility=View.VISIBLE;preview.visibility=View.VISIBLE
        recording=true;completed=false;primary.text="停止并保留本段";openingAt=SystemClock.uptimeMillis();writeFailed=false;controls(false);frameCount=0;acceptedCount=0;actions.clear();preparationActions.clear();previousFrame=0;lastFrame=0;prepareUntil=0;recordPhase=false
        val step=TrainingPlan.step(index)
        file=File(folder,"$session/${index.toString().padStart(2,'0')}-${step.id}-${System.currentTimeMillis()}.jsonl")
        val header=JSONObject().put("session",session).put("step_index",index).put("step",step.id).put("step_label",step.label).put("instruction",step.instruction)
            .put("requested_hand",chosenHand).put("requested_angle",TrainingPlan.angle(index)).put("angle_is_instruction_not_measurement",true)
            .put("expected_action",step.expected?.name ?: "NONE").put("expected_count",step.expectedCount).put("target",step.target ?: JSONObject.NULL)
            .put("created_at",System.currentTimeMillis()).put("app_version",packageManager.getPackageInfo(packageName,0).versionName).put("system_actions",false).put("raw_images_saved",false)
            .put("cursor_settings",GestureSettings(this).load().toString()).put("replay_mapping","recorded cursor and acceptance; recompute gesture from saved landmarks")
        try{record=TrainingRecord(checkNotNull(file),header)}catch(e:Exception){recording=false;controls(true);feedback.text="创建记录失败：${e.javaClass.simpleName}";return}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progress.text="正在准备相机，模型就绪后开始倒计时…";feedback.text="听从上方提示，只做这一种动作。"
        val token=epoch
        handler.postDelayed({if(recording && token==epoch)preview.doOnLayout{
            if(!recording || token!=epoch)return@doOnLayout
            camera=CameraController(this,this,preview,GestureSettings(this).loadPerformance().config().copy(visionMode=VisionMode.HAND,handFallback=false),
                {message->feedback.text=message;if(message.contains("失败") || message.contains("错误")){finishSegment(true)}},
                {frame,metrics->onFrame(frame,metrics.cameraFps,metrics.inferenceFps,metrics.skipped)})
            camera?.start()
        }},400)
        tick(token)
    }
    private fun write(row:JSONObject){
        val target=record ?: return
        io.execute{runCatching{target.append(row)}.onFailure{writeFailed=true;handler.post{feedback.text="记录写入失败，当前段需重录";finishSegment(true)}}}
    }
    private fun onFrame(frame:TrackingFrame,cameraFps:Double,aiFps:Double,skipped:Long){
        if(!recording)return
        val now=SystemClock.uptimeMillis();lastFrame=now
        if(prepareUntil==0L){prepareUntil=now+3000;recordUntil=prepareUntil+6000}
        if(!recordPhase && now>=prepareUntil){recordPhase=true;simulator.resetClicks()}
        val points=frame.hand?.normalized?.map{CursorPoint(it.x,it.y)}
        val tip=gate.update(points,frame.timestampMs,now,frame.width,frame.height)
        val mapped=cursor.update(tip,frame.timestampMs,now)
        val observation=if(tip!=null && mapped!=null && points!=null)HandObservation.from(points,frame.width,frame.height,frame.timestampMs,mapped) else null
        val decision=controller.update(observation,now)
        runCatching{skeleton.show(frame,preview.outputTransform,cursor.region)}
        simulator.show((decision.gesture.frozenCursor ?: mapped).takeIf{decision.showPointer},frame.timestampMs,decision.gesture.confirmation)
        val action=decision.gesture.action?.name ?: if(decision.gesture.pauseChanged)"PAUSE_TOGGLE" else null
        var hit=0
        if(recordPhase){
            frameCount++;if(observation!=null)acceptedCount++
            if(action!=null && action!="DRAG_MOVE")actions[action]=(actions[action] ?: 0)+1
            if(decision.gesture.action==com.airgesture.app.accessibility.ActionKind.CLICK)decision.gesture.point?.let{hit=simulator.recordClick(it)}
        }else if(action!=null && action!="DRAG_MOVE"){
            preparationActions[action]=(preparationActions[action] ?: 0)+1
            feedback.text="准备阶段已识别：${actionLabels(preparationActions)}。先放松手指，看到“采集中”后再做提示动作。"
        }
        fun xyz(points:List<Landmark3D>?)=points?.let{list->JSONArray().apply{list.forEach{p->put(JSONArray().put(p.x.takeIf{it.isFinite()} ?: JSONObject.NULL).put(p.y.takeIf{it.isFinite()} ?: JSONObject.NULL).put(p.z.takeIf{it.isFinite()} ?: JSONObject.NULL))}}} ?: JSONObject.NULL
        val row=JSONObject().put("type","frame").put("phase",if(recordPhase)"record" else "prepare").put("t",frame.timestampMs)
            .put("age_ms",now-frame.timestampMs).put("gap_ms",if(previousFrame==0L)0 else frame.timestampMs-previousFrame)
            .put("width",frame.width).put("height",frame.height).put("landmarks",xyz(frame.hand?.normalized)).put("world",xyz(frame.hand?.world))
            .put("handedness",frame.hand?.handedness ?: JSONObject.NULL).put("handedness_score",frame.hand?.handednessScore ?: JSONObject.NULL)
            .put("accepted",observation!=null).put("tracking",gate.state.name).put("cursor",TrainingRecord.point(mapped))
            .put("frozen_cursor",TrainingRecord.point(decision.gesture.frozenCursor)).put("click_point",TrainingRecord.point(decision.gesture.point))
            .put("confirmation",decision.gesture.confirmation).put("show_pointer",decision.showPointer).put("action",action ?: JSONObject.NULL).put("target_hit",hit)
            .put("message",decision.gesture.message).put("camera_fps",cameraFps).put("inference_fps",aiFps).put("analysis_skipped",skipped)
        previousFrame=frame.timestampMs;write(row)
        if(recordPhase)feedback.text="模拟识别：${actionLabels(actions).ifEmpty{"暂无动作"}}\n${decision.gesture.message}"
    }
    private fun tick(token:Int){
        if(!recording || token!=epoch)return
        val now=SystemClock.uptimeMillis()
        if(prepareUntil==0L && now-openingAt>10000){finishSegment(true);feedback.text="相机未返回识别结果，请重录";return}
        if(prepareUntil>0){
            if(now>=recordUntil){finishSegment(false);return}
            progress.text=if(now<prepareUntil)"准备 ${((prepareUntil-now+999)/1000)} 秒：把手放好，暂不做动作" else "采集中 ${(recordUntil-now+999)/1000} 秒 · 现在做动作"
        }
        if(lastFrame>0 && now-lastFrame>300){simulator.clear();skeleton.hideHand();controller.update(null,now)}
        handler.postDelayed({tick(token)},100)
    }
    private fun finishSegment(interrupted:Boolean){
        if(!recording)return
        recording=false;saving=true;epoch++;handler.removeCallbacksAndMessages(null)
        camera?.close();camera=null;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val output=record;record=null
        val summary=JSONObject().put("type","summary").put("frames",frameCount).put("accepted_frames",acceptedCount).put("actions",JSONObject(actions.toMap()))
            .put("preparation_actions",JSONObject(preparationActions.toMap()))
            .put("interrupted",interrupted).put("performed_as_prompt_confirmed",false).put("system_actions",false)
        io.execute{
            val error=runCatching{output?.append(summary);output?.close()}.exceptionOrNull()
            handler.post{
                saving=false;completed=!interrupted && error==null && !writeFailed && frameCount>0;controls(true);renderPrompt()
                progress.text=if(error!=null || writeFailed)"保存失败，请重录" else if(interrupted)"本段已中断，已有记录保留，可重录" else "已保存：$frameCount 帧，跟踪可用 $acceptedCount 帧"
                feedback.text="提示动作：${TrainingPlan.step(index).expected?.label ?: "不应触发"} ×${TrainingPlan.step(index).expectedCount}\n采集中：${actionLabels(actions).ifEmpty{"无"}}\n准备阶段：${actionLabels(preparationActions).ifEmpty{"无"}}（不计入采集结果）。这不是准确率；请确认是否按提示做完。"
            }
        }
    }
    private fun showLibrary(){
        val clips=folder.walkTopDown().filter{it.isFile && it.extension=="jsonl"}.sortedByDescending{it.lastModified()}.toList()
        if(clips.isEmpty()){feedback.text="还没有记录；先录一段。";return}
        val labels=clips.map{f->runCatching{val h=JSONObject(f.bufferedReader().use{it.readLine()});"${h.optString("requested_hand")} · ${h.optString("step_label",h.optString("step"))} · ${h.optString("requested_angle")}"}.getOrDefault(f.name)}
        android.app.AlertDialog.Builder(this).setTitle("已保存 ${clips.size} 段").setItems(labels.toTypedArray()){_,i->
            android.app.AlertDialog.Builder(this).setTitle(labels[i]).setItems(arrayOf("回放并重新识别","补录这一段","标记：这段没有按提示做好")){_,choice->
                when(choice){0->replay(clips[i]);1->{val h=TrainingRecord.read(clips[i]).first();index=h.getInt("step_index");chosenHand=h.getString("requested_hand");handButton.text="当前：$chosenHand";completed=false;renderPrompt();startSegment()};2->TrainingRecord.annotate(clips[i],false)}
            }.show()
        }.show()
    }
    private fun replay(source:File){
        epoch++;handler.removeCallbacksAndMessages(null);camera?.close();camera=null;configure();simulator.resetClicks();controls(false)
        val token=epoch
        io.execute{
            val rows=runCatching{TrainingRecord.read(source)}.getOrNull()
            handler.post{
                if(token!=epoch)return@post
                if(rows==null){controls(true);feedback.text="记录读取失败";return@post}
                val header=rows.firstOrNull() ?: JSONObject()
                val originalInstruction=header.optString("instruction")
                val correction=when{
                    header.optString("step")=="down" && originalInstruction.contains("指尖朝下")->"\n现已更正：食指和中指朝上，整只手向下移动。"
                    header.optString("step") in listOf("left","right") && originalInstruction.startsWith("四指朝上")->"\n旧手形规则：当前已改为五指张开左移、食指和中指朝上右移。此旧录制不作新规则验收。"
                    else->""
                }
                prompt.text="回放：${header.optString("requested_hand")} · ${header.optString("requested_angle")}\n原提示：$originalInstruction$correction"
                skeleton.visibility=View.GONE;preview.visibility=View.GONE;replayView.visibility=View.VISIBLE
                val frames=rows.filter{it.optString("type")=="frame"};var i=0;var phase="";val old=mutableMapOf<String,Int>();val current=mutableMapOf<String,Int>();val prep=mutableMapOf<String,Int>();val oldPrep=mutableMapOf<String,Int>()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                fun next(){
                    if(token!=epoch)return
                    if(i>=frames.size){controls(true);window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);progress.text="回放完成 · 未发送系统动作";return}
                    val row=frames[i++]
                    phase=row.optString("phase")
                    val h=runCatching{TrainingRecord.observation(row)}.getOrNull()
                    val t=row.getLong("t");val decision=controller.update(h,t+row.optLong("age_ms"))
                    replayView.show(row)
                    simulator.show((decision.gesture.frozenCursor ?: h?.cursor).takeIf{decision.showPointer},t,decision.gesture.confirmation)
                    val action=decision.gesture.action?.name ?: if(decision.gesture.pauseChanged)"PAUSE_TOGGLE" else null
                    if(phase=="record"){
                        if(!row.isNull("action"))row.getString("action").takeIf{it!="DRAG_MOVE"}?.let{old[it]=(old[it] ?: 0)+1}
                        if(action!=null && action!="DRAG_MOVE")current[action]=(current[action] ?: 0)+1
                        if(action=="CLICK")decision.gesture.point?.let{simulator.recordClick(it)}
                    }else{
                        if(!row.isNull("action"))row.getString("action").takeIf{it!="DRAG_MOVE"}?.let{oldPrep[it]=(oldPrep[it] ?: 0)+1}
                        if(action!=null && action!="DRAG_MOVE")prep[action]=(prep[action] ?: 0)+1
                    }
                    progress.text="骨架回放 $i/${frames.size} · 原帧龄 ${row.optLong("age_ms")} ms · ${row.optString("tracking")}"
                    feedback.text="采集中 原：${actionLabels(old).ifEmpty{"无"}} / 新：${actionLabels(current).ifEmpty{"无"}}\n准备阶段 原：${actionLabels(oldPrep).ifEmpty{"无"}} / 新：${actionLabels(prep).ifEmpty{"无"}}\n${decision.gesture.message}"
                    val delay=if(i<frames.size)(frames[i].getLong("t")-t).coerceIn(16,500) else 150L
                    handler.postDelayed({next()},delay)
                }
                next()
            }
        }
    }
    override fun onStart(){super.onStart();if(!recording && !saving){controls(true);renderPrompt()}}
    override fun onStop(){finishSegment(true);epoch++;handler.removeCallbacksAndMessages(null);camera?.close();camera=null;window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onStop()}
    override fun onDestroy(){camera?.close();io.shutdown();super.onDestroy()}
}
