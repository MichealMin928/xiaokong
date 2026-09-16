package com.airgesture.app

import android.content.Intent
import android.os.*
import android.view.MotionEvent
import android.app.Activity
import androidx.core.content.ContextCompat
import com.airgesture.app.accessibility.*
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.mode.*
import com.airgesture.app.service.*
import org.json.JSONObject

/** Debug-only, explicitly launched from ADB. No synthetic action ever targets a third-party surface. */
internal class DeviceProof(private val activity:ActionTestActivity,private val kind:String){
    private val handler=Handler(Looper.getMainLooper())
    private val context get()=activity.applicationContext
    private var done=false
    private val begin=SystemClock.uptimeMillis()
    private val runId=java.util.UUID.randomUUID().toString()
    private var settingsBackup:Map<String,*>?=null
    private var visits=0
    private var firstFrames=0
    private var stage=0
    private var restartWaitUntil=0L
    private val samples=org.json.JSONArray()
    private var touchScreen:ScreenGeometry?=null
    private var pointerOverlay:com.airgesture.app.cursor.GlobalCursorOverlay?=null
    private var proofCamera:com.airgesture.app.camera.CameraController?=null
    fun start(){write(false,"RUNNING");context.stopService(Intent(context,GestureForegroundService::class.java));later(1500){
        if(kind=="kws"){
            Thread{try{
                val results=KeywordModelProof.run(context)
                handler.post{for(i in 0 until results.length())samples.put(results.getJSONObject(i));finish(true,"Synthetic Mandarin fixtures through real on-device KWS; inspect individual matches; no microphone or actions")}
            }catch(e:Exception){handler.post{finish(false,e.message ?: "KWS initialization failed")}}}.start()
            return@later
        }
        when(kind){"voice","voice_auto","mic_conflict","voice_restart"->voiceStart();"camera"->cameraCost();"pointer"->pointerRender();"touch"->touch();"swipe"->swipe();"hybrid"->hybrid();"directional"->directional();"menu"->menu();"global"->globalStart();"gate"->gateStart();"soak"->soakStart();"xhs"->xhsStart();else->finish(false,"Unknown proof")}
    }}
    fun onStopped(){if(kind in listOf("touch","swipe","pointer","camera","voice_restart") && !done)finish(false,"Test surface left foreground")}
    private fun later(ms:Long,block:()->Unit){handler.postDelayed({if(!done)try{block()}catch(e:Exception){finish(false,e.message ?: e.javaClass.simpleName)}},ms)}
    private fun ensure(value:Boolean,message:String){check(value){message}}
    private fun voiceStart(){
        ensure(AccessibilityBridge.available,"Accessibility must be connected")
        ContextCompat.startForegroundService(activity,Intent(context,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
            .putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name).putExtra(GestureForegroundService.EXTRA_CONTROL,true).putExtra(GestureForegroundService.EXTRA_VOICE,true))
        later(600){if(kind=="voice_restart")voiceRestartTick() else voiceTick()}
    }
    private fun voiceCommand(kind:com.airgesture.app.command.CommandKind,number:Int?=null){
        ensure(com.airgesture.app.session.ControlSessionState.request(com.airgesture.app.command.ControlCommand(kind,SystemClock.uptimeMillis(),number=number)),"Voice session missing")
    }
    private fun voiceRestartTick(){
        ensure(SystemClock.uptimeMillis()-begin<45000,"Rapid mouse restart deadline")
        ensure(activity.hasWindowFocus(),"Rapid mouse restart requires own surface")
        val s=com.airgesture.app.session.ControlSessionState.current
        val now=SystemClock.uptimeMillis()
        when(stage){
            0->if(s.audio.state=="ACTIVE"){
                ensure(!s.cameraBound && !s.handModelLoaded,"Voice starts without vision")
                stage=1;voiceCommand(com.airgesture.app.command.CommandKind.START_MOUSE)
            }
            1->if(s.handFrames>=firstFrames+3 && now>=restartWaitUntil){
                ensure(s.audio.state=="ACTIVE" && s.cameraBound && s.handModelLoaded,"Replaced camera must retain active resource state")
                visits++;samples.put(JSONObject().put("cycle",visits).put("hand_frames",s.handFrames).put("camera",s.cameraBound).put("model",s.handModelLoaded).put("mic",s.audio.state))
                firstFrames=s.handFrames.toInt()
                voiceCommand(com.airgesture.app.command.CommandKind.STOP_MOUSE)
                if(visits>=6){stage=2;restartWaitUntil=now+1200}
                else{voiceCommand(com.airgesture.app.command.CommandKind.START_MOUSE);restartWaitUntil=now+1800}
            }
            2->if(now>=restartWaitUntil && !s.cameraBound && !s.handModelLoaded){
                ensure(s.audio.state=="ACTIVE","Final mouse stop must retain voice")
                val frames=s.handFrames
                stage=3
                later(1000){
                    val end=com.airgesture.app.session.ControlSessionState.current
                    ensure(end.handFrames==frames && !end.cameraBound && !end.handModelLoaded,"Frames continued after camera/model release")
                    samples.put(JSONObject().put("stage","final_release").put("hand_frames",end.handFrames).put("camera",end.cameraBound).put("model",end.handModelLoaded))
                    finish(true,"Six real camera/model activations including immediate stop/start replacement; voice stayed active, resource state matched new frames, final release stopped frames; no saved images")
                }
            }
        }
        if(stage!=3)later(150){voiceRestartTick()}
    }
    private fun voiceTick(){
        ensure(SystemClock.uptimeMillis()-begin<45000,"Voice lifecycle proof deadline")
        val s=com.airgesture.app.session.ControlSessionState.current
        when(stage){
            20->Unit
            21->{
                ensure(s.mode==com.airgesture.app.session.ControlMode.PAUSED && s.audio.state=="INTERRUPTED","Mic conflict must stop recognition without automatic reacquire: ${s.mode}/${s.audio.state}")
                ensure(!s.cameraBound && !s.handModelLoaded,"Mic conflict must release vision")
                samples.put(JSONObject().put("stage","different_uid_mic_conflict").put("state",s.audio.state).put("mode",s.mode.name))
                stage=22;voiceCommand(com.airgesture.app.command.CommandKind.RESUME_CONTROL)
            }
            22->if(s.audio.state=="ACTIVE") {finish(true,"Separate foreground app AudioRecord triggered cooperative KWS release; stayed interrupted after probe released; explicit resume reacquired mic; no audio saved");return}
            0->if(s.audio.state=="ACTIVE"){
                if(kind=="mic_conflict"){
                    stage=20
                    activity.startActivity(Intent().setComponent(android.content.ComponentName("com.airgesture.probe","com.airgesture.probe.ProbeActivity")))
                    later(9000){stage=21};later(250){voiceTick()};return
                }
                ensure(!s.cameraBound && !s.handModelLoaded,"Voice-only must release camera/model")
                samples.put(JSONObject().put("stage","voice_only").put("camera",s.cameraBound).put("mic",s.audio.state).put("aec_supported",s.audio.aecSupported).put("aec_enabled",s.audio.aecEnabled).put("ns_supported",s.audio.nsSupported).put("ns_enabled",s.audio.nsEnabled))
                stage=1;voiceCommand(com.airgesture.app.command.CommandKind.START_MOUSE)
            }
            1->if(s.handFrames>=3){
                ensure(s.cameraBound && s.handModelLoaded && s.audio.state=="ACTIVE","Mouse must coexist with local voice")
                samples.put(JSONObject().put("stage","mouse_on").put("hand_frames",s.handFrames));stage=2;if(kind!="voice_auto")voiceCommand(com.airgesture.app.command.CommandKind.STOP_MOUSE)
            }
            2->if(!s.cameraBound && !s.handModelLoaded){
                ensure(s.audio.state=="ACTIVE","Closing mouse must preserve voice")
                if(kind=="voice_auto"){
                    ensure(s.cameraMs>=9000,"Auto sleep must use configured idle timeout")
                    samples.put(JSONObject().put("camera_ms",s.cameraMs).put("hand_model_ms",s.handModelMs).put("mode",s.mode.name))
                    finish(true,"10 second no-hand timeout released real camera and model while microphone stayed active");return
                }
                samples.put(JSONObject().put("stage","mouse_off").put("camera",false).put("hand_model",false));stage=3
                voiceCommand(com.airgesture.app.command.CommandKind.SHOW_NUMBERS)
            }
            3->if(s.mode==com.airgesture.app.session.ControlMode.NUMBER_SELECT){stage=4;voiceCommand(com.airgesture.app.command.CommandKind.SELECT_NUMBER,1)}
            4->if(s.mode==com.airgesture.app.session.ControlMode.VOICE_ONLY){
                ensure(activity.clicks==1,"Node ACTION_CLICK must click own target once")
                samples.put(JSONObject().put("stage","node_click").put("clicks",activity.clicks));stage=5;voiceCommand(com.airgesture.app.command.CommandKind.SHOW_NUMBERS)
                later(150){activity.target.text="内容已变化，旧编号必须失效"}
                later(650){voiceCommand(com.airgesture.app.command.CommandKind.SELECT_NUMBER,1)}
            }
            5->if(s.mode==com.airgesture.app.session.ControlMode.VOICE_ONLY){
                voiceCommand(com.airgesture.app.command.CommandKind.SELECT_NUMBER,1);ensure(activity.clicks==1,"Stale number clicked")
                stage=6;voiceCommand(com.airgesture.app.command.CommandKind.SHOW_GRID)
            }
            6->if(s.mode==com.airgesture.app.session.ControlMode.GRID_SELECT){stage=7;voiceCommand(com.airgesture.app.command.CommandKind.SELECT_NUMBER,s.choices.first().number);later(800){voiceCommand(com.airgesture.app.command.CommandKind.SELECT_NUMBER,1)}}
            7->if(s.mode==com.airgesture.app.session.ControlMode.VOICE_ONLY && activity.clicks==2){
                samples.put(JSONObject().put("stage","grid_click").put("clicks",activity.clicks));stage=8;voiceCommand(com.airgesture.app.command.CommandKind.PAUSE_CONTROL)
            }
            8->if(s.mode==com.airgesture.app.session.ControlMode.PAUSED){
                ensure(!s.cameraBound && !s.handModelLoaded && s.audio.state=="ACTIVE","Pause keeps only resume listening")
                stage=9;voiceCommand(com.airgesture.app.command.CommandKind.RESUME_CONTROL)
            }
            9->if(s.mode==com.airgesture.app.session.ControlMode.VOICE_ONLY){finish(true,"Real microphone KWS, camera off/on/off, native numbered click, stale-node invalidation, two-level grid click and pause/resume; commands synthetic, own surface only");return}
        }
        later(250){voiceTick()}
    }
    private fun cameraCost(){
        ensure(activity.hasWindowFocus(),"Camera comparison needs own foreground surface")
        val formats=listOf(2,1,1,2) // CameraX RGBA / YUV / YUV / RGBA; same 640x480 and 8 FPS cap.
        var round=0
        fun next(){
            if(round==formats.size){finish(true,"ABBA camera conversion comparison at identical resolution and cap; no saved images or gesture dispatch, not a manual accuracy test");return}
            val format=formats[round++]
            val frames=mutableListOf<com.airgesture.app.vision.TrackingFrame>()
            var start=0L;var cpu=0L;var measured=false
            var snapshot:com.airgesture.app.debug.PerformanceSnapshot?=null
            proofCamera=com.airgesture.app.camera.CameraController(context,activity,null,
                com.airgesture.app.settings.GestureConfig(maxInferenceFps=8),
                {error->finish(false,error)},
                {frame,metrics->
                    if(!measured){measured=true;later(600){start=SystemClock.uptimeMillis();cpu=android.os.Process.getElapsedCpuTime()}}
                    if(start>0){frames.add(frame);snapshot=metrics}
                },outputImageFormat=format).also{it.start()}
            fun endRound(){
                ensure(frames.size>=12,"Insufficient camera callbacks: ${frames.size}")
                val elapsed=SystemClock.uptimeMillis()-start
                val cpuMs=android.os.Process.getElapsedCpuTime()-cpu
                ensure(frames.all{it.width==480 && it.height==640},"Analysis geometry changed")
                samples.put(JSONObject().put("format",if(format==1)"YUV" else "RGBA").put("round",round)
                    .put("frames",frames.size).put("elapsed_ms",elapsed).put("process_cpu_ms",cpuMs)
                    .put("prepare_ms",frames.map{it.processingMs-it.inferenceMs}.average())
                    .put("inference_ms",frames.map{it.inferenceMs}.average()).put("hand_frames",frames.count{it.hand!=null})
                    .put("camera_fps",snapshot?.cameraFps).put("result_fps",snapshot?.inferenceFps)
                    .put("thermal",if(Build.VERSION.SDK_INT>=29)context.getSystemService(PowerManager::class.java).currentThermalStatus else 0))
                proofCamera?.close();proofCamera=null
                later(700){next()}
            }
            later(5200){endRound()}
        }
        next()
    }
    private fun pointerRender(){
        ensure(activity.hasWindowFocus(),"Pointer proof needs own surface")
        val overlay=com.airgesture.app.cursor.GlobalCursorOverlay(activity).also{pointerOverlay=it;it.attach()}
        overlay.show(CursorPoint(.25f,.5f),immediate=true)
        later(150){
            val start=checkNotNull(overlay.renderedCenter())
            overlay.show(CursorPoint(.75f,.5f))
            later(30){
                val middle=checkNotNull(overlay.renderedCenter())
                later(120){
                    val end=checkNotNull(overlay.renderedCenter())
                    ensure(end.x>start.x+overlay.bounds().x*.4f,"Overlay did not reach new target")
                    ensure(middle.x in start.x..end.x,"Overlay overshot measured endpoints")
                    samples.put(JSONObject().put("start_x",start.x).put("middle_x",middle.x).put("end_x",end.x))
                    overlay.show(CursorPoint(.5f,.5f),immediate=true,confirmation=1)
                    later(100){
                        val locked=checkNotNull(overlay.renderedCenter())
                        overlay.show(CursorPoint(.5f,.5f),immediate=true,confirmation=2)
                        later(100){
                            ensure(overlay.renderedCenter()==locked,"Confirmation changed the aimed location")
                            overlay.hide();ensure(overlay.renderedCenter()==null,"Hidden overlay retained visible cursor")
                            finish(true,"Real overlay attachment, interpolated movement, immediate locked aim, confirmation 1/2 and hide; no touch injected")
                        }
                    }
                }
            }
        }
    }
    private fun touch(){
        ensure(activity.hasWindowFocus(),"Surface must have focus");ensure(AccessibilityBridge.available,"Existing accessibility service not bound")
        @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay
        val size=android.graphics.Point();@Suppress("DEPRECATION") display.getRealSize(size)
        val screen=ScreenGeometry(size.x,size.y,display.rotation);touchScreen=screen
        val location=IntArray(2);activity.target.getLocationOnScreen(location)
        val target=CursorPoint(location[0]+activity.target.width*.5f,location[1]+activity.target.height*.5f)
        activity.dragSurface.getLocationOnScreen(location)
        val start=CursorPoint(location[0]+activity.dragSurface.width*.3f,location[1]+activity.dragSurface.height*.4f)
        val end=CursorPoint(location[0]+activity.dragSurface.width*.7f,location[1]+activity.dragSurface.height*.6f)
        GlobalSession.publish(GlobalStatus(true,false,"仅当前测试页面"));GlobalSession.actionsEnabled=true
        fun send(action:ActionKind,point:CursorPoint,next:()->Unit){
            ensure(activity.hasWindowFocus(),"Test surface lost focus")
            ensure(AccessibilityBridge.dispatch(SystemAction(action,point,screen,SystemClock.uptimeMillis())){result->
                later(80){ensure(result==ActionResult.COMPLETED,"$action callback $result");next()}
            },"Dispatch rejected: $action")
        }
        send(ActionKind.CLICK,target){ensure(activity.clicks==1,"Single click count ${activity.clicks}")
            send(ActionKind.DOUBLE_CLICK,target){ensure(activity.clicks==3,"Double click count ${activity.clicks}")
                send(ActionKind.DRAG_START,start){send(ActionKind.DRAG_MOVE,end){AccessibilityBridge.releaseDrag();later(200){
                    ensure(activity.touchEvents.count{it==MotionEvent.ACTION_DOWN}==1,"Drag must press once")
                    ensure(activity.touchEvents.count{it==MotionEvent.ACTION_UP}==1,"Drag must release once")
                    ensure(!activity.touchEvents.contains(MotionEvent.ACTION_CANCEL),"Unexpected drag cancel")
                    ensure(activity.touchEvents.contains(MotionEvent.ACTION_MOVE),"Drag must move")
                    send(ActionKind.DRAG_START,start){later(450){
                        ensure(activity.touchEvents.count{it==MotionEvent.ACTION_DOWN}==2,"Idle drag press count")
                        ensure(activity.touchEvents.count{it==MotionEvent.ACTION_UP}==2,"Missing frames must release held contact")
                        ensure(!activity.touchEvents.contains(MotionEvent.ACTION_CANCEL),"Idle release unexpectedly cancelled")
                        finish(true,"Real Accessibility: click, double click, continuous drag and automatic release after missing frames; own surface only")
                    }}
                }}}
            }
        }
    }
    private fun gateStart(){
        ensure(AccessibilityBridge.available,"Existing accessibility service not bound")
        val prefs=context.getSharedPreferences("assistant_profiles",0);settingsBackup=prefs.all.toMap()
        firstFrames=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        ProfileStore(context).save(AppProfile(context.packageName,"隔离验收页面",AssistantMode.VIDEO))
        ContextCompat.startForegroundService(activity,Intent(context,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.VIDEO.name))
        later(1800){gateTick()}
    }
    private fun hybrid(){
        ensure(activity.hasWindowFocus() && AccessibilityBridge.available,"Own proof surface must have focus")
        @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay
        val size=android.graphics.Point();@Suppress("DEPRECATION") display.getRealSize(size)
        val screen=ScreenGeometry(size.x,size.y,display.rotation)
        val location=IntArray(2);activity.target.getLocationOnScreen(location)
        val inset=14*activity.resources.displayMetrics.density
        val aim=CursorPoint((location[0]+activity.target.width*.5f-inset)/(size.x-1-2*inset),
            (location[1]+activity.target.height*.5f-inset)/(size.y-1-2*inset))
        val controller=com.airgesture.app.gesture.HybridGestureController().apply{configure(com.airgesture.app.settings.ActionSettings(drag=true))}
        var t=1000L
        fun feed(point:Boolean=false,pinch:Float=.9f,x:Float=.5f,home:Boolean=false,cursor:CursorPoint=aim,tap:Float=0f):com.airgesture.app.gesture.HybridDecision {
            t+=120
            val page=!point && !home && pinch>.5f
            return controller.update(com.airgesture.app.gesture.HandObservation(t,cursor,CursorPoint(x,.6f),pinch-tap*.5f,page,false,
                swipePose=page,pointerPose=point,pagePose=page,homePose=home,fingerDirection=com.airgesture.app.gesture.FingerDirection.LEFT,thumbLocal=CursorPoint(tap,0f)),t+80)
        }
        repeat(8){feed(point=true)}
        val tapped=listOf(.15f,.32f,.16f,.32f).map{feed(point=true,tap=it,cursor=aim.copy(y=(aim.y+.3f).coerceAtMost(.9f)))}
        val click=tapped.last()
        ensure(tapped.count{it.gesture.action==ActionKind.CLICK}==1 && click.gesture.point==aim,"Two noncontact thumb taps must retain noncentral aim")
        GlobalSession.publish(GlobalStatus(true,false,"仅当前验收页面"));GlobalSession.actionsEnabled=true
        fun send(result:com.airgesture.app.gesture.HybridDecision,next:()->Unit){
            val mapped=checkNotNull(result.mapped(null))
            val point=mapped.second?.let{com.airgesture.app.cursor.CoordinateMapper().toPixels(it,size.x,size.y,inset)}
            ensure(AccessibilityBridge.dispatch(SystemAction(mapped.first,point,screen,SystemClock.uptimeMillis())){r->later(350){
                ensure(r==ActionResult.COMPLETED,"Integrated ${mapped.first}: $r");next()
            }},"Integrated action rejected")
        }
        send(click){
            ensure(activity.clicks==1,"Actual pointed target must click exactly once")
            ensure(ProofChildActivity.resumed,"Thumb approach click must open the target page")
            later(450){
                repeat(12){feed()}
                val back=listOf(.46f,.42f,.38f).map{feed(x=it)}.single{it.gesture.action==ActionKind.BACK}
                send(back){
                    ensure(activity.hasWindowFocus(),"Real Back must return to own parent surface")
                    val home=List(8){feed(home=true)}.single{it.gesture.action==ActionKind.HOME}
                    send(home){
                        ensure(AccessibilityBridge.foregroundPackage=="com.android.launcher","Real Home must reach OPPO launcher")
                        finish(true,"Two thumb approaches -> one real targeted click opens own page, sideways palm Back and OPPO Home; no social action sent")
                    }
                }
            }
        }
    }
    private fun directional(){
        ensure(activity.hasWindowFocus() && AccessibilityBridge.available,"Own direction surface must have focus")
        @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay
        val size=android.graphics.Point();@Suppress("DEPRECATION") display.getRealSize(size)
        val screen=ScreenGeometry(size.x,size.y,display.rotation)
        GlobalSession.publish(GlobalStatus(true,false,"仅本应用四向滑动验收"));GlobalSession.actionsEnabled=true
        val directions=listOf(ActionKind.SWIPE_UP,ActionKind.SWIPE_DOWN,ActionKind.SWIPE_LEFT,ActionKind.SWIPE_RIGHT)
        var index=0
        fun next(){
            if(index==directions.size){finish(true,"Four-up / two-down / five-left / two-right + synthetic motion -> four real Android strokes with measured directions on own surface; no third-party input");return}
            val expected=directions[index++]
            val c=com.airgesture.app.gesture.HybridGestureController().apply{configure(com.airgesture.app.settings.ActionSettings())}
            var time=1000L
            fun feed(v:Float=0f):com.airgesture.app.gesture.HybridDecision{
                time+=120
                val dx=when(expected){ActionKind.SWIPE_LEFT->-v;ActionKind.SWIPE_RIGHT->v;else->0f}
                val dy=when(expected){ActionKind.SWIPE_UP->-v;ActionKind.SWIPE_DOWN->v;else->0f}
                return c.update(com.airgesture.app.gesture.HandObservation(time,CursorPoint(.5f,.5f),CursorPoint(.5f+dx,.6f+dy),.9f,true,false,
                    pagePose=true,fourFingerPose=expected in listOf(ActionKind.SWIPE_UP,ActionKind.SWIPE_LEFT),twoFingerPose=expected in listOf(ActionKind.SWIPE_DOWN,ActionKind.SWIPE_RIGHT),fiveFingerPose=expected==ActionKind.SWIPE_LEFT,fingerDirection=com.airgesture.app.gesture.FingerDirection.UP),time+70)
            }
            repeat(8){feed()}
            val result=listOf(.04f,.08f,.12f).map{feed(it)}.single{it.gesture.action!=null}
            ensure(result.mapped(null)?.first==expected,"Unexpected directional mapping")
            val returns=listOf(.08f,.04f,0f).map{feed(it)}
            ensure(returns.none{it.gesture.action!=null},"Same-pose return must not dispatch an opposite action")
            val before=activity.touchEvents.size
            ensure(AccessibilityBridge.dispatch(SystemAction(expected,null,screen,SystemClock.uptimeMillis())){r->later(100){
                ensure(r==ActionResult.COMPLETED,"$expected callback $r")
                val xs=activity.touchRawXs.drop(before);val ys=activity.touchRawYs.drop(before)
                ensure(xs.size>2 && activity.touchEvents.drop(before).count{it==MotionEvent.ACTION_UP}==1,"One complete stroke required")
                val dx=xs.last()-xs.first();val dy=ys.last()-ys.first()
                ensure(when(expected){ActionKind.SWIPE_UP->dy < -size.y*.35f;ActionKind.SWIPE_DOWN->dy > size.y*.35f
                    ActionKind.SWIPE_LEFT->dx < -size.x*.35f;else->dx > size.x*.35f},"Measured stroke direction mismatch: $dx,$dy")
                samples.put(JSONObject().put("action",expected.name).put("dx",dx).put("dy",dy).put("result",r.name))
                next()
            }},"Direction dispatch rejected")
        }
        next()
    }
    private fun globalStart(){
        ensure(AccessibilityBridge.available,"Accessibility must be bound")
        val prefs=context.getSharedPreferences("assistant_profiles",0);settingsBackup=prefs.all.toMap()
        ProfileStore(context).save(AppProfile(context.packageName,"全局相机连续性验收",AssistantMode.VIDEO))
        visits=GlobalSession.log.export().lineSequence().count{it.startsWith("capture_started ")}
        firstFrames=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        ContextCompat.startForegroundService(activity,Intent(context,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name))
        later(1200){globalTick()}
    }
    private fun menu(){
        ensure(activity.hasWindowFocus() && AccessibilityBridge.available,"Own menu surface must have focus")
        @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay
        val size=android.graphics.Point();@Suppress("DEPRECATION") display.getRealSize(size)
        GlobalSession.publish(GlobalStatus(true,false,"仅本应用菜单验收"));GlobalSession.actionsEnabled=true
        ensure(AccessibilityBridge.proveOwnMenu(SystemAction(ActionKind.DISLIKE,null,ScreenGeometry(size.x,size.y,display.rotation),SystemClock.uptimeMillis())){result->later(200){
            ensure(result==ActionResult.COMPLETED,"Menu command returned $result")
            ensure(activity.dislikeSelections==1,"Exact menu entry must be selected once")
            finish(true,"Real long press -> exact menu-label lookup -> one own-app dummy selection; no third-party dislike sent")
        }},"Own menu proof rejected")
    }
    private fun globalTick(){
        ensure(SystemClock.uptimeMillis()-begin<22000,"Global camera proof deadline")
        ensure(!GlobalSession.actionsEnabled,"Global camera proof cannot inject input")
        val count=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        if(count>=firstFrames+3){
            when(stage){
                0->{stage=1;firstFrames=count;activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))}
                1->{ensure(AccessibilityBridge.foregroundPackage=="com.android.launcher","Expected launcher foreground");stage=2;firstFrames=count;activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}
                2->{ensure(AccessibilityBridge.foregroundPackage=="com.android.settings","Expected Android settings foreground")
                    val starts=GlobalSession.log.export().lineSequence().count{it.startsWith("capture_started ")}-visits
                    ensure(starts==1,"Allowed profile/window changes must not restart camera: $starts starts")
                    samples.put(JSONObject().put("capture_starts",starts))
                    finish(true,"One GLOBAL camera capture continues across own configured profile, launcher and settings; no injected input");return}
            }
        }
        later(350){globalTick()}
    }
    private fun swipe(){
        ensure(activity.hasWindowFocus() && AccessibilityBridge.available,"Own surface must be focused with accessibility bound")
        val gate=com.airgesture.app.vision.HandTrackingGate()
        val cursor=com.airgesture.app.cursor.CursorController()
        val machine=com.airgesture.app.gesture.GestureStateMachine(com.airgesture.app.settings.ActionSettings(sceneSwipes=true,doublePinch=true,palmFlip=true))
        val profile=ProfileStore(context).profiles().first{it.packageName=="com.xingin.xhs"}
        val emitted=mutableListOf<ActionKind>();var t=1000L
        // Synthetic 7 FPS relaxed-palm trajectory, never described as camera or human evidence.
        for(dy in List(8){0f}+listOf(-.03f,-.06f,-.09f,-.12f,-.15f)){
            t+=140
            val p=MutableList(21){CursorPoint(.5f,.6f+dy)}.apply{
                this[0]=CursorPoint(.5f,.72f+dy);this[4]=CursorPoint(.34f,.55f+dy)
                this[5]=CursorPoint(.44f,.60f+dy);this[6]=CursorPoint(.44f,.50f+dy);this[8]=CursorPoint(.44f,.37f+dy)
                this[9]=CursorPoint(.50f,.59f+dy);this[10]=CursorPoint(.50f,.47f+dy);this[12]=CursorPoint(.50f,.34f+dy)
                this[13]=CursorPoint(.56f,.60f+dy);this[14]=CursorPoint(.56f,.50f+dy);this[16]=CursorPoint(.56f,.38f+dy)
                this[17]=CursorPoint(.62f,.62f+dy);this[18]=CursorPoint(.62f,.55f+dy);this[20]=CursorPoint(.62f,.54f+dy)
            }
            val tip=gate.update(p,t,t+90,480,640);val point=cursor.update(tip,t,t+90)
            val h=point?.let{com.airgesture.app.gesture.HandObservation.from(p,480,640,t,it)}
            machine.update(h,t+90).action?.let{ModeRouter.map(it,profile)?.kind?.let(emitted::add)}
        }
        ensure(emitted==listOf(ActionKind.SWIPE_UP),"Expected one mapped upward swipe, got $emitted")
        @Suppress("DEPRECATION") val display=activity.windowManager.defaultDisplay
        val size=android.graphics.Point();@Suppress("DEPRECATION") display.getRealSize(size)
        val screen=ScreenGeometry(size.x,size.y,display.rotation)
        GlobalSession.publish(GlobalStatus(true,false,"仅当前测试页面"));GlobalSession.actionsEnabled=true
        ensure(AccessibilityBridge.dispatch(SystemAction(emitted.single(),null,screen,SystemClock.uptimeMillis())){result->later(100){
            ensure(result==ActionResult.COMPLETED,"Swipe callback $result")
            ensure(activity.touchEvents.count{it==MotionEvent.ACTION_DOWN}==1 && activity.touchEvents.count{it==MotionEvent.ACTION_UP}==1,"Swipe must be one real contact")
            ensure(!activity.touchEvents.contains(MotionEvent.ACTION_CANCEL),"Swipe cancelled")
            ensure(activity.touchRawYs.first()-activity.touchRawYs.last()>size.y*.4f,"Actual touch must move upwards across screen")
            ensure(activity.clicks==0,"Swipe must not click")
            finish(true,"Synthetic 7 FPS landmarks -> tracking gate -> scene gesture -> Xiaohongshu mapping -> real Android upward stroke on own surface; no third-party input")
        }},"Swipe dispatch rejected")
    }
    private fun gateTick(){
        android.util.Log.i("AirGesture","gate_proof_stage=$stage foreground_own=${AccessibilityBridge.foregroundPackage==context.packageName} available=${AccessibilityBridge.available}")
        ensure(SystemClock.uptimeMillis()-begin<18000,"App gate deadline")
        ensure(!GlobalSession.actionsEnabled,"Gate test must not enable input")
        val log=GlobalSession.log.export()
        when(stage){
            0->if(log.contains("capture_started mode=VIDEO") && log.lineSequence().count{it.startsWith("global_frame ")}>firstFrames){
                visits=log.lineSequence().count{it.startsWith("capture_started mode=VIDEO")};firstFrames=log.lineSequence().count{it.startsWith("global_frame ")};stage=1
                activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            }
            1->if(GlobalSession.status.value?.message?.contains("相机已释放")==true){
                firstFrames=log.lineSequence().count{it.startsWith("global_frame ")};stage=2;activity.startActivity(Intent(activity,ActionTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            2->if(log.lineSequence().count{it.startsWith("capture_started mode=VIDEO")}>visits && log.lineSequence().count{it.startsWith("global_frame ")}>firstFrames){finish(true,"Actual foreground events: own whitelist -> launcher camera release -> own app camera reacquire");return}
        }
        later(350){gateTick()}
    }
    private fun soakStart(){
        // No EXTRA_CONTROL: 30 minutes of real CameraX/model/service, no injected gestures.
        ContextCompat.startForegroundService(activity,Intent(context,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name))
        later(2000){soakTick()}
    }
    private fun xhsStart(){
        ensure(AccessibilityBridge.available,"Existing accessibility service not bound")
        val profile=ProfileStore(context).profiles().first{it.packageName=="com.xingin.xhs"}
        ensure(profile.enabled && profile.mode==AssistantMode.VIDEO,"Xiaohongshu profile must enable video mode")
        firstFrames=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        ContextCompat.startForegroundService(activity,Intent(context,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.VIDEO.name))
        later(500){activity.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(profile.packageName)));later(1000){xhsTick()}}
    }
    private fun xhsTick(){
        ensure(!GlobalSession.actionsEnabled,"Xiaohongshu camera check must not inject input")
        ensure(SystemClock.uptimeMillis()-begin<25000,"Xiaohongshu camera deadline; foreground=${AccessibilityBridge.foregroundPackage}")
        val count=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        if(SystemClock.uptimeMillis()-begin>=14000 && AccessibilityBridge.foregroundPackage=="com.xingin.xhs" && count>=firstFrames+6){finish(true,"Real Xiaohongshu foreground started camera and delivered model frames using unchanged app presets; no input injected");return}
        later(500){xhsTick()}
    }
    private fun soakTick(){
        ensure(GlobalSession.running,"Service stopped during soak");ensure(!GlobalSession.actionsEnabled,"Soak must not enable system actions")
        val seconds=(SystemClock.uptimeMillis()-begin)/1000
        val battery=context.registerReceiver(null,android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val sample=JSONObject().put("elapsed_seconds",seconds).put("pss_kb",Debug.getPss())
            .put("process_cpu_ms",android.os.Process.getElapsedCpuTime())
            .put("battery_temperature_c",(battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0) ?: 0)/10f)
            .put("thermal",if(Build.VERSION.SDK_INT>=29)context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else -1)
            .put("summary",GlobalSession.log.export().lineSequence().lastOrNull{it.startsWith("session_seconds=")})
        samples.put(sample);write(false,"RUNNING")
        android.util.Log.i("AirGesture","soak_sample=$sample")
        if(seconds>=1800){finish(true,"30 minute real-camera service soak; no deliberate hands or injected input");return}
        later(60_000){soakTick()}
    }
    private fun write(passed:Boolean,message:String){
        val data=JSONObject().put("run_id",runId).put("proof",kind).put("passed",passed).put("message",message).put("elapsed_ms",SystemClock.uptimeMillis()-begin)
            .put("clicks",activity.clicks).put("dummy_dislike_selections",activity.dislikeSelections).put("touch_events",org.json.JSONArray(activity.touchEvents)).put("samples",samples)
            .put("touch_raw_y",org.json.JSONArray(activity.touchRawYs))
            .put("raw_images_saved",false).put("real_hand",false)
        java.io.File(activity.filesDir,"proof-$kind.json").writeText(data.toString(2))
    }
    private fun finish(passed:Boolean,message:String){
        if(done)return;done=true;handler.removeCallbacksAndMessages(null)
        pointerOverlay?.close();pointerOverlay=null
        proofCamera?.close();proofCamera=null
        AccessibilityBridge.releaseDrag();GlobalSession.actionsEnabled=false
        context.stopService(Intent(context,GestureForegroundService::class.java));GlobalSession.publish(GlobalStatus(message=message))
        settingsBackup?.let{backup->val edit=context.getSharedPreferences("assistant_profiles",0).edit().clear()
            backup.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()}
        write(passed,message)
        android.util.Log.i("AirGesture","device_proof=$kind passed=$passed message=$message")
        activity.target.text="${if(passed)"通过" else "失败"}：$message"
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
