package com.airgesture.app.cursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.min
import kotlin.math.roundToInt

/** One small, non-interactive overlay; underlying apps continue to receive physical touches. */
class GlobalCursorOverlay(context: Context) : AutoCloseable {
    private val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    private val displayContext = context.createDisplayContext(display)
    private val windowContext = if (Build.VERSION.SDK_INT >= 30)
        displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null) else displayContext
    private val manager = windowContext.getSystemService(WindowManager::class.java)
    private val density = windowContext.resources.displayMetrics.density
    private val size = (28 * density).roundToInt()
    val inset get() = size / 2f
    private val mapper = CoordinateMapper()
    private var attached = false
    private var statusOnly=false
    private var sceneText:String?=null
    private var sceneColor=Color.GRAY
    private var confirmation=0
    private val presentation=CursorPresentation()
    private var renderBounds:Point?=null
    val rotation get() = display.rotation
    private val view = object : View(windowContext) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            sceneText?.let { text ->
                paint.style=Paint.Style.FILL;paint.color=Color.rgb(25,35,40)
                canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),12*density,12*density,paint)
                paint.color=sceneColor;canvas.drawCircle(12*density,height/2f,4*density,paint)
                paint.color=Color.WHITE;paint.textSize=12*resources.displayMetrics.scaledDensity
                canvas.drawText(text,23*density,height/2f-(paint.ascent()+paint.descent())/2,paint)
                return
            }
            val cx = width / 2f
            val cy = height / 2f
            if(statusOnly){paint.style=Paint.Style.FILL;paint.color=Color.rgb(35,185,125);canvas.drawCircle(cx,cy,3*density,paint);return}
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4 * density
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, 9 * density, paint)
            paint.strokeWidth = 2 * density
            paint.color = when(confirmation){1,3->Color.rgb(255,193,70);2->Color.rgb(100,255,165);-1->Color.rgb(255,100,100);else->Color.CYAN}
            canvas.drawCircle(cx, cy, 9 * density, paint)
            paint.style = Paint.Style.FILL
            if(confirmation==0)canvas.drawCircle(cx, cy, 3 * density, paint)
            else {paint.textSize=10*density;paint.textAlign=Paint.Align.CENTER;canvas.drawText(when(confirmation){3->"2";-1->"×";else->confirmation.toString()},cx,cy-(paint.ascent()+paint.descent())/2,paint)}
        }
    }.apply { visibility = View.INVISIBLE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val params = WindowManager.LayoutParams(size, size,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "AirGestureCursor"
        // Android 12+ blocks touches through overly opaque untrusted overlay windows.
        alpha = if (Build.VERSION.SDK_INT >= 31)
            min(0.75f, context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch) else 0.75f
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private val animate=object:Runnable {
        override fun run(){
            if(!attached || sceneText!=null)return
            val now=android.os.SystemClock.uptimeMillis()
            try{presentation.sample(now)?.let{position(it)}}catch(_:Exception){hide();return}
            if(presentation.moving(now))view.postOnAnimation(this)
        }
    }
    private fun position(normalized:CursorPoint):CursorPoint? {
        val bounds=renderBounds ?: bounds()
        val point=mapper.toPixels(normalized,bounds.x,bounds.y,size/2f) ?: return null
        val x=(point.x-size/2f).roundToInt();val y=(point.y-size/2f).roundToInt()
        if(params.x!=x || params.y!=y){params.x=x;params.y=y;manager.updateViewLayout(view,params)}
        return point
    }

    fun attach() { manager.addView(view, params); attached = true }
    internal fun renderedCenter():CursorPoint? {
        if(!attached || view.visibility!=View.VISIBLE || !view.isAttachedToWindow)return null
        val location=IntArray(2);view.getLocationOnScreen(location)
        return CursorPoint(location[0]+view.width/2f,location[1]+view.height/2f)
    }

    @Suppress("DEPRECATION")
    fun bounds(): Point = if (Build.VERSION.SDK_INT >= 30) {
        manager.currentWindowMetrics.bounds.let { Point(it.width(), it.height()) }
    } else Point().also { display.getRealSize(it) }

    /** Returns the rendered start of this frame's short interpolation for diagnostics. */
    fun show(normalized: CursorPoint?, immediate:Boolean=false, confirmation:Int=0): CursorPoint? {
        statusOnly=false;sceneText=null
        if (!attached || normalized == null) { hide(); return null }
        if(params.width!=size || params.height!=size || params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON==0){
            params.width=size;params.height=size;params.flags=params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            manager.updateViewLayout(view,params)
        }
        this.confirmation=confirmation
        // Read window metrics once per measured target, not on every animation tick.
        renderBounds=bounds()
        view.removeCallbacks(animate)
        val point=position(presentation.set(normalized,android.os.SystemClock.uptimeMillis(),immediate))
        view.visibility = View.VISIBLE
        view.invalidate();view.postOnAnimation(animate)
        return point
    }

    /** A stationary status dot keeps reading awake only while calibrated eyes are present. */
    fun showReadingStatus(visible:Boolean){
        if(!visible){hide();return}
        show(CursorPoint(.97f,.04f));statusOnly=true;view.invalidate()
    }
    /** Feedback for scene mode without covering the content with a pointer or preventing sleep. */
    fun showSceneStatus(text:String,ready:Boolean,paused:Boolean){
        if(!attached)return
        view.removeCallbacks(animate);presentation.reset()
        sceneText=text;sceneColor=if(paused)Color.LTGRAY else if(ready)Color.rgb(35,215,135) else Color.rgb(255,193,70)
        val width=(172*density).roundToInt();val height=(32*density).roundToInt()
        val x=(bounds().x-width-10*density).roundToInt();val y=(60*density).roundToInt()
        val flags=params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        if(params.width!=width || params.height!=height || params.x!=x || params.y!=y || params.flags!=flags){
            params.width=width;params.height=height;params.x=x;params.y=y;params.flags=flags;manager.updateViewLayout(view,params)
        }
        view.visibility=View.VISIBLE;view.invalidate()
    }
    fun hide() { view.removeCallbacks(animate);presentation.reset();view.visibility = View.INVISIBLE }
    override fun close() {
        hide();if (attached) { attached = false; manager.removeViewImmediate(view) }
    }
}
