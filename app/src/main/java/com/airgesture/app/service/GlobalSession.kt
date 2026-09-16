package com.airgesture.app.service

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.airgesture.app.debug.DebugLog

data class GlobalStatus(val running: Boolean = false, val paused: Boolean = false,
                        val message: String = "隔空控制尚未开启", val diagnostics: String = "")

/** Process-local numerical status only. Never retains a Service, Activity, image or landmarks. */
object GlobalSession {
    var actionsEnabled = false
        internal set
    private val mutable = MutableLiveData(GlobalStatus())
    val status: LiveData<GlobalStatus> = mutable
    val log = DebugLog()
    val running get() = mutable.value?.running == true
    fun publish(status: GlobalStatus) { mutable.value = status }
}
