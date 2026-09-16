package com.airgesture.app.gesture

import com.airgesture.app.accessibility.ActionKind
import com.airgesture.app.cursor.CursorController
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.mode.*
import com.airgesture.app.settings.ActionSettings
import com.airgesture.app.vision.HandTrackingGate
import org.junit.Assert.*
import org.junit.Test

class SceneLandmarkPipelineTest {
    private fun hand(dy:Float=0f)=MutableList(21){CursorPoint(.5f,.6f+dy)}.apply {
        this[0]=CursorPoint(.5f,.72f+dy);this[4]=CursorPoint(.34f,.55f+dy)
        this[5]=CursorPoint(.44f,.60f+dy);this[6]=CursorPoint(.44f,.50f+dy);this[8]=CursorPoint(.44f,.37f+dy)
        this[9]=CursorPoint(.50f,.59f+dy);this[10]=CursorPoint(.50f,.47f+dy);this[12]=CursorPoint(.50f,.34f+dy)
        this[13]=CursorPoint(.56f,.60f+dy);this[14]=CursorPoint(.56f,.50f+dy);this[16]=CursorPoint(.56f,.38f+dy)
        this[17]=CursorPoint(.62f,.62f+dy);this[18]=CursorPoint(.62f,.55f+dy);this[20]=CursorPoint(.62f,.54f+dy)
    }
    @Test fun relaxedLittleFingerWorksThroughGateCursorGestureAndAppMapping() {
        for((width,height) in listOf(480 to 640,640 to 480)){
            val gate=HandTrackingGate();val cursor=CursorController()
            val machine=GestureStateMachine(ActionSettings(sceneSwipes=true,doublePinch=true,palmFlip=true))
            var t=1000L;val actions=mutableListOf<ActionKind>()
            val profile=AppProfile.presets.first{it.packageName=="com.xingin.xhs"}
            for(dy in List(8){0f}+listOf(-.03f,-.06f,-.09f,-.12f,-.15f)){
                t+=140;val p=hand(dy);val tip=gate.update(p,t,t+90,width,height)
                val mapped=cursor.update(tip,t,t+90)
                val h=mapped?.let{HandObservation.from(p,width,height,t,it)}
                if(h!=null){assertFalse(h.openPalm);assertTrue(h.swipePose)}
                machine.update(h,t+90).action?.let{ModeRouter.map(it,profile)?.kind?.let(actions::add)}
            }
            assertEquals(listOf(ActionKind.SWIPE_UP),actions)
        }
    }
    @Test fun pointingAndClosedFingersAreNotSwipePoses() {
        val points=hand().toMutableList()
        for((tip,pip) in listOf(12 to 10,16 to 14,20 to 18))points[tip]=points[pip]
        val h=checkNotNull(HandObservation.from(points,480,640,1000,CursorPoint(.5f,.5f)))
        assertFalse(h.swipePose)
    }

    @Test fun fourFingerDirectionsWorkThroughActualLandmarkGateAndHybridRouting() {
        for(mirror in listOf(false,true))for(expected in listOf(ActionKind.SWIPE_UP,ActionKind.SWIPE_DOWN,ActionKind.SWIPE_LEFT,ActionKind.SWIPE_RIGHT)){
            val gate=HandTrackingGate();val cursor=CursorController()
            val hybrid=HybridGestureController().apply{configure(ActionSettings())}
            var t=1000L;val actions=mutableListOf<ActionKind>()
            for(v in List(10){0f}+listOf(.03f,.06f,.09f,.12f,.15f)){
                t+=140
                val p=hand().apply{this[20]=CursorPoint(.62f,.40f);if(expected in listOf(ActionKind.SWIPE_DOWN,ActionKind.SWIPE_RIGHT)){this[16]=this[14];this[20]=this[18]}}.map{
                    val upright=it
                    val dx=when(expected){ActionKind.SWIPE_LEFT->v;ActionKind.SWIPE_RIGHT->-v;else->0f}
                    val dy=when(expected){ActionKind.SWIPE_UP->-v;ActionKind.SWIPE_DOWN->v;else->0f}
                    CursorPoint((if(mirror)1-upright.x else upright.x)+dx,upright.y+dy)
                }
                val tip=gate.update(p,t,t+80,480,640);val mapped=cursor.update(tip,t,t+80)
                val observation=mapped?.let{HandObservation.from(p,480,640,t,it)}
                hybrid.update(observation,t+80).mapped(null)?.first?.let(actions::add)
            }
            assertEquals("$expected mirror=$mirror",listOf(expected),actions)
        }
    }
}
