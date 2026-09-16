package com.airgesture.app.cursor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.hypot

/** Visual-only surface: no touch consumption, dwell clicks, or accessibility actions. */
class CursorTestView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mapper = CoordinateMapper()
    private var normalized: CursorPoint? = null
    private var frameTimestamp = 0L
    private var reportedTimestamp = -1L
    private var confirmation=0
    var onFrameDrawn: ((Long, Long) -> Unit)? = null
    var onTargetChanged: ((Int) -> Unit)? = null
    private var hovered = -1
    private val targets = listOf(CursorPoint(.12f, .14f), CursorPoint(.88f, .14f),
        CursorPoint(.5f, .5f), CursorPoint(.12f, .86f), CursorPoint(.88f, .86f))
    private val density get() = resources.displayMetrics.density
    private val clicks=IntArray(5)
    fun resetClicks(){clicks.fill(0);invalidate()}
    fun recordClick(point:CursorPoint):Int {
        val pixel=mapper.toPixels(point,width,height,14*density) ?: return 0
        val target=targets.indexOfFirst { t->
            val center=mapper.toPixels(t,width,height,14*density) ?: return@indexOfFirst false
            hypot(pixel.x-center.x,pixel.y-center.y)<=22*density
        }
        if(target>=0)clicks[target]++
        invalidate();return target+1
    }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable = false }

    fun show(point: CursorPoint?, timestampMs: Long, confirmation:Int=0) {
        this.confirmation=confirmation
        normalized = point
        frameTimestamp = timestampMs
        postInvalidateOnAnimation()
    }

    fun clear() { normalized = null; hovered = -1; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val point = normalized?.let { mapper.toPixels(it, width, height, 14 * density) }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(35, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        var current = -1
        targets.forEachIndexed { index, target ->
            val center = mapper.toPixels(target, width, height, 14 * density) ?: return@forEachIndexed
            val radius = 22 * density
            val hit = point != null && hypot(point.x - center.x, point.y - center.y) <= radius
            if (hit) current = index
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * density
            paint.color = if (hit) Color.rgb(100, 255, 165) else Color.argb(200, 255, 255, 255)
            canvas.drawCircle(center.x, center.y, radius, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 13 * density
            canvas.drawText("${index + 1} (${clicks[index]})", center.x, center.y + 5 * density, paint)
        }
        if (current != hovered) { hovered = current; onTargetChanged?.invoke(current + 1) }
        if (point != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * density
            paint.color = Color.BLACK
            canvas.drawCircle(point.x, point.y, 10 * density, paint)
            paint.strokeWidth = 2 * density
            paint.color = when(confirmation){1->Color.rgb(255,193,70);2->Color.rgb(100,255,165);else->Color.CYAN}
            canvas.drawCircle(point.x, point.y, 9 * density, paint)
            paint.style = Paint.Style.FILL
            if(confirmation==0)canvas.drawCircle(point.x, point.y, 3 * density, paint)
            else {paint.textSize=10*density;paint.textAlign=Paint.Align.CENTER;canvas.drawText(confirmation.toString(),point.x,point.y-(paint.ascent()+paint.descent())/2,paint)}
            if (frameTimestamp != reportedTimestamp) {
                reportedTimestamp = frameTimestamp
                onFrameDrawn?.invoke(frameTimestamp, android.os.SystemClock.uptimeMillis())
            }
        }
    }
}
