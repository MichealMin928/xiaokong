package com.airgesture.app.debug

import android.util.Log

/** Bounded metadata-only log; no images, file paths, app contents or camera buffers. */
class DebugLog {
    private val lines = ArrayDeque<String>()
    @Synchronized fun add(line: String) {
        lines.addLast(line)
        while (lines.size > 1800) lines.removeFirst()
        Log.i("AirGesture", line)
    }
    @Synchronized fun export(): String = "AirGesture metadata only\n" + lines.joinToString("\n") + "\n"
}
