package com.airgesture.app.debug

data class PerformanceSnapshot(
    val cameraFps: Double,
    val inferenceFps: Double,
    val received: Long,
    val skipped: Long,
    val completed: Long,
    val meanInferenceMs: Double,
)

/** Counts actual camera capture callbacks separately from frames delivered to the analyzer. */
class PerformanceMonitor {
    private val cameraTimes = ArrayDeque<Long>()
    private val resultTimes = ArrayDeque<Long>()
    private val inferenceTimes = ArrayDeque<Long>()
    private var received = 0L
    private var skipped = 0L
    private var completed = 0L

    @Synchronized fun cameraFrame(now: Long) { record(cameraTimes, now) }
    @Synchronized fun received() { received++ }
    @Synchronized fun skipped() { skipped++ }
    @Synchronized fun result(now: Long, inferenceMs: Long) {
        completed++
        record(resultTimes, now)
        inferenceTimes.addLast(inferenceMs)
        while (inferenceTimes.size > 60) inferenceTimes.removeFirst()
    }

    private fun record(times: ArrayDeque<Long>, now: Long) {
        times.addLast(now)
        while (times.size > 90 || (times.size > 1 && now - times.first() > 2000)) times.removeFirst()
    }

    private fun fps(times: ArrayDeque<Long>): Double = if (times.size < 2) 0.0 else
        (times.size - 1) * 1000.0 / (times.last() - times.first()).coerceAtLeast(1)

    @Synchronized fun snapshot() = PerformanceSnapshot(
        fps(cameraTimes), fps(resultTimes), received, skipped, completed,
        if (inferenceTimes.isEmpty()) 0.0 else inferenceTimes.average(),
    )
}
