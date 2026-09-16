package com.airgesture.app.cursor

data class CursorPoint(val x: Float, val y: Float) {
    fun isFinite() = x.isFinite() && y.isFinite()
}

/** Bounds are expressed in upright, mirrored camera coordinates. */
data class ControlRegion(val minX: Float = 0.2f, val maxX: Float = 0.8f,
                         val minY: Float = 0.2f, val maxY: Float = 0.8f) {
    init {
        require(listOf(minX, maxX, minY, maxY).all { it.isFinite() })
        require(minX >= 0f && maxX <= 1f && maxX - minX >= 0.1f)
        require(minY >= 0f && maxY <= 1f && maxY - minY >= 0.1f)
    }
}

class CoordinateMapper {
    /** MediaPipe input is upright and unmirrored; mirror exactly once for intuitive front-camera motion. */
    fun normalize(raw: CursorPoint, region: ControlRegion): CursorPoint? {
        if (!raw.isFinite()) return null
        return CursorPoint(
            ((1f - raw.x - region.minX) / (region.maxX - region.minX)).coerceIn(0f, 1f),
            ((raw.y - region.minY) / (region.maxY - region.minY)).coerceIn(0f, 1f),
        )
    }

    /** Destination is a measured View now, and can be current display bounds in a later milestone. */
    fun toPixels(normalized: CursorPoint, width: Int, height: Int, inset: Float): CursorPoint? {
        if (!normalized.isFinite() || width < 1 || height < 1 || !inset.isFinite()) return null
        val pad = inset.coerceIn(0f, ((minOf(width, height) - 1f) / 2f).coerceAtLeast(0f))
        return CursorPoint(
            pad + normalized.x.coerceIn(0f, 1f) * (width - 1 - 2 * pad),
            pad + normalized.y.coerceIn(0f, 1f) * (height - 1 - 2 * pad),
        )
    }
}
