package com.airgesture.app.session

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Observer
import com.airgesture.app.accessibility.*
import com.airgesture.app.command.*
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.selection.*
import com.airgesture.app.service.GlobalSession
import com.airgesture.app.voice.*

/** Session orchestration only. Existing CameraX/hand pipeline remains owned by the foreground service. */
class HybridControlSession(private val context:Context,var config:VoiceCommandConfig,
    private val geometry:()->ScreenGeometry,private val cameraPermitted:()->Boolean,
    private val setVisual:(Boolean)->Unit,private val dispatch:(ControlCommand,NodeSelection?)->Boolean,
    private val changed:()->Unit,private val executeCommands:Boolean=true,
    initialVoice:Boolean=true,initialGestures:Boolean=false):AutoCloseable {
    private val modes=ControlModeManager(config.mouseSleepMs)
    private var commands=VoiceCommandEngine(config)
    private val history=VoiceHistory(context)
    var voiceEnabled=initialVoice;private set
    var gesturesEnabled=initialGestures;private set
    private val selection=SelectionOverlay(context)
    private val grid=TargetRegionController()
    private var numbers:NumberSnapshot?=null
    private var selectedVersion=0L
    private var selectedScreen:ScreenGeometry?=null
    private var audio:AudioInputManager?=null
    private var status=ControlStatus(running=true,keywordThreshold=config.keywordThreshold,message=voiceHint())
    private fun voiceHint()=if(!voiceEnabled)"手势控制已开启 · 麦克风关闭" else if(config.activation==VoiceActivation.WAKE_WORD)"先说「${config.wakeWord}」再说命令；说「开启手势」可举手操作" else "直接说上滑、返回、显示按钮或开启手势"
    private val voiceTime=RuntimeCounter();private val cameraTime=RuntimeCounter();private val modelTime=RuntimeCounter()
    private var closed=false
    private var lastPublished=0L
    private var receiptVersion=0L
    private var videoAssist=false
    private var focusSuspended=false
    private val focusHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val focusRetries=VideoAssistRetryLimiter()
    private val mediaFocus=MediaAudioFocus(context,::onAudioFocusLost)
    private fun onAudioFocusLost(){
        if(closed)return
        commands.cancelWake();focusSuspended=true
        if(mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))enter(ControlMode.VOICE_ONLY,"页面声音变化，已退出选择")
        GlobalSession.log.add("voice_media_focus=LOST assist=$videoAssist")
        focusHandler.removeCallbacksAndMessages(null)
        if(videoAssist && micActive && !paused && focusRetries.allow(now())){
            status=status.copy(message="视频切换中，正在恢复外放辅助")
            scheduleVideoFocus(0)
        }else{
            videoAssist=false
            status=status.copy(message="声音控制已交还其他应用；需要时重新开启视频外放辅助")
        }
        publish();changed()
    }
    private fun scheduleVideoFocus(attempt:Int){
        focusHandler.postDelayed({
            if(closed || !videoAssist || !micActive || paused)return@postDelayed
            val unlocked=context.getSystemService(android.os.PowerManager::class.java).isInteractive && !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            if(unlocked && mediaFocus.mediaPlaying()){
                focusSuspended=false
                status=status.copy(message="视频外放辅助开启 · ${voiceHint()}")
                GlobalSession.log.add("voice_media_focus=CLIP_RESUME")
                publish();changed()
            }else if(unlocked && attempt<2)scheduleVideoFocus(attempt+1)
            else{
                videoAssist=false;focusSuspended=false
                status=status.copy(message="视频外放辅助已退出；播放视频后可从通知重新开启")
                publish();changed()
            }
        },650)
    }
    val mode get()=modes.mode
    val visual get()=mode==ControlMode.VISUAL_MOUSE
    val paused get()=mode==ControlMode.PAUSED
    val micActive get()=status.audio.state=="ACTIVE"
    private var interruptionBaseline:Long?=null
    private val interrupted=Observer<Long>{value->
        if(interruptionBaseline!=null && interruptionBaseline!=value){enter(ControlMode.PAUSED,"无障碍已中断，请确认服务后手动恢复");changed()}
        interruptionBaseline=value
    }
    private val accessibilityConnection=Observer<Boolean>{available->if(!available){enter(ControlMode.PAUSED,"无障碍已断开，请回到小空开启后恢复");changed()}}
    private val invalidated=Observer<Long>{if(mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT)){enter(ControlMode.VOICE_ONLY,"页面已滚动或切换，请重新说显示按钮");publish();changed()}}
    fun start(){ControlSessionState.commandHandler=::execute;ControlSessionState.keywordHandler=::onKeyword;ControlSessionState.videoAssistHandler=::toggleVideoAssist;ControlSessionState.configHandler=::applyConfig;AccessibilityBridge.selectionInvalidated.observeForever(invalidated);AccessibilityBridge.interruptions.observeForever(interrupted);AccessibilityBridge.connected.observeForever(accessibilityConnection);if(voiceEnabled)startAudio();enter(ControlMode.VOICE_ONLY,voiceHint());publish()}
    fun setChannels(voice:Boolean,gestures:Boolean){
        if(closed)return
        gesturesEnabled=gestures && cameraPermitted()
        if(voiceEnabled!=voice){voiceEnabled=voice;if(voice)startAudio() else stopAudio()}
        enter(ControlMode.VOICE_ONLY,if(gestures && !gesturesEnabled)"请在权限管理中允许相机，再打开手势开关" else voiceHint())
        publish();changed()
    }
    private fun stopAudio(){
        audio?.close();audio=null;commands.reset();voiceTime.active(false,now())
        videoAssist=false;focusSuspended=false;focusHandler.removeCallbacksAndMessages(null);mediaFocus.close()
        status=status.copy(audio=AudioDiagnostics(),listening=false)
    }
    private fun applyConfig(value:VoiceCommandConfig){
        if(closed)return
        config=value;commands=VoiceCommandEngine(value);modes.mouseSleepMs=value.mouseSleepMs
        status=status.copy(keywordThreshold=value.keywordThreshold)
        enter(if(paused)ControlMode.PAUSED else ControlMode.VOICE_ONLY,"设置已生效 · ${if(paused)"控制仍暂停，可关闭再打开开关恢复" else voiceHint()}")
        if(voiceEnabled)startAudio();publish();changed()
    }
    fun toggleVideoAssist(){
        if(closed || !micActive || paused)return
        videoAssist=!videoAssist
        focusSuspended=false;focusRetries.reset();focusHandler.removeCallbacksAndMessages(null)
        if(!videoAssist){
            commands.cancelWake()
            if(mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))enter(ControlMode.VOICE_ONLY,"已退出选择")
        }
        status=status.copy(message=if(videoAssist)"视频外放辅助开启 · ${voiceHint()}" else "视频外放辅助关闭，已交还声音控制")
        publish();changed()
    }
    private fun startAudio(){
        audio?.close();commands.reset()
        status=status.copy(audio=AudioDiagnostics(state="STARTING"));voiceTime.active(false,now())
        audio=AudioInputManager(context,config,::onKeyword){d->
            if(!closed){
                if(d.unmatchedUtterances>status.audio.unmatchedUtterances){
                    history.add("声音","未匹配","听到了说话，但没有匹配到完整指令")
                    if(!paused)status=status.copy(message="听到了声音，未识别成完整指令；请再说一次")
                }
                status=status.copy(audio=d);voiceTime.active(d.state=="ACTIVE",now())
                selection.ready(d.digitsActive)
                if(d.state in setOf("INTERRUPTED","ERROR"))enter(ControlMode.PAUSED,d.error ?: "麦克风已停止，请回到应用继续")
                publish();changed()
            }
        }.also{it.vocabulary=vocabularyForMode();it.start()}
    }
    fun onKeyword(event:KeywordEvent){
        if(closed || !voiceEnabled || !micActive)return
        val decision=commands.accept(event,mode,now())
        status=status.copy(lastKeyword=event.keyword,lastKeywordAt=now(),lastDecision=decision.reason)
        history.add(event.keyword,"听到", "${event.source} · ${decision.reason} · ${mode.name}")
        GlobalSession.log.add("voice_keyword=${event.keyword} source=${event.source} confidence=unavailable threshold=${if(event.source==KeywordSource.KWS)event.threshold else "not_applicable"} t=${event.timestamp} gate=${decision.reason} decode_ms=${event.decodeMs} mode=$mode")
        if(decision.awakened){focusSuspended=false;status=status.copy(message="我在，接下来 5 秒说一个命令")
            if(executeCommands)android.widget.Toast.makeText(context,"小空在 · 请说一个命令",android.widget.Toast.LENGTH_SHORT).show()
        }
        if(decision.command==null && !decision.awakened){
            status=status.copy(message=VoiceOutcome.gate(decision.reason,config.wakeWord));announce(status.message)
        }
        decision.command?.let{if(executeCommands)execute(it) else {
            status=status.copy(commandCount=status.commandCount+1,lastDecision="OBSERVE_ONLY")
            GlobalSession.log.add("voice_would_execute=${it.kind} observe_only=1")
        }};publish();changed()
    }
    fun execute(command:ControlCommand){
        if(closed)return
        if(paused && command.kind!=CommandKind.RESUME_CONTROL)return
        if(command.kind in setOf(CommandKind.SHOW_NUMBERS,CommandKind.SHOW_GRID))focusSuspended=false
        status=status.copy(commandCount=status.commandCount+1)
        history.add(command.kind.name,"放行",mode.name)
        when(command.kind){
            CommandKind.START_MOUSE->if(cameraPermitted()){gesturesEnabled=true;enter(ControlMode.VISUAL_MOUSE,"手势已开启 · 食指瞄准，拇指靠近两次点击")} else enter(ControlMode.VOICE_ONLY,"请回到小空，允许相机后重新开始会话")
            CommandKind.STOP_MOUSE->{gesturesEnabled=false;enter(ControlMode.VOICE_ONLY,"手势已关闭，相机释放")}
            CommandKind.CANCEL->enter(ControlMode.VOICE_ONLY,"已退出选择")
            CommandKind.PAUSE_CONTROL->enter(ControlMode.PAUSED,"已暂停操作 · 说恢复控制继续")
            CommandKind.RESUME_CONTROL->{if(!AccessibilityBridge.available)enter(ControlMode.PAUSED,"请先回到系统设置开启无障碍") else {enter(ControlMode.VOICE_ONLY,"已恢复控制");if(voiceEnabled && !micActive)startAudio()}}
            CommandKind.SHOW_NUMBERS,CommandKind.SHOW_GRID->showChoices(command.kind==CommandKind.SHOW_GRID)
            CommandKind.SELECT_NUMBER->select(command.number ?: return,command.detectedAt)
            else->{
                if(mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))enter(ControlMode.VOICE_ONLY,"已退出选择")
                val beforeReceipt=receiptVersion
                val admitted=dispatch(command,null)
                if(receiptVersion==beforeReceipt)status=status.copy(lastDecision=if(admitted)"DISPATCHED" else "REJECTED",message=if(admitted)"已提交${command.systemAction?.label ?: "操作"}" else "操作未完成，请确认无障碍和当前页面")
            }
        }
        if(command.systemAction==null)history.add(command.kind.name,"结果",status.message)
        publish();changed()
    }
    private fun select(number:Int,time:Long){
        if(mode !in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))return
        if(selectedVersion!=AccessibilityBridge.contextVersion || selectedScreen!=geometry()){enter(ControlMode.VOICE_ONLY,"页面变了，请重新选择");return}
        val snapshot=numbers ?: return
        if(mode==ControlMode.GRID_SELECT && !grid.choosingButtons){
            if(!grid.chooseRegion(number)){status=status.copy(message="这个区域没有按钮，请读屏幕显示的区域号");announce(status.message);return}
            renderRegions();return
        }
        val item=if(mode==ControlMode.NUMBER_SELECT)snapshot.targets.firstOrNull{it.number==number} else grid.target(number)
        if(item==null){status=status.copy(message="没有这个按钮，请读屏幕上显示的编号");announce(status.message);return}
        val screen=geometry();val p=item.bounds.center
        selection.close()
        val admitted=dispatch(ControlCommand(CommandKind.CLICK,time,CursorPoint(p.x/screen.width,p.y/screen.height)),NodeSelection(snapshot.id,item.number))
        enter(ControlMode.VOICE_ONLY,if(admitted)"已选择 $number 号按钮" else "按钮已变化或不可点击，请重新说显示按钮")
        if(!admitted)announce(status.message)
    }
    private fun showChoices(regional:Boolean){
        enter(ControlMode.VOICE_ONLY,"正在查找屏幕按钮")
        val screen=geometry();val snapshot=AccessibilityBridge.captureNumbers(screen,if(regional)80 else 20)
        if(snapshot==null){status=status.copy(message="当前页面未提供可点击按钮，请使用隔空鼠标");announce(status.message);return}
        numbers=snapshot;selectedVersion=AccessibilityBridge.contextVersion;selectedScreen=screen
        modes.enter(if(regional)ControlMode.GRID_SELECT else ControlMode.NUMBER_SELECT,now())
        if(regional){grid.start(snapshot.targets,screen.width,screen.height);renderRegions()}
        else{selection.numbers(snapshot.targets);status=status.copy(message="已标出 ${snapshot.targets.size} 个按钮，读数字加「号」点击${if(snapshot.truncated)"；更多按钮可说分区选择" else ""}",selectionPhase="BUTTONS",choices=snapshot.targets)}
        updateVocabulary();selection.ready(status.audio.digitsActive)
        GlobalSession.log.add("voice_choices mode=$mode count=${snapshot.targets.size} truncated=${snapshot.truncated}")
    }
    private fun renderRegions(){
        if(grid.choosingButtons){selection.numbers(grid.displayButtons());status=status.copy(message="该区域有 ${grid.buttons.size} 个按钮，读数字加「号」点击${if(grid.truncated)"；重叠目标较多，仅显示前 20 个" else ""}",selectionPhase="BUTTONS",choices=grid.displayButtons())}
        else{selection.regions(grid.regions,grid.visibleTargets());status=status.copy(message="只标出有按钮的区域，先读区域号",selectionPhase="REGIONS",choices=grid.regions.map{NumberTarget(it.number,it.bounds)})}
        selection.ready(status.audio.digitsActive)
    }
    private fun announce(message:String){if(executeCommands)android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_SHORT).show()}
    private fun enter(value:ControlMode,message:String){
        if(value==ControlMode.PAUSED && !paused){history.add("状态","暂停",message);announce(message)}
        if(value==ControlMode.PAUSED){videoAssist=false;focusSuspended=false;focusHandler.removeCallbacksAndMessages(null);commands.cancelWake();mediaFocus.close()}
        selection.close();AccessibilityBridge.clearNumbers();numbers=null;grid.reset();selectedScreen=null
        val resolved=if(value==ControlMode.VOICE_ONLY && gesturesEnabled)ControlMode.VISUAL_MOUSE else value
        modes.enter(resolved,now());status=status.copy(mode=resolved,message=message,selectionPhase="NONE",choices=emptyList())
        setVisual(resolved==ControlMode.VISUAL_MOUSE);updateVocabulary()
    }
    private fun vocabularyForMode()=when(mode){ControlMode.PAUSED->VoiceVocabulary.PAUSED;ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT->VoiceVocabulary.SELECTION;else->VoiceVocabulary.COMMANDS}
    private fun updateVocabulary(){
        val next=vocabularyForMode()
        if(audio?.vocabulary!=next)status=status.copy(audio=status.audio.copy(digitsActive=next==VoiceVocabulary.SELECTION && status.audio.decoder=="UTTERANCE" && micActive))
        audio?.vocabulary=next
    }
    fun visualFailure(message:String){gesturesEnabled=false;enter(ControlMode.VOICE_ONLY,"相机暂时不可用；请回到应用检查权限或占用");publish();changed();GlobalSession.log.add("voice_camera_error=$message")}
    fun handFrame(present:Boolean,inferenceMs:Long){modes.hand(present,now());status=status.copy(handFrames=status.handFrames+1,handInferenceMs=status.handInferenceMs+inferenceMs)}
    fun resources(camera:Boolean,model:Boolean){cameraTime.active(camera,now());modelTime.active(model,now());status=status.copy(cameraBound=camera,handModelLoaded=model);publish()}
    fun receipt(receipt:ActionReceipt){
        if(receipt.kind!=ActionKind.DRAG_MOVE)history.add(receipt.kind.name,if(receipt.result==ActionResult.DISPATCHED)"提交系统" else "系统结果","${receipt.result} · ${receipt.reason} · ${receipt.elapsedMs} ms")
        if(receipt.result!=ActionResult.DISPATCHED){
            receiptVersion++
            status=status.copy(lastDecision=receipt.result.name,lastActionMs=receipt.elapsedMs,
                message=if(receipt.result==ActionResult.COMPLETED)"已发送${receipt.kind.label} · 可以继续说指令" else VoiceOutcome.action(receipt.reason))
            if(receipt.result!=ActionResult.COMPLETED)announce(status.message)
            publish();changed()
        }
    }
    fun tick(){
        if(closed)return
        modes.expiry(now())?.let{if(mode==ControlMode.VISUAL_MOUSE)gesturesEnabled=false;enter(ControlMode.VOICE_ONLY,it);changed()}
        if(mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT) && (selectedVersion!=AccessibilityBridge.contextVersion || selectedScreen!=geometry()))enter(ControlMode.VOICE_ONLY,"页面变了，请重新选择")
        if(now()-lastPublished>=1000){
            val wasListening=status.listening
            if(wasListening && !commands.listening(now()))status=status.copy(message="本次等待已结束，请重新说「${config.wakeWord}」")
            publish();if(wasListening!=status.listening)changed()
        }
    }
    private fun publish(){if(closed)return;val t=now();lastPublished=t
        val listening=commands.listening(t)
        val selecting=mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT)
        mediaFocus.setWanted(micActive && !paused && !focusSuspended && (videoAssist || listening || selecting))
        if(status.mediaFocus!=mediaFocus.state)GlobalSession.log.add("voice_media_focus=${mediaFocus.state} assist=$videoAssist listening=$listening selection=$selecting")
        if(mediaFocus.state=="DENIED")status=status.copy(message="当前应用未允许临时降音，请降低视频音量或使用耳机后重试")
        status=status.copy(mode=mode,voiceMs=voiceTime.elapsed(t),cameraMs=cameraTime.elapsed(t),handModelMs=modelTime.elapsed(t),videoAssist=videoAssist,mediaFocus=mediaFocus.state,listening=listening,voiceEnabled=voiceEnabled,gesturesEnabled=gesturesEnabled);ControlSessionState.publish(status)}
    private fun now()=SystemClock.uptimeMillis()
    override fun close(){
        if(closed)return
        ControlSessionState.commandHandler=null;ControlSessionState.keywordHandler=null;ControlSessionState.videoAssistHandler=null;ControlSessionState.configHandler=null
        videoAssist=false;commands.cancelWake();mediaFocus.close()
        focusHandler.removeCallbacksAndMessages(null)
        status=status.copy(audio=status.audio.copy(state="OFF"))
        audio?.close();audio=null;selection.close();AccessibilityBridge.clearNumbers();AccessibilityBridge.selectionInvalidated.removeObserver(invalidated);AccessibilityBridge.interruptions.removeObserver(interrupted);AccessibilityBridge.connected.removeObserver(accessibilityConnection)
        voiceTime.active(false,now());cameraTime.active(false,now());modelTime.active(false,now());publish();closed=true
        ControlSessionState.publish(status.copy(running=false,voiceEnabled=false,gesturesEnabled=false,audio=status.audio.copy(state="OFF",digitsActive=false),cameraBound=false,handModelLoaded=false,videoAssist=false,mediaFocus="OFF",listening=false,selectionPhase="NONE",choices=emptyList(),message="控制已停止，麦克风和相机关闭"))
    }
}
