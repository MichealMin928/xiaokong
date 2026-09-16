package com.airgesture.app

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ModelSmokeTest {
    @Test fun liveStreamReturnsOrderedBlankResultsWithoutCameraPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val results = ArrayBlockingQueue<Pair<Long, Int>>(2)
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
            .setResultListener { result, _ -> results.offer(result.timestampMs() to result.landmarks().size) }
            .setErrorListener { results.offer(-1L to -1) }.build()
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val image = BitmapImageBuilder(bitmap).build()
        val task = HandLandmarker.createFromOptions(context, options)
        val begin = android.os.SystemClock.uptimeMillis()
        try {
            repeat(20) { index ->
                val timestamp = begin + index * 50L
                task.detectAsync(image, timestamp)
                val result = checkNotNull(results.poll(10, TimeUnit.SECONDS)) { "Live stream callback timed out" }
                assertEquals(timestamp, result.first)
                assertEquals(0, result.second)
            }
            Log.i("AirGesture", "live_smoke=PASS input=synthetic_blank callbacks=20 elapsed_ms=${android.os.SystemClock.uptimeMillis() - begin} camera_used=0")
        } finally {
            task.close()
            image.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    @Test fun bundledModelLoadsAndBlankImageProducesNoHandOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE).setNumHands(1).build()
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val image = BitmapImageBuilder(bitmap).build()
        val task = HandLandmarker.createFromOptions(context, options)
        try {
            val result = task.detect(image)
            assertEquals(0, result.landmarks().size)
            Log.i("AirGesture", "model_smoke=PASS input=synthetic_blank hands=0 camera_used=0")
        } finally {
            task.close()
            image.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
