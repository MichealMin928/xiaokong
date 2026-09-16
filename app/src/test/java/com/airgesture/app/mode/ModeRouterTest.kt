package com.airgesture.app.mode

import com.airgesture.app.accessibility.ActionKind
import org.junit.Assert.*
import org.junit.Test

class ModeRouterTest {
    @Test fun unlistedAppsSleepAndSceneModesDoNotLeakAcrossApps(){
        val p=AppProfile.presets
        assertFalse(ModeRouter.allowed(AssistantMode.VIDEO,null,p,false))
        assertFalse(ModeRouter.allowed(AssistantMode.VIDEO,"com.tencent.mm",p,false))
        assertTrue(ModeRouter.allowed(AssistantMode.VIDEO,"com.xingin.xhs",p,false))
        assertFalse(ModeRouter.allowed(AssistantMode.READING,"com.xingin.xhs",p,false))
        assertTrue(ModeRouter.allowed(AssistantMode.MOUSE,null,p,false))
        assertFalse(ModeRouter.allowed(AssistantMode.MOUSE,null,p,true))
        assertFalse(ModeRouter.allowed(AssistantMode.VIDEO,"com.xingin.xhs",p.map{it.copy(enabled=false)},false))
    }
    @Test fun appSpecificMappingHasNoUnconfiguredFallbackAction(){
        assertEquals(MappedAction.PLAY_PAUSE,ModeRouter.map(ActionKind.CLICK,AppProfile.presets.first()))
        assertEquals(MappedAction.LIKE,ModeRouter.map(ActionKind.DOUBLE_CLICK,AppProfile.presets.first()))
        assertEquals(MappedAction.SEEK_FORWARD,ModeRouter.map(ActionKind.SWIPE_LEFT,AppProfile.presets[2]))
        assertEquals(MappedAction.NONE,ModeRouter.map(ActionKind.SWIPE_LEFT,AppProfile.presets.first()))
        assertEquals(MappedAction.NONE,ModeRouter.map(ActionKind.CLICK,null))
    }
    @Test fun automaticModeFollowsEnabledProfileWhileManualMouseStaysMouse(){
        val reading=AppProfile.presets.first{it.mode==AssistantMode.READING}
        assertEquals(AssistantMode.READING,ModeRouter.resolve(AssistantMode.VIDEO,reading,true))
        assertEquals(AssistantMode.VIDEO,ModeRouter.resolve(AssistantMode.VIDEO,reading,false))
        assertEquals(AssistantMode.MOUSE,ModeRouter.resolve(AssistantMode.MOUSE,reading,true))
    }
    @Test fun colorOsCameraAnnouncementDoesNotLookLikeLeavingTheApp(){
        assertFalse(WindowContextPolicy.changesForeground("com.android.systemui","android.view.View",-1,false))
        assertTrue(WindowContextPolicy.changesForeground("com.android.systemui","android.view.View",12,true))
        assertTrue(WindowContextPolicy.changesForeground("com.android.launcher","Launcher",13,true))
        assertTrue(WindowContextPolicy.changesForeground("com.xingin.xhs","Activity",14,true))
    }
    @Test fun elevatedBatteryTemperatureCapsEvenIfRomReportsNormalThermalState(){
        val p=PowerGovernor();p.update(1000,true,true)
        assertEquals(12,p.fps(20,0,42f));assertEquals(8,p.fps(20,0,44f));assertEquals(20,p.fps(20,0,38f))
    }
    @Test fun powerWakesOnPresenceAndSleepsOutsideWhitelist(){
        val p=PowerGovernor();assertEquals(WorkState.WATCH,p.update(1000,true,false));assertEquals(3,p.fps(20))
        assertEquals(WorkState.ACTIVE,p.update(1300,true,true));assertEquals(20,p.fps(20))
        assertEquals(WorkState.ACTIVE,p.update(2000,true,false));assertEquals(WorkState.WATCH,p.update(3400,true,false))
        assertEquals(WorkState.ACTIVE,p.update(3500,true,true,true)) // keep enough frames to recognize resume fist
        assertEquals(8,p.fps(20,3));assertEquals(WorkState.SLEEP,p.update(4000,false,true))
        assertEquals(WorkState.WATCH,p.update(4100,true,false))
    }
}
