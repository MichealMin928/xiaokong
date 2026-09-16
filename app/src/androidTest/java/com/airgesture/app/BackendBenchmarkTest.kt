package com.airgesture.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Synthetic blank-frame comparison only: no camera, accuracy or thermal acceptance claim. */
@RunWith(AndroidJUnit4::class)
class BackendBenchmarkTest {
    @Test fun compareCpuAndGpuInitializationAndLiveCallbacks() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        for(delegate in listOf(Delegate.CPU,Delegate.GPU)) {
            val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
            val image=BitmapImageBuilder(bitmap).build()
            val callbacks=ArrayBlockingQueue<Long>(2)
            var task:HandLandmarker?=null
            try {
                val begin=SystemClock.uptimeMillis()
                task=HandLandmarker.createFromOptions(context,HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(delegate).build())
                    .setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
                    .setResultListener { result,_->callbacks.offer(if(result.landmarks().isEmpty()) result.timestampMs() else -2L) }
                    .setErrorListener {callbacks.offer(-1L)}.build())
                val init=SystemClock.uptimeMillis()-begin
                val times=mutableListOf<Long>()
                repeat(30) { index ->
                    val t=SystemClock.uptimeMillis()
                    val timestamp=begin+index*200L
                    task.detectAsync(image,timestamp)
                    assertEquals(timestamp,callbacks.poll(5,TimeUnit.SECONDS))
                    if(index>=5) times.add(SystemClock.uptimeMillis()-t)
                }
                Log.i("AirGesture","backend_benchmark=$delegate supported=true input=synthetic_blank init_ms=$init median_ms=${times.sorted()[times.size/2]} min_ms=${times.min()} max_ms=${times.max()} camera_used=0")
            } catch(e:Exception) {
                Log.i("AirGesture","backend_benchmark=$delegate supported=false error=${e.javaClass.simpleName} camera_used=0")
                if(delegate==Delegate.CPU) throw e
            } finally {task?.close();image.close();bitmap.recycle()}
        }
    }
}
