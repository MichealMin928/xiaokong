package com.airgesture.app

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.mode.*
import com.airgesture.app.service.*
import org.junit.Assert.*
import org.junit.Test

class SessionRestartTest {
    @Test fun startSwitchesExistingModeAndResumesPausedSession(){
        val i=InstrumentationRegistry.getInstrumentation();val context=i.targetContext
        val store=ProfileStore(context);val original=store.mouseWhitelist
        fun waitFor(label:String,condition:()->Boolean){
            val deadline=SystemClock.uptimeMillis()+9000
            while(SystemClock.uptimeMillis()<deadline){var ok=false;i.runOnMainSync{ok=condition()};if(ok)return;SystemClock.sleep(80)}
            fail(label)
        }
        fun frames()=GlobalSession.log.export().lineSequence().count{it.startsWith("global_frame ")}
        try{
            store.mouseWhitelist=false
            ActivityScenario.launch(HomeActivity::class.java).use{scenario->
                fun start(mode:AssistantMode){scenario.onActivity{a->ContextCompat.startForegroundService(a,Intent(a,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START).putExtra(GestureForegroundService.EXTRA_MODE,mode.name))}}
                start(AssistantMode.MOUSE)
                waitFor("Mouse camera must run"){frames()>0}
                start(AssistantMode.VIDEO)
                waitFor("Start must switch the running mouse session into video whitelist sleep"){GlobalSession.status.value?.message?.contains("相机已释放")==true}
                val previous=frames();start(AssistantMode.MOUSE)
                waitFor("Switching back must produce a new camera result"){frames()>previous}
                scenario.onActivity{it.startService(Intent(it,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_PAUSE))}
                waitFor("Pause must take effect"){GlobalSession.status.value?.paused==true}
                start(AssistantMode.MOUSE)
                waitFor("Explicit start must resume paused session"){GlobalSession.running && GlobalSession.status.value?.paused==false}
                i.runOnMainSync{assertFalse("No system actions during test",GlobalSession.actionsEnabled)}
            }
        }finally{context.stopService(Intent(context,GestureForegroundService::class.java));store.mouseWhitelist=original}
    }
}
