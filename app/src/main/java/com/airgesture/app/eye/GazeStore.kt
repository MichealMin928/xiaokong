package com.airgesture.app.eye

import android.content.Context
class GazeStore(context:Context) {
    private val prefs=context.getSharedPreferences("gaze_calibration",Context.MODE_PRIVATE)
    fun load(landscape:Boolean):GazeCalibration? = try {
        val data=prefs.getString(if(landscape)"landscape" else "portrait",null)?.split(',')?.map{it.toFloat()}
        if(data==null) null else if(data.size!=7)null else GazeCalibration(data[0],data[1],data[2],data[3],data[4],data[5],data[6]).takeIf{it.valid}
    }catch(_:Exception){null}
    fun save(value:GazeCalibration,landscape:Boolean){require(value.valid);prefs.edit().putString(if(landscape)"landscape" else "portrait",listOf(value.top,value.center,value.bottom,value.yaw,value.pitch,value.roll,value.scale).joinToString(",")).apply()}
    fun clear(landscape:Boolean){prefs.edit().remove(if(landscape)"landscape" else "portrait").apply()}
}
