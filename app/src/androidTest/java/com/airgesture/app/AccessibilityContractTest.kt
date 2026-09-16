package com.airgesture.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.accessibility.*
import com.airgesture.app.cursor.ControlRegion
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityContractTest {
    @Test fun systemBindingRequiresSignaturePermissionAndWindowEventsOnly() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val component=ComponentName(context,AirAccessibilityService::class.java)
        @Suppress("DEPRECATION")
        val info=context.packageManager.getServiceInfo(component,PackageManager.GET_META_DATA)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE,info.permission)
        assertTrue(info.exported)
        val xml=checkNotNull(info.loadXmlMetaData(context.packageManager,AccessibilityService.SERVICE_META_DATA))
        xml.use {
            while(it.eventType!=org.xmlpull.v1.XmlPullParser.START_TAG)it.next()
            val ns="http://schemas.android.com/apk/res/android"
            assertEquals("true",it.getAttributeValue(ns,"canPerformGestures"))
            assertEquals("true",it.getAttributeValue(ns,"canRetrieveWindowContent"))
            assertEquals(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,it.getAttributeIntValue(ns,"accessibilityEventTypes",0))
        }
    }
    @Test fun inactiveSessionCannotSendCoordinatesEvenWithServiceInstalled() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.stopService(Intent(context,com.airgesture.app.service.GestureForegroundService::class.java))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            var callback=false
            val action=SystemAction(ActionKind.CLICK,CursorPoint(10f,10f),ScreenGeometry(1080,2400,0),SystemClock.uptimeMillis())
            assertFalse(AccessibilityBridge.dispatch(action){callback=true})
            assertFalse(callback)
        }
    }
    @Test fun calibrationGestureAndPerformancePreferencesRoundTripIndependently() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=GestureSettings(context)
        val cursor=store.load();val actions=store.loadActions();val performance=store.loadPerformance()
        try {
            val portrait=ControlRegion(.3f,.7f,.2f,.6f)
            val landscape=ControlRegion(.2f,.6f,.25f,.55f)
            store.saveCalibration(portrait,false);store.saveCalibration(landscape,true)
            store.save(cursor.copy(acceleration=true))
            store.saveActions(actions.copy(swipeLeftBack=true,swipeUp=false))
            store.savePerformance(PerformanceSettings(PerformanceMode.ECONOMY,false))
            assertEquals(portrait,store.load().portraitRegion);assertEquals(landscape,store.load().landscapeRegion)
            assertTrue(store.load().acceleration);assertTrue(store.loadActions().swipeLeftBack);assertFalse(store.loadActions().swipeUp)
            assertEquals(12,store.loadPerformance().config().maxInferenceFps)
        } finally {
            store.save(cursor);store.saveActions(actions);store.savePerformance(performance)
            store.saveCalibration(cursor.portraitRegion,false);store.saveCalibration(cursor.landscapeRegion,true)
        }
    }
}
