package com.airgesture.app.settings

import android.content.Context
import com.airgesture.app.cursor.ControlRegion

/** Local cursor preferences only. A saved setting must never start camera capture. */
class GestureSettings(context: Context) {
    private val preferences = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
    fun load() = CursorSettings(
        Sensitivity.entries.firstOrNull { it.name == preferences.getString("sensitivity", null) } ?: Sensitivity.MEDIUM,
        Smoothness.entries.firstOrNull { it.name == preferences.getString("smoothness", null) } ?: Smoothness.MEDIUM,
        SmoothingMethod.entries.firstOrNull { it.name == preferences.getString("method", null) } ?: SmoothingMethod.ONE_EURO,
        preferences.getBoolean("acceleration",false), readRegion("portrait"), readRegion("landscape"),
    )
    fun save(settings: CursorSettings) {
        preferences.edit().putString("sensitivity", settings.sensitivity.name)
            .putString("smoothness", settings.smoothness.name)
            .putString("method", settings.method.name).putBoolean("acceleration",settings.acceleration).apply()
    }
    private fun readRegion(orientation:String):ControlRegion? = try {
        if(!preferences.contains("${orientation}_minX")) null else ControlRegion(
            preferences.getFloat("${orientation}_minX",0f),preferences.getFloat("${orientation}_maxX",0f),
            preferences.getFloat("${orientation}_minY",0f),preferences.getFloat("${orientation}_maxY",0f))
    } catch(_:IllegalArgumentException){null}
    fun saveCalibration(region:ControlRegion?,landscape:Boolean) {
        val prefix=if(landscape)"landscape" else "portrait"
        val editor=preferences.edit()
        if(region==null) listOf("minX","maxX","minY","maxY").forEach {editor.remove("${prefix}_$it")}
        else editor.putFloat("${prefix}_minX",region.minX).putFloat("${prefix}_maxX",region.maxX)
            .putFloat("${prefix}_minY",region.minY).putFloat("${prefix}_maxY",region.maxY)
        editor.apply()
    }
    fun loadPerformance()=PerformanceSettings(
        PerformanceMode.entries.firstOrNull{it.name==preferences.getString("performance_mode",null)} ?: PerformanceMode.BALANCED,
        preferences.getBoolean("gpu",false))
    fun savePerformance(settings:PerformanceSettings) {
        preferences.edit().putString("performance_mode",settings.mode.name).putBoolean("gpu",settings.gpu).apply()
    }
    fun loadActions() = ActionSettings(
        preferences.getBoolean("pinch",true), preferences.getBoolean("swipeUp",true),
        preferences.getBoolean("swipeDown",true), preferences.getBoolean("fistPause",true),
        preferences.getBoolean("swipeLeftBack",false), preferences.getBoolean("swipeRightHome",false),
        preferences.getBoolean("pushExperimental",false), preferences.getBoolean("debug",true),preferences.getBoolean("drag",true))
    fun saveActions(settings: ActionSettings) {
        preferences.edit().putBoolean("pinch",settings.pinch).putBoolean("swipeUp",settings.swipeUp)
            .putBoolean("swipeDown",settings.swipeDown).putBoolean("fistPause",settings.fistPause)
            .putBoolean("swipeLeftBack",settings.swipeLeftBack).putBoolean("swipeRightHome",settings.swipeRightHome)
            .putBoolean("pushExperimental",settings.pushExperimental).putBoolean("debug",settings.debug).putBoolean("drag",settings.drag).apply()
    }
}
