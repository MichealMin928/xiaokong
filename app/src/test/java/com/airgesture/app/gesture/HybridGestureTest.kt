package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.mode.*
import com.airgesture.app.settings.ActionSettings
import org.junit.Assert.*
import org.junit.Test

class HybridGestureTest {
    private val controller=HybridGestureController().apply{configure(ActionSettings(drag=true))}
    private var time=1000L
    private fun h(x:Float=.5f,y:Float=.6f,direction:FingerDirection=FingerDirection.UP,point:Boolean=false,
        pinch:Float=.9f,cursor:CursorPoint=CursorPoint(.2f,.3f),home:Boolean=false,command:Boolean=false,
        tap:Float=0f,thumbY:Float=.5f,two:Boolean=false,five:Boolean=true)=HandObservation(time,cursor,CursorPoint(x,y),if(pinch==.9f).9f-tap*.5f else pinch,!point && !home && !command,false,
            swipePose=!point && !home && !command,pointerPose=point,pagePose=!point && !home && !command,
            homePose=home,fingerDirection=direction,thumbLocal=CursorPoint(tap,0f),thumbTip=CursorPoint(.5f,thumbY),thumbOnly=command,fourFingerPose=!point && !home && !command && !two,twoFingerPose=!point && !home && !command && two,fiveFingerPose=!point && !home && !command && !two && five)
    private fun frame(hand:HandObservation=h()):HybridDecision {time+=120;return controller.update(hand.copy(time=time),time+70)}
    private fun rest(direction:FingerDirection=FingerDirection.UP,two:Boolean=false){repeat(8){frame(h(direction=direction,two=two))}}
    private fun point(){repeat(8){frame(h(point=true))};assertTrue(controller.pointer)}
    @Test fun distinctFingerCountsAllowDirectionChangesWithoutReturningToOrigin() {
        rest(two=true)
        assertEquals(listOf(ActionKind.SWIPE_DOWN),listOf(.64f,.68f,.72f).mapNotNull{frame(h(y=it,two=true)).gesture.action})
        controller.resetTracking();controller.configure(ActionSettings(drag=true))
        assertEquals(listOf(ActionKind.SWIPE_UP),listOf(.72f,.68f,.64f,.60f).mapNotNull{frame(h(y=it)).gesture.action})
    }
    @Test fun repeatedUpStrokesNeedNoRestOrExactReturnPosition() {
        rest()
        val actions=listOf(.56f,.52f,.48f,.51f,.54f,.57f,.53f,.49f,.45f,.50f,.54f,.58f,.54f,.50f,.46f)
            .mapNotNull{frame(h(y=it)).gesture.action}
        assertEquals(listOf(ActionKind.SWIPE_UP,ActionKind.SWIPE_UP,ActionKind.SWIPE_UP),actions)
    }
    @Test fun repeatedTwoFingerDownStrokesNeedNoRest() {
        rest(two=true)
        val actions=listOf(.64f,.68f,.72f,.69f,.66f,.63f,.67f,.71f,.75f).mapNotNull{frame(h(y=it,two=true)).gesture.action}
        assertEquals(listOf(ActionKind.SWIPE_DOWN,ActionKind.SWIPE_DOWN),actions)
    }
    @Test fun fiveFingerLeftStrokesIgnoreRightwardReturnsWithoutWaiting() {
        rest()
        val actions=listOf(.46f,.42f,.38f,.41f,.44f,.47f,.43f,.39f,.35f)
            .mapNotNull{frame(h(x=it)).gesture.action}
        assertEquals(listOf(ActionKind.SWIPE_LEFT,ActionKind.SWIPE_LEFT),actions)
    }
    @Test fun twoFingerRightStrokesIgnoreLeftwardReturnsWithoutWaiting() {
        rest(two=true)
        val actions=listOf(.54f,.58f,.62f,.59f,.56f,.53f,.57f,.61f,.65f)
            .mapNotNull{frame(h(x=it,two=true)).gesture.action}
        assertEquals(listOf(ActionKind.SWIPE_RIGHT,ActionKind.SWIPE_RIGHT),actions)
    }
    @Test fun changingFingerCountAllowsHorizontalDirectionChangeAtCurrentPosition() {
        rest()
        assertEquals(listOf(ActionKind.SWIPE_LEFT),listOf(.46f,.42f,.38f).mapNotNull{frame(h(x=it)).gesture.action})
        assertEquals(listOf(ActionKind.SWIPE_RIGHT),listOf(.38f,.42f,.46f,.50f).mapNotNull{frame(h(x=it,two=true)).gesture.action})
    }
    @Test fun fourFingersWithoutOpenThumbCannotMoveHorizontally() {
        rest()
        for(x in listOf(.46f,.42f,.38f,.42f,.46f,.5f))assertNull(frame(h(x=x,five=false)).gesture.action)
        assertEquals(listOf(ActionKind.SWIPE_UP),listOf(.56f,.52f,.48f).mapNotNull{frame(h(y=it,five=false)).gesture.action})
    }
    @Test fun thumbOpeningDoesNotResetVerticalTravelOrReuseEarlierLeftTravel() {
        rest()
        assertNull(frame(h(y=.56f,five=false)).gesture.action)
        assertNull(frame(h(y=.52f)).gesture.action)
        assertEquals(ActionKind.SWIPE_UP,frame(h(y=.48f)).gesture.action)
        controller.resetTracking();rest()
        for(x in listOf(.46f,.42f,.38f))assertNull(frame(h(x=x,five=false)).gesture.action)
        assertNull(frame(h(x=.34f)).gesture.action)
        assertEquals(listOf(ActionKind.SWIPE_LEFT),listOf(.30f,.26f,.22f).mapNotNull{frame(h(x=it)).gesture.action})
    }
    @Test fun fourFingersDownNeverScrollDown() {
        rest()
        for(y in listOf(.6f,.64f,.68f,.72f,.76f))assertNull(frame(h(y=y,direction=FingerDirection.DOWN).copy(fourFingerPose=true,twoFingerPose=false)).gesture.action)
    }
    @Test fun twoUprightFingersMovingUpNeverScrollUp() {
        rest(two=true)
        for(y in listOf(.56f,.52f,.48f,.44f))assertNull(frame(h(y=y,two=true)).gesture.action)
        assertEquals(listOf(ActionKind.SWIPE_DOWN),listOf(.48f,.52f,.56f).mapNotNull{frame(h(y=it,two=true)).gesture.action})
    }
    @Test fun twoDownwardPointingFingersAreNotTheDownCommand() {
        rest(FingerDirection.DOWN,two=true)
        for(y in listOf(.64f,.68f,.72f,.76f))assertNull(frame(h(y=y,two=true,direction=FingerDirection.DOWN)).gesture.action)
    }
    @Test fun fourUprightFingersReturningDownNeverScrollDown() {
        rest()
        for(y in listOf(.64f,.68f,.72f,.76f))assertNull(frame(h(y=y)).gesture.action)
    }
    @Test fun diagonalMotionDoesNotScrollAndDoesNotLockFutureActions() {
        rest()
        for(v in listOf(.04f,.08f,.12f))assertNull(frame(h(x=.5f+v,y=.6f-v)).gesture.action)
        assertEquals(listOf(ActionKind.SWIPE_UP),listOf(.44f,.40f,.36f).mapNotNull{frame(h(x=.62f,y=it)).gesture.action})
    }
    @Test fun uprightPalmMovesPagesAndSidewaysPalmReturnsOnEitherHand() {
        for(d in listOf(FingerDirection.UP,FingerDirection.LEFT,FingerDirection.RIGHT))for(sign in listOf(-1,1)){
            val c=FingerPagingController();var t=1000L
            fun tick(x:Float=.5f):ActionKind?{t+=120;return c.update(h(x=x,direction=d,two=d==FingerDirection.UP && sign>0).copy(time=t),t+70).action}
            repeat(8){assertNull(tick())}
            val expected=if(d!=FingerDirection.UP)ActionKind.BACK else if(sign<0)ActionKind.SWIPE_LEFT else ActionKind.SWIPE_RIGHT
            assertEquals(listOf(expected),listOf(.04f,.08f,.12f).mapNotNull{tick(.5f+it*sign)})
            repeat(10){assertNull(tick(.5f+.12f*sign))}
        }
    }
    @Test fun twoNoncontactThumbTapsClickTheAimBeforeFingerDrift() {
        point()
        val frames=listOf(.15f,.32f,.16f,.32f).map{frame(h(point=true,tap=it,cursor=CursorPoint(.2f,.7f)))}
        assertEquals(listOf(ActionKind.CLICK),frames.mapNotNull{it.gesture.action})
        assertTrue(frames.all{it.showPointer})
        assertEquals(CursorPoint(.2f,.3f),frames.last().gesture.point)
        assertEquals(ActionKind.CLICK to CursorPoint(.2f,.3f),frames.last().mapped(AppProfile.presets.first()))
    }
    @Test fun oneTapOrHeldThumbDoesNotClick() {
        point()
        for(v in listOf(.15f,.3f))assertNull(frame(h(point=true,tap=v)).gesture.action)
        repeat(25){assertNull(frame(h(point=true,tap=.3f)).gesture.action)}
    }
    @Test fun wholeHandTravelWhilePointingCannotClickOrSwipe() {
        point()
        for(x in listOf(.54f,.58f,.62f,.58f,.54f,.5f,.54f,.58f,.62f))assertNull(frame(h(point=true,x=x)).gesture.action)
    }
    @Test fun singleShortContactDoesNotClickButTwoContactsCanConfirm() {
        point()
        val actions=listOf(.3f to .2f,0f to .9f,.3f to .2f).mapNotNull{(tap,pinch)->frame(h(point=true,tap=tap,pinch=pinch)).gesture.action}
        assertEquals(listOf(ActionKind.CLICK),actions)
    }
    @Test fun longPinchDragsWithoutPreClickAndLostHandReleases() {
        point()
        val held=List(8){frame(h(point=true,tap=.4f,pinch=.2f))}.mapNotNull{it.gesture.action}
        assertEquals(ActionKind.DRAG_START,held.first());assertFalse(held.contains(ActionKind.CLICK))
        assertEquals(ActionKind.DRAG_MOVE,frame(h(point=true,tap=.4f,pinch=.2f,x=.54f)).gesture.action)
        assertEquals(ActionKind.DRAG_END,controller.update(null,time+120).gesture.action)
    }
    @Test fun circleDisappearsOnPalmFistAndMissingHand() {
        point();assertTrue(frame(h(point=true)).showPointer)
        assertFalse(frame().showPointer)
        rest();assertFalse(controller.pointer)
        assertFalse(controller.update(null,time+100).showPointer)
        point();assertFalse(frame(h(point=true).copy(fist=true,pointerPose=false)).showPointer)
    }
    @Test fun homeSixFiresOnceAcrossWindowEventsAndNeedsPoseRelease() {
        assertEquals(listOf(ActionKind.HOME),List(12){frame(h(home=true)).gesture.action}.filterNotNull())
        controller.resetTracking();controller.configure(ActionSettings(drag=true))
        repeat(10){assertNull(frame(h(home=true)).gesture.action)}
        rest();assertEquals(listOf(ActionKind.HOME),List(8){frame(h(home=true)).gesture.action}.filterNotNull())
    }
    @Test fun socialCommandsRequireTwoActualMovementsInsteadOfTwoStaticPoses() {
        for(sign in listOf(-1,1)){
            rest();repeat(4){assertNull(frame(h(command=true)).gesture.action)}
            val actions=listOf(.035f,.075f,.035f,0f,.035f,.075f).mapNotNull{frame(h(command=true,thumbY=.5f+it*sign)).gesture.action}
            assertEquals(listOf(if(sign<0)ActionKind.LIKE else ActionKind.DISLIKE),actions)
            repeat(8){assertNull(frame(h(command=true,thumbY=.5f+.075f*sign)).gesture.action)}
        }
    }
    @Test fun holdingOrReposingThumbNeverLikesOrDislikes() {
        repeat(8){assertNull(frame(h(command=true)).gesture.action)}
        rest();repeat(8){assertNull(frame(h(command=true)).gesture.action)}
    }
    @Test fun thumbSequenceDoesNotBecomeFistPauseOrPointerClick() {
        rest();repeat(4){frame(h(command=true))}
        for(y in listOf(.47f,.425f))frame(h(command=true,thumbY=y))
        repeat(5){assertFalse(frame(h().copy(fist=true,openPalm=false,pagePose=false,swipePose=false)).gesture.pauseChanged)}
        assertFalse(controller.paused)
    }
    @Test fun pausedStaleOrMissingInputCannotFinishPendingCommands() {
        point();frame(h(point=true,tap=.3f));frame(h(point=true));controller.update(null,time+120)
        assertNull(frame(h(point=true,tap=.3f)).gesture.action);assertNull(frame(h(point=true)).gesture.action)
        controller.setPaused(true);repeat(10){assertNull(frame(h(home=true)).gesture.action)}
        controller.setPaused(false);repeat(5){frame(h(home=true))}
        time+=500;assertNull(controller.update(h(home=true).copy(time=time-300),time).gesture.action)
        assertNull(frame(h(home=true)).gesture.action)
    }
    @Test fun horizontalActionsReachLauncherAndAppsWithoutProfileRemappingToBack() {
        assertTrue(ModeRouter.allowed(AssistantMode.GLOBAL,"com.android.launcher",emptyList(),true))
        assertEquals(AssistantMode.GLOBAL,ModeRouter.resolve(AssistantMode.GLOBAL,AppProfile.presets.last(),true))
        for(profile in listOf(null,AppProfile.presets.first(),AppProfile.presets.last())){
            assertEquals(ActionKind.SWIPE_LEFT to null,HybridDecision(GestureDecision(ActionKind.SWIPE_LEFT)).mapped(profile))
            assertEquals(ActionKind.SWIPE_RIGHT to null,HybridDecision(GestureDecision(ActionKind.SWIPE_RIGHT)).mapped(profile))
        }
    }

    @Test fun steadyPoseNoiseDoesNotTriggerActionsInAnyChannel() {
        val random=kotlin.random.Random(23)
        fun noise()=random.nextFloat()*.012f-.006f
        rest()
        repeat(180){assertNull(frame(h(x=.5f+noise(),y=.6f+noise())).gesture.action)}
        point()
        repeat(180){assertNull(frame(h(point=true,tap=noise()*5,cursor=CursorPoint(.2f+noise(),.3f+noise()))).gesture.action)}
        repeat(180){assertNull(frame(h(command=true,thumbY=.5f+noise())).gesture.action)}
    }
    @Test fun changingToTwoFingersCanStartDownwardMotionImmediately() {
        rest();frame(h(y=.56f))
        assertEquals(listOf(ActionKind.SWIPE_DOWN),listOf(.56f,.60f,.64f,.68f).mapNotNull{frame(h(y=it,two=true)).gesture.action})
    }
    @Test fun indexMovementAloneCannotConfirmWithoutThumbMovement() {
        point()
        for(gap in listOf(.8f,.65f,.82f,.66f,.82f))assertNull(frame(h(point=true,pinch=gap,tap=0f,cursor=CursorPoint(.2f,.65f))).gesture.action)
    }
    @Test fun smallUnconfirmedThumbChangesNeverFreezeOrdinaryPointerMotion() {
        point()
        for(tap in listOf(.09f,.10f,.02f,.09f,.10f,.02f)){
            val result=frame(h(point=true,tap=tap,cursor=CursorPoint(.22f,.3f)))
            assertNull(result.gesture.frozenCursor);assertNull(result.gesture.action);assertTrue(result.showPointer)
        }
    }
    @Test fun movingTheWholeHandCancelsPendingClickAndUnlocksPointer(){
        point();assertEquals(1,frame(h(point=true,tap=.3f)).gesture.confirmation)
        val moved=frame(h(point=true,tap=.3f,x=.53f))
        assertNull(moved.gesture.action);assertNull(moved.gesture.frozenCursor)
        assertEquals(0,moved.gesture.confirmation)
    }
    @Test fun aBriefLostFrameHoldsOnlyTheDisplayAndCannotCompleteTheClick(){
        point();frame(h(point=true,tap=.3f));val lost=controller.update(null,time+140)
        assertTrue(lost.showPointer);assertNull(lost.gesture.action)
        assertFalse(controller.update(null,time+400).showPointer)
        assertNull(frame(h(point=true,tap=.3f)).gesture.action)
    }
    @Test fun marginalIndexExtensionMovesDisplayBrieflyWithoutClickingOrEnteringMouse(){
        val marginal=h(point=true,cursor=CursorPoint(.45f,.5f)).copy(pointerPose=false,pointerContinuationPose=true)
        repeat(8){assertFalse(frame(marginal).showPointer)}
        point()
        val moving=frame(marginal)
        assertTrue(moving.showPointer);assertNull(moving.gesture.frozenCursor);assertNull(moving.gesture.action)
        val expired=frame(marginal)
        assertFalse(expired.showPointer)
        assertFalse(frame(marginal.copy(pagePose=true,twoFingerPose=true)).showPointer)
    }
    @Test fun bentIndexDuringSecondContactStaysInMouseChannel(){
        point();frame(h(point=true,tap=.3f));frame(h(point=true,tap=0f))
        val clicked=frame(h(point=true,tap=.4f,pinch=.2f).copy(pointerPose=false,thumbOnly=true,fist=true))
        assertEquals(ActionKind.CLICK,clicked.gesture.action);assertEquals(2,clicked.gesture.confirmation)
        assertTrue(clicked.pointer);assertEquals(CursorPoint(.2f,.3f),clicked.gesture.point)
    }
}
