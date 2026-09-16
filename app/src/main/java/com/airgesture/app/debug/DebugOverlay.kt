package com.airgesture.app.debug

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform
import com.airgesture.app.vision.TrackingFrame
import com.airgesture.app.cursor.ControlRegion

/** Draws only numeric landmarks; retains no camera images. Shares PreviewView's exact bounds. */
@androidx.annotation.OptIn(TransformExperimental::class)
class DebugOverlay(context: Context) : View(context) {
    private var points = floatArrayOf()
    private var regionPoints = floatArrayOf()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edges = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20)

    fun show(frame: TrackingFrame, target: OutputTransform?, region: ControlRegion) {
        if (target == null) { clear(); return }
        val transform = CoordinateTransform(frame.transform, target)
        regionPoints = floatArrayOf(
            (1 - region.minX) * frame.width, region.minY * frame.height,
            (1 - region.maxX) * frame.width, region.minY * frame.height,
            (1 - region.maxX) * frame.width, region.maxY * frame.height,
            (1 - region.minX) * frame.width, region.maxY * frame.height,
        ).also { transform.mapPoints(it) }
        val hand = frame.hand
        if (hand == null || hand.normalized.size != 21) { hideHand(); return }
        val mapped = FloatArray(42)
        hand.normalized.forEachIndexed { index, point ->
            mapped[index * 2] = point.x * frame.width
            mapped[index * 2 + 1] = point.y * frame.height
        }
        transform.mapPoints(mapped)
        points = mapped
        invalidate()
    }

    fun hideHand() { points = floatArrayOf(); invalidate() }
    fun clear() { points = floatArrayOf(); regionPoints = floatArrayOf(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (regionPoints.size == 8) {
            val path = Path().apply {
                moveTo(regionPoints[0], regionPoints[1])
                for (i in 2..6 step 2) lineTo(regionPoints[i], regionPoints[i + 1])
                close()
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * resources.displayMetrics.density
            paint.color = Color.CYAN
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 12f * resources.displayMetrics.density
            canvas.drawText("食指活动区", regionPoints[0], (regionPoints[1] - 6 * resources.displayMetrics.density).coerceAtLeast(paint.textSize), paint)
        }
        if (points.size != 42) return
        paint.color = Color.rgb(70, 255, 170)
        paint.strokeWidth = 2f * resources.displayMetrics.density
        edges.forEach { (a, b) -> canvas.drawLine(points[a * 2], points[a * 2 + 1], points[b * 2], points[b * 2 + 1], paint) }
        points.indices.step(2).forEach { i ->
            paint.color = if (i == 16) Color.YELLOW else Color.WHITE
            canvas.drawCircle(points[i], points[i + 1], (if (i == 16) 5f else 3f) * resources.displayMetrics.density, paint)
        }
    }
}
