package com.airgesture.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.airgesture.app.debug.PerformanceMonitor
import com.airgesture.app.settings.GestureConfig
import com.airgesture.app.settings.VisionMode
import com.airgesture.app.eye.FacePoint
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/** One async inference at a time. Camera buffers always close, including errors/skipped frames. */
@androidx.annotation.OptIn(TransformExperimental::class)
class HandTrackingEngine(
    private val context: Context,
    private val config: GestureConfig,
    private val performance: PerformanceMonitor,
    private val onFrame: (TrackingFrame) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {
    private data class Flight(
        val timestamp: Long,
        val submittedAt: Long,
        val bitmap: Bitmap,
        val image: MPImage,
        val transform: OutputTransform,
    ) {
        private val released=java.util.concurrent.atomic.AtomicBoolean(false)
        fun release() {
            if(!released.compareAndSet(false,true))return
            image.close()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
    private val pending = AtomicReference<Flight?>(null)
    private val transformFactory = ImageProxyTransformFactory().apply {
        isUsingCropRect = false
        isUsingRotationDegrees = true
    }
    @Volatile private var closed = false
    private var lastTimestamp = 0L
    private var lastHandProbe=0L
    @Volatile private var handBurstUntil=0L
    private fun createLandmarker(delegate:Delegate) = HandLandmarker.createFromOptions(context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").setDelegate(delegate).build())
            .setNumHands(1)
            .setMinHandDetectionConfidence(config.minDetectionConfidence)
            .setMinHandPresenceConfidence(config.minPresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                val flight = pending.get()
                if (flight != null && flight.timestamp == result.timestampMs()) {
                    try {
                        if (!closed) {
                            val now = SystemClock.uptimeMillis()
                            val points = result.landmarks().firstOrNull()
                            val category = result.handedness().firstOrNull()?.firstOrNull()
                            val hand = points?.takeIf { it.size == 21 }?.let {
                                HandLandmarks(
                                    it.map { p -> Landmark3D(p.x(), p.y(), p.z()) },
                                    result.worldLandmarks().firstOrNull().orEmpty().map { p -> Landmark3D(p.x(), p.y(), p.z()) },
                                    category?.categoryName() ?: "Unknown",
                                    category?.score() ?: 0f,
                                )
                            }
                            if(config.visionMode==VisionMode.FACE && hand!=null)handBurstUntil=now+1800
                            val latency = now - flight.submittedAt
                            performance.result(now, latency)
                            onFrame(TrackingFrame(flight.timestamp, flight.bitmap.width, flight.bitmap.height,
                                hand, flight.transform, latency, now - flight.timestamp))
                        }
                    } finally {
                        // Release before accepting a new frame; never retain a camera image in the UI.
                        flight.release()
                        pending.compareAndSet(flight, null)
                    }
                }
            }
            .setErrorListener { error ->
                pending.getAndSet(null)?.release()
                if (!closed) onError("推理异常：${error.javaClass.simpleName}；请停止后重试。")
            }.build())

    private val landmarker = if(config.visionMode==VisionMode.HAND || config.handFallback) {
        if(config.useGpu) try {createLandmarker(Delegate.GPU)} catch(_:Exception){createLandmarker(Delegate.CPU)}
        else createLandmarker(Delegate.CPU)
    }else null
    private val faceLandmarker=if(config.visionMode==VisionMode.FACE) FaceLandmarker.createFromOptions(context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").setDelegate(Delegate.CPU).build())
            .setNumFaces(2).setMinFaceDetectionConfidence(.6f).setMinFacePresenceConfidence(.6f).setMinTrackingConfidence(.6f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result,_ ->
                val flight=pending.get()
                if(flight!=null && flight.timestamp==result.timestampMs()){
                    try{if(!closed){
                        val now=SystemClock.uptimeMillis()
                        val points=result.faceLandmarks().singleOrNull()?.map{FacePoint(it.x(),it.y(),it.z())}
                        performance.result(now,now-flight.submittedAt)
                        onFrame(TrackingFrame(flight.timestamp,flight.bitmap.width,flight.bitmap.height,null,flight.transform,
                            now-flight.submittedAt,now-flight.timestamp,points,VisionMode.FACE))
                    }}finally{flight.release();pending.compareAndSet(flight,null)}
                }
            }.setErrorListener { error ->
                pending.getAndSet(null)?.release()
                if(!closed)onError("眼控模型异常：${error.javaClass.simpleName}；请停止后重试")
            }.build()) else null

    override fun analyze(proxy: ImageProxy) {
        performance.received()
        val now = SystemClock.uptimeMillis()
        if (closed || pending.get() != null || now - lastTimestamp < 1000L / config.maxInferenceFps) {
            performance.skipped()
            proxy.close()
            return
        }
        var raw: Bitmap? = null
        var rotated: Bitmap? = null
        var image: MPImage? = null
        try {
            val timestamp = max(now, lastTimestamp + 1)
            lastTimestamp = timestamp
            val transform = transformFactory.getOutputTransform(proxy)
            val rotation = proxy.imageInfo.rotationDegrees
            // CameraX 1.5.3 uses its native stride-aware YUV-to-bitmap conversion here,
            // after admission. Keep full-resolution pixels and the same rotation/mirror.
            raw = proxy.toBitmap()
            rotated = if (rotation == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, false)
            if (raw !== rotated) raw.recycle()
            raw = null
            // Inference stays unmirrored. CameraX's preview transform applies the front-camera mirror once.
            image = BitmapImageBuilder(rotated).build()
            val flight = Flight(timestamp, SystemClock.uptimeMillis(), rotated, image, transform)
            pending.set(flight)
            rotated = null
            image = null
            if(faceLandmarker!=null && !(config.handFallback && (now<handBurstUntil || now-lastHandProbe>=400)))faceLandmarker.detectAsync(flight.image,timestamp)
            else {lastHandProbe=now;checkNotNull(landmarker).detectAsync(flight.image,timestamp)}
        } catch (e: Exception) {
            pending.getAndSet(null)?.release()
            image?.close()
            rotated?.takeUnless { it.isRecycled }?.recycle()
            raw?.takeUnless { it.isRecycled }?.recycle()
            if (!closed) onError("图像处理失败：${e.javaClass.simpleName}；请停止后重试。")
        } finally {
            proxy.close()
        }
    }

    /** Called on the same executor after clearing/unbinding the analyzer. */
    override fun close() {
        closed = true
        landmarker?.close();faceLandmarker?.close()
        pending.getAndSet(null)?.release()
    }
}
