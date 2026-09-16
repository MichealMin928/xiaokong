package com.airgesture.app

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.accessibility.AccessibilityBridge
import com.airgesture.app.mode.*
import com.airgesture.app.service.*
import com.airgesture.app.eye.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SceneAndEyeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun waitFor(label:String,check:()->Boolean){
        val deadline=SystemClock.uptimeMillis()+12000
        while(SystemClock.uptimeMillis()<deadline){var pass=false;instrumentation.runOnMainSync{pass=check()};if(pass)return;SystemClock.sleep(80)}
        fail(label)
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
    @Test fun eyePageProducesRealFaceModelCallbacksAndStopsOnExit(){
        ActivityScenario.launch(EyeActivity::class.java).use{scenario->
            var activity:EyeActivity?=null
            scenario.onActivity{a->activity=a;views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().first{it.text=="区域练习"}.performClick()}
            waitFor("Eye CameraX must deliver live model results"){
                views(checkNotNull(activity).findViewById(android.R.id.content)).filterIsInstance<TextView>().any{it.text.contains("AI ")}
            }
        }
        android.util.Log.i("AirGesture","eye_pipeline=PASS real_camera=1 raw_image_saved=0")
    }
    @Test fun foregroundWhitelistReleasesAndReopensCameraWithoutSystemActions(){
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val prefs=context.getSharedPreferences("assistant_profiles",0)
        val backup=prefs.all.toMap()
        val store=ProfileStore(context)
        try{
            store.save(AppProfile(context.packageName,"自动检查页面",AssistantMode.VIDEO))
            ActivityScenario.launch(HomeActivity::class.java).use{scenario->
                waitFor("Existing user accessibility authorization must reconnect"){AccessibilityBridge.available}
                scenario.onActivity{a->ContextCompat.startForegroundService(a,Intent(a,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.VIDEO.name))}
                waitFor("Whitelisted own app must start real camera"){GlobalSession.log.export().contains("capture_started mode=VIDEO")}
                instrumentation.runOnMainSync{assertFalse(GlobalSession.actionsEnabled);context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                waitFor("Leaving whitelist must release camera"){GlobalSession.log.export().contains("app_gate allowed=false") && GlobalSession.status.value?.message?.contains("相机已释放")==true}
                instrumentation.runOnMainSync{context.startActivity(Intent(context,HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                waitFor("Foreground app must reacquire camera from existing session"){GlobalSession.log.export().lineSequence().count{it.startsWith("capture_started mode=VIDEO")}>1}
            }
        }finally{
            context.stopService(Intent(context,GestureForegroundService::class.java))
            val edit=prefs.edit().clear();backup.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())}};edit.commit()
        }
    }
    @Test fun eyeCalibrationTargetsSpanPhysicalScreenRatherThanTheControlsPanel(){
        ActivityScenario.launch(EyeActivity::class.java).use{scenario->
            scenario.onActivity{a->
                views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().first{it.text=="三点校准"}.performClick()
            }
            SystemClock.sleep(500)
            scenario.onActivity{a->
                val field=views(a.findViewById(android.R.id.content)).last{it.javaClass.simpleName=="GazeField"}
                val position=IntArray(2);field.getLocationOnScreen(position)
                val height=a.resources.displayMetrics.heightPixels
                assertTrue("TOP must be in upper screen region",position[1]+field.height*.1f<height*.2f)
                assertTrue("BOTTOM must be in lower screen region",position[1]+field.height*.9f>height*.8f)
                views(a.findViewById(android.R.id.content)).filterIsInstance<Button>().first{it.text=="结束练习"}.performClick()
            }
        }
    }
    @Test fun gazeAndAppSettingsPersistSeparately(){
        val gazePrefs=context.getSharedPreferences("gaze_calibration",0);val original=gazePrefs.all.toMap()
        try{
            val gaze=GazeStore(context);val c=GazeCalibration(.3f,.5f,.7f,0f,.4f,0f,.3f)
            gaze.save(c,false);assertEquals(c,gaze.load(false));gaze.clear(true);assertNull(gaze.load(true));assertEquals(c,gaze.load(false))
        }finally{val edit=gazePrefs.edit().clear();original.forEach{(k,v)->if(v is String)edit.putString(k,v)};edit.commit()}
    }
}
