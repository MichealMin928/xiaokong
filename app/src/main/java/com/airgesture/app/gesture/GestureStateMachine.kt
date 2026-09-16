package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.ActionConfig
import com.airgesture.app.settings.ActionSettings

enum class PinchState { UNARMED, OPEN, APPROACH, PINCH_START, PINCH_HOLD, RELEASE }
data class GestureDecision(val action: ActionKind? = null, val point: CursorPoint? = null,
    val frozenCursor: CursorPoint? = null, val pauseChanged: Boolean = false,
    val message: String = "", val confirmation: Int = 0)

/** Mutually exclusive temporal gestures. No Android calls and no retries after an emitted action. */
class GestureStateMachine(var settings: ActionSettings = ActionSettings(), private val config: ActionConfig = ActionConfig()) {
    var pinchState = PinchState.UNARMED; private set
    var paused = false; private set
    private var lastTime: Long? = null
    private var neutralSince: Long? = null
    private var neutralAnchor: CursorPoint? = null
    private var lastCursor: CursorPoint? = null
    private var pinchAnchor: CursorPoint? = null
    private var candidateAt = 0L
    private var candidateFrames = 0
    private var releaseSince: Long? = null
    private var cooldownUntil = 0L
    private val swipes = SwipeDetector(config)
    // Scene input is a short palm stroke at the real device's 6-8 inference FPS.
    // Mouse gestures retain their original fast-stroke thresholds and early pinch aim lock.
    private val sceneSwipe = SwipeDetector(config.copy(swipeWindowMs=600,swipeDistance=.10f,
        swipeMinSpeed=.18f,swipeAxisRatio=1.5f,swipeRestMs=180),scene=true)
    val swipeReady get()=(if(settings.sceneSwipes)sceneSwipe else swipes).ready
    val swipeStatus get()=if(paused)"已暂停 · 握拳恢复" else (if(settings.sceneSwipes)sceneSwipe else swipes).status
    private val fists = FistDetector(config)
    private val push = PushDetector()
    private val aim = PinchAimLock()
    private val interaction = PinchInteraction()
    private val flip = PalmFlipDetector()
    private var pinchPalm: CursorPoint? = null
    var lastSwipeVelocity = 0f; private set

    fun resetTracking() {
        pinchState = PinchState.UNARMED
        lastTime = null; neutralSince = null; neutralAnchor = null; lastCursor = null
        pinchAnchor = null; candidateFrames = 0; releaseSince = null
        swipes.reset();sceneSwipe.reset()
        fists.reset()
        push.reset()
        aim.reset(); pinchPalm=null; flip.reset(); interaction.reset()
    }
    fun setPaused(value: Boolean) { paused = value; resetTracking(); interaction.reset() }
    fun configure(value: ActionSettings) { settings = value; resetTracking(); interaction.reset() }

    fun update(hand: HandObservation?, now: Long): GestureDecision {
        val decision=evaluate(hand,now)
        return interaction.process(decision,hand,now,settings,paused)
    }

    private fun evaluate(hand: HandObservation?, now:Long):GestureDecision {
        val swipes=if(settings.sceneSwipes)sceneSwipe else this.swipes
        if (hand == null || !hand.valid || now-hand.time !in 0..config.maxGapMs ||
            (lastTime != null && (hand.time <= lastTime!! || hand.time-lastTime!! > config.maxGapMs))) {
            resetTracking(); return GestureDecision(message="等待整只手稳定入镜")
        }
        lastTime = hand.time
        if (settings.fistPause && fists.update(hand)) {
            paused=!paused; cooldownUntil=now+config.swipeCooldownMs
            resetTracking()
            return GestureDecision(pauseChanged=true,message=if(paused) "隔空控制已暂停" else "隔空控制已恢复")
        }
        if (hand.fist) {
            push.reset()
            pinchState=PinchState.UNARMED;pinchAnchor=null;neutralSince=null;neutralAnchor=null;swipes.reset()
            return GestureDecision(frozenCursor=lastCursor,message=if(paused) "握拳保持，准备恢复" else "握拳保持，准备暂停")
        }
        if (paused) return GestureDecision(message="已暂停")
        if(settings.palmFlip && now>=cooldownUntil && flip.update(hand)){cooldownUntil=now+900;resetTracking();return GestureDecision(ActionKind.PALM_FLIP,message="翻掌返回")}
        if(settings.pushExperimental && now>=cooldownUntil && hand.openPalm &&
            pinchState !in listOf(PinchState.APPROACH,PinchState.PINCH_START,PinchState.PINCH_HOLD,PinchState.RELEASE)) {
            if(push.update(hand)) {
                cooldownUntil=now+950;pinchState=PinchState.UNARMED;swipes.reset()
                return GestureDecision(ActionKind.CLICK,lastCursor ?: hand.cursor,message="前推点击（实验）")
            }
        } else if(!settings.pushExperimental || !hand.openPalm) push.reset()
        val swipePose=if(settings.sceneSwipes)(hand.swipePose && hand.pinchRatio>=config.pinchRelease) || (swipes.ready && hand.pinchRatio>config.pinchClose)
            else hand.openPalm && hand.pinchRatio>=config.pinchRelease
        if (swipePose && now>=cooldownUntil &&
            pinchState !in listOf(PinchState.APPROACH,PinchState.PINCH_START,PinchState.PINCH_HOLD,PinchState.RELEASE)) {
            swipes.update(hand)?.let { motion ->
                lastSwipeVelocity=motion.velocity
                val action=when(motion.direction) {
                    SwipeDirection.UP -> ActionKind.SWIPE_UP.takeIf {settings.swipeUp}
                    SwipeDirection.DOWN -> ActionKind.SWIPE_DOWN.takeIf {settings.swipeDown}
                    SwipeDirection.LEFT -> if(settings.sceneSwipes)ActionKind.SWIPE_LEFT else ActionKind.BACK.takeIf {settings.swipeLeftBack}
                    SwipeDirection.RIGHT -> if(settings.sceneSwipes)ActionKind.SWIPE_RIGHT else ActionKind.HOME.takeIf {settings.swipeRightHome}
                }
                if(action!=null) {
                    cooldownUntil=now+config.swipeCooldownMs
                    resetTracking()
                    return GestureDecision(action,message="挥动一次 · 等待重新稳住")
                }
            }
        } else swipes.reset()
        if (!settings.pinch) { lastCursor = hand.cursor; return GestureDecision(message="光标跟随") }
        if(settings.sceneSwipes && pinchState==PinchState.OPEN && hand.pinchRatio<=config.pinchClose && now>=cooldownUntil){
            // Scene clicks use screen center; early cursor locking only suppresses natural palm strokes here.
            pinchAnchor=hand.cursor;pinchPalm=hand.palm;candidateAt=hand.time;candidateFrames=1
            pinchState=PinchState.PINCH_START;swipes.reset()
        }else if(!settings.sceneSwipes && (pinchState==PinchState.UNARMED || pinchState==PinchState.OPEN)) {
            val early=aim.observe(hand,pinchState==PinchState.OPEN && now>=cooldownUntil)
            if(early!=null){pinchAnchor=early;pinchPalm=aim.palm;pinchState=PinchState.APPROACH;swipes.reset();push.reset()}
        }
        when (pinchState) {
            PinchState.UNARMED -> {
                if (hand.pinchRatio >= config.pinchRelease && !hand.fist && now >= cooldownUntil) {
                    val anchor = neutralAnchor
                    if (anchor == null || distance(anchor,hand.cursor) > .035f) {
                        neutralAnchor=hand.cursor; neutralSince=hand.time
                    } else if (hand.time-(neutralSince ?: hand.time) >= config.neutralMs) pinchState=PinchState.OPEN
                } else { neutralSince=null; neutralAnchor=null }
            }
            PinchState.OPEN -> Unit
            PinchState.APPROACH -> {
                if(hand.pinchRatio<=config.pinchClose){candidateAt=hand.time;candidateFrames=1;pinchState=PinchState.PINCH_START}
                else if(aim.cancelled(hand)){pinchState=PinchState.UNARMED;pinchAnchor=null;aim.release();neutralAnchor=null;neutralSince=null}
            }
            PinchState.PINCH_START -> {
                if (hand.pinchRatio >= config.pinchRelease) { pinchState=PinchState.RELEASE; releaseSince=hand.time }
                else if (hand.time-candidateAt > 350) pinchState=PinchState.PINCH_HOLD
                else if (pinchPalm==null || distance(hand.palm,checkNotNull(pinchPalm)) > .085f) {
                    // An unstable pinch is consumed too: a held/dragged pinch cannot become a late click.
                    pinchState=PinchState.PINCH_HOLD
                } else if (hand.pinchRatio <= config.pinchClose) {
                    candidateFrames++
                    if (candidateFrames >= 2 && hand.time-candidateAt >= config.pinchHoldMs) {
                        pinchState=PinchState.PINCH_HOLD; cooldownUntil=now+if(settings.doublePinch)120 else config.clickCooldownMs
                        return GestureDecision(ActionKind.CLICK,pinchAnchor,pinchAnchor,message="捏合一次")
                    }
                }
            }
            PinchState.PINCH_HOLD -> if (hand.pinchRatio >= config.pinchRelease) {
                pinchState=PinchState.RELEASE; releaseSince=hand.time
            }
            PinchState.RELEASE -> {
                if (hand.pinchRatio < config.pinchRelease) { pinchState=PinchState.PINCH_HOLD; releaseSince=null }
                else if (hand.time-(releaseSince ?: hand.time) >= config.pinchReleaseMs && now >= cooldownUntil) {
                    pinchState=PinchState.OPEN; pinchAnchor=null
                    aim.release();aim.observe(hand,false)
                }
            }
        }
        val frozen = pinchAnchor.takeIf { pinchState in listOf(PinchState.APPROACH,PinchState.PINCH_START,PinchState.PINCH_HOLD,PinchState.RELEASE) }
        if (frozen == null) lastCursor=hand.cursor
        return GestureDecision(frozenCursor=frozen,message=when(pinchState) {
            PinchState.UNARMED -> "张开拇指和食指，稳住片刻"
            PinchState.PINCH_START -> "确认捏合…"
            PinchState.APPROACH -> "已锁定瞄准位置 · 合拢即可点击"
            PinchState.PINCH_HOLD,PinchState.RELEASE -> "已捏合，请松开后再点"
            PinchState.OPEN -> "食指移动 · 捏合点击"
        })
    }
}
