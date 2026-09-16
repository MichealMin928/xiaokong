package com.airgesture.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.camera.CameraController
import com.airgesture.app.settings.GestureConfig
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.camera.view.TransformExperimental::class)
class CapturePowerTest {
    @Test fun oppoCaptureRateFallsWhileIdleAndRestoresWithoutRebinding(){
        val i=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var camera:CameraController?=null
            var fps=0.0
            var frames=0
            var error:String?=null
            fun await(label:String,predicate:()->Boolean){
                val until=SystemClock.uptimeMillis()+9000
                while(SystemClock.uptimeMillis()<until){var ok=false;i.runOnMainSync{assertNull(error);ok=predicate()};if(ok)return;SystemClock.sleep(100)}
                fail("$label; camera FPS=$fps frames=$frames")
            }
            try{
                scenario.onActivity{a->camera=CameraController(a,a,null,GestureConfig(maxInferenceFps=20),{error=it},{_,metrics->fps=metrics.cameraFps;frames++}).also{it.setLowPowerCapture(true);it.start()}}
                await("OPPO idle hardware rate must be at most 15 FPS"){frames>=25 && fps in 8.0..17.0}
                var low=0.0;i.runOnMainSync{low=fps;camera?.setLowPowerCapture(false)}
                await("Active capture must recover above idle without a bind cycle"){fps>=21.0}
                android.util.Log.i("AirGesture","capture_power=PASS idle_fps=$low active_fps=$fps rebind_required=0")
            }finally{i.runOnMainSync{camera?.close()}}
        }
    }
}
