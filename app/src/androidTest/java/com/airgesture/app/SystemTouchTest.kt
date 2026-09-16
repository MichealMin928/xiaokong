package com.airgesture.app

import android.app.UiAutomation
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.accessibility.*
import com.airgesture.app.cursor.CursorPoint
import com.airgesture.app.service.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SystemTouchTest {
    @Test fun actualAccessibilityClickDoubleClickAndContinuedDragStayOnIsolatedTestSurface(){
        val i=InstrumentationRegistry.getInstrumentation()
        i.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        i.targetContext.stopService(android.content.Intent(i.targetContext,GestureForegroundService::class.java))
        ActivityScenario.launch(ActionTestActivity::class.java).use{scenario->
            val deadline=SystemClock.uptimeMillis()+12000
            var connected=false
            while(!connected && SystemClock.uptimeMillis()<deadline){i.runOnMainSync{connected=AccessibilityBridge.available};SystemClock.sleep(80)}
            assertTrue("Existing enabled accessibility service must bind",connected)
            SystemClock.sleep(300)
            lateinit var screen:ScreenGeometry
            lateinit var target:CursorPoint
            lateinit var start:CursorPoint
            lateinit var end:CursorPoint
            scenario.onActivity{a->
                val bounds=a.windowManager.currentWindowMetrics.bounds
                screen=ScreenGeometry(bounds.width(),bounds.height(),a.display?.rotation ?: 0)
                val location=IntArray(2);a.target.getLocationOnScreen(location)
                target=CursorPoint(location[0]+a.target.width*.5f,location[1]+a.target.height*.5f)
                a.dragSurface.getLocationOnScreen(location)
                start=CursorPoint(location[0]+a.dragSurface.width*.3f,location[1]+a.dragSurface.height*.4f)
                end=CursorPoint(location[0]+a.dragSurface.width*.7f,location[1]+a.dragSurface.height*.6f)
                GlobalSession.publish(GlobalStatus(true,false,"仅测试当前隔离页面"));GlobalSession.actionsEnabled=true
            }
            fun dispatch(kind:ActionKind,point:CursorPoint){
                val result=ArrayBlockingQueue<ActionResult>(4)
                i.runOnMainSync{assertTrue("Dispatch $kind",AccessibilityBridge.dispatch(SystemAction(kind,point,screen,SystemClock.uptimeMillis())){result.offer(it)})}
                assertEquals(ActionResult.COMPLETED,result.poll(3,TimeUnit.SECONDS));SystemClock.sleep(50)
            }
            try{
                dispatch(ActionKind.CLICK,target);scenario.onActivity{assertEquals(1,it.clicks)}
                dispatch(ActionKind.DOUBLE_CLICK,target);scenario.onActivity{assertEquals(3,it.clicks)}
                dispatch(ActionKind.DRAG_START,start);dispatch(ActionKind.DRAG_MOVE,end)
                i.runOnMainSync{AccessibilityBridge.releaseDrag()};SystemClock.sleep(150)
                scenario.onActivity{a->
                    assertEquals(1,a.touchEvents.count{it==MotionEvent.ACTION_DOWN})
                    assertEquals(1,a.touchEvents.count{it==MotionEvent.ACTION_UP})
                    assertFalse(a.touchEvents.contains(MotionEvent.ACTION_CANCEL))
                    assertTrue(a.touchEvents.contains(MotionEvent.ACTION_MOVE));assertEquals(3,a.clicks)
                }
                android.util.Log.i("AirGesture","system_touch=PASS click=1 double_click=2 drag_down=1 drag_up=1 surface=own_debug_activity real_hand=0")
            }finally{i.runOnMainSync{AccessibilityBridge.releaseDrag();GlobalSession.actionsEnabled=false;GlobalSession.publish(GlobalStatus())}}
        }
    }
}
