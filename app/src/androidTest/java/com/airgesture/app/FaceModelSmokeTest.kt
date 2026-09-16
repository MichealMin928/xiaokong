package com.airgesture.app

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class FaceModelSmokeTest {
    @Test fun bundledFaceModelReturnsOrderedLiveResults(){
        val queue=ArrayBlockingQueue<Pair<Long,Int>>(2)
        val task=FaceLandmarker.createFromOptions(InstrumentationRegistry.getInstrumentation().targetContext,
            FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setNumFaces(2).setRunningMode(RunningMode.LIVE_STREAM).setResultListener{result,_->queue.offer(result.timestampMs() to result.faceLandmarks().size)}.build())
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888);val image=BitmapImageBuilder(bitmap).build()
        val begin=SystemClock.uptimeMillis()
        try{repeat(30){val t=begin+it*100;task.detectAsync(image,t);val result=checkNotNull(queue.poll(5,TimeUnit.SECONDS));assertEquals(t,result.first);assertEquals(0,result.second)}
            android.util.Log.i("AirGesture","face_model_smoke=PASS callbacks=30 elapsed_ms=${SystemClock.uptimeMillis()-begin} input=synthetic_blank")
        }finally{task.close();image.close();bitmap.recycle()}
    }
}
