package com.airgesture.app.session

enum class ControlMode(val label:String){VOICE_ONLY("语音控制"),NUMBER_SELECT("按钮选择"),GRID_SELECT("分区选择"),VISUAL_MOUSE("手势控制"),PAUSED("控制已暂停")}

/** Only one selection/visual channel exists. Resume always returns to camera-free voice. */
class ControlModeManager(var mouseSleepMs:Long=10_000){
    var mode=ControlMode.VOICE_ONLY;private set
    private var lastHand=0L
    private var selectionStarted=0L
    fun enter(value:ControlMode,now:Long){mode=value;if(value==ControlMode.VISUAL_MOUSE)lastHand=now
        if(value in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT))selectionStarted=now}
    fun hand(present:Boolean,now:Long){if(mode==ControlMode.VISUAL_MOUSE && present)lastHand=now}
    fun expiry(now:Long):String?=when{
        mode==ControlMode.VISUAL_MOUSE && mouseSleepMs>0 && now-lastHand>=mouseSleepMs->"隔空鼠标已休眠"
        mode in setOf(ControlMode.NUMBER_SELECT,ControlMode.GRID_SELECT) && now-selectionStarted>=20_000->"选择已超时"
        else->null
    }
}
