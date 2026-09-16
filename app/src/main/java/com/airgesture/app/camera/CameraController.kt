package com.airgesture.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.airgesture.app.debug.PerformanceMonitor
import com.airgesture.app.debug.PerformanceSnapshot
import com.airgesture.app.settings.GestureConfig
import com.airgesture.app.vision.HandTrackingEngine
import com.airgesture.app.vision.TrackingFrame
import java.util.concurrent.Executors

/** All CameraX bindings are on the main thread; image conversion/inference uses one worker. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraController(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val previewView: PreviewView?,
    private val config: GestureConfig,
    private val onStatus: (String) -> Unit,
    private val onFrame: (TrackingFrame, PerformanceSnapshot) -> Unit,
    private val displayRotation: () -> Int = { android.view.Surface.ROTATION_0 },
    private val outputImageFormat:Int=ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888,
    private val onResources:(Boolean,Boolean)->Unit={_,_->},
) : AutoCloseable {
    private class Session {
        @Volatile var stopped = false
        var engine: HandTrackingEngine? = null // Only accessed by the analyzer executor.
        var failed = false
        @Volatile var modelLoaded=false
        var bound=false
        val performance = PerformanceMonitor()
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = ContextCompat.getMainExecutor(context)
    private var session: Session? = null
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var observedState: LiveData<CameraState>? = null
    private var stateObserver: Observer<CameraState>? = null
    private var boundCamera:androidx.camera.core.Camera?=null
    private var lowPowerRequested=false
    private var appliedLowPower:Boolean?=null

    fun start() {
        stop()
        val current = Session().also { session = it }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (current.stopped) return@addListener
            try {
                val cameraProvider = future.get()
                check(cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) { "No front camera" }
                val viewPort = previewView?.let { checkNotNull(it.viewPort) { "Preview not laid out" } }
                val resolution = ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(config.analysisWidth, config.analysisHeight),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
                val rotation = previewView?.display?.rotation ?: displayRotation()
                val previewCase = previewView?.let { view ->
                    Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
                        .build().also { it.surfaceProvider = view.surfaceProvider }
                }
                val analyzerBuilder = ImageAnalysis.Builder().setResolutionSelector(resolution)
                    .setTargetRotation(rotation)
                    // Convert only frames admitted by HandTrackingEngine. RGBA output
                    // makes CameraX convert even the frames we immediately skip.
                    .setOutputImageFormat(outputImageFormat)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                Camera2Interop.Extender(analyzerBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        if (!current.stopped) current.performance.cameraFrame(SystemClock.uptimeMillis())
                    }
                })
                val analysisCase = analyzerBuilder.build()
                analysisCase.setAnalyzer(executor) { proxy ->
                    if (current.stopped || current.failed) {
                        proxy.close()
                    } else {
                        try {
                            val engine = current.engine ?: HandTrackingEngine(context.applicationContext, config,
                                current.performance,
                                { frame ->
                                    val metrics = current.performance.snapshot()
                                    main.execute { if (!current.stopped) onFrame(frame, metrics) }
                                },
                                { message ->
                                    main.execute { if (!current.stopped) onStatus(message) }
                                }).also {
                                    current.engine = it
                                    current.modelLoaded=true
                                    main.execute{if(!current.stopped)onResources(current.bound,true)}
                                    Log.i("AirGesture", "model_initialized=1 requested_gpu=${config.useGpu} model=${config.visionMode} hand_fallback=${config.handFallback}")
                                }
                            engine.analyze(proxy)
                        } catch (e: Exception) {
                            proxy.close()
                            current.failed = true
                            Log.e("AirGesture", "model_failed type=${e.javaClass.simpleName} detail=${e.message?.take(240)}", e)
                            main.execute { if (!current.stopped) onStatus("模型初始化失败：${e.javaClass.simpleName}。请停止后重试。") }
                        }
                    }
                }
                provider = cameraProvider
                preview = previewCase
                analysis = analysisCase
                val group = UseCaseGroup.Builder().addUseCase(analysisCase).apply {
                    if (previewCase != null) addUseCase(previewCase)
                    if (viewPort != null) setViewPort(viewPort)
                }.build()
                val camera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, group)
                current.bound=true;onResources(true,current.modelLoaded)
                boundCamera=camera;appliedLowPower=null;setLowPowerCapture(lowPowerRequested)
                val observer = Observer<CameraState> { state ->
                    if (!current.stopped) state.error?.let { onStatus("相机错误 ${it.code}；请停止后重试。") }
                }
                observedState = camera.cameraInfo.cameraState
                stateObserver = observer
                observedState?.observe(owner, observer)
                Log.i("AirGesture", "camera_bound=front rotation=$rotation resolution=${analysisCase.resolutionInfo?.resolution} output_format=$outputImageFormat")
            } catch (e: Exception) {
                Log.e("AirGesture", "camera_start_failed type=${e.javaClass.simpleName}")
                onStatus("前摄启动失败：${e.javaClass.simpleName}。请停止后重试。")
                stop()
            }
        }, main)
    }

    fun setLowPowerCapture(value:Boolean){
        lowPowerRequested=value
        val camera=boundCamera ?: return
        if(appliedLowPower==value)return
        appliedLowPower=value
        try{
            val control=Camera2CameraControl.from(camera.cameraControl)
            val request=if(value){
                val available=Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
                val band=CaptureRatePolicy.idleBand(available.map{FpsBand(it.lower,it.upper)})
                if(band==null){Log.i("AirGesture","camera_idle_rate_supported=0");return}
                Log.i("AirGesture","camera_capture_band=${band.lower}..${band.upper} low_power=1")
                control.setCaptureRequestOptions(CaptureRequestOptions.Builder().setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,android.util.Range(band.lower,band.upper)).build())
            }else{
                Log.i("AirGesture","camera_capture_band=AUTO low_power=0")
                control.clearCaptureRequestOptions()
            }
            request.addListener({runCatching{request.get()}.onFailure{Log.i("AirGesture","camera_capture_option_cancelled=${it.javaClass.simpleName}")}},main)
        }catch(e:Exception){Log.i("AirGesture","camera_capture_option_unavailable=${e.javaClass.simpleName}")}
    }

    fun updateRotation(rotation: Int) {
        analysis?.targetRotation = rotation
        preview?.targetRotation = rotation
    }

    fun stop() {
        session?.let { old ->
            old.stopped = true
            old.bound=false;onResources(false,old.modelLoaded)
            executor.execute { old.engine?.close(); old.engine = null;old.modelLoaded=false;main.execute{if(session==null || session===old)onResources(false,false)} }
        }
        session = null
        boundCamera=null;appliedLowPower=null
        stateObserver?.let { observedState?.removeObserver(it) }
        stateObserver = null
        observedState = null
        analysis?.clearAnalyzer()
        val cases = listOfNotNull(preview, analysis)
        if (cases.isNotEmpty()) provider?.unbind(*cases.toTypedArray())
        analysis = null
        preview = null
        Log.i("AirGesture", "camera_released=1")
    }

    override fun close() { stop(); executor.shutdown() }
}
