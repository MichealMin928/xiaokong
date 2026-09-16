package com.airgesture.app.settings

enum class PerformanceMode(val label:String,val fps:Int,val width:Int,val height:Int) {
    ECONOMY("省电 · 最多 12 FPS",12,320,240),
    BALANCED("均衡 · 最多 20 FPS",20,640,480),
    FAST("高响应 · 最多 25 FPS",25,640,480)
}
data class PerformanceSettings(val mode:PerformanceMode=PerformanceMode.BALANCED,val gpu:Boolean=false) {
    fun config()=GestureConfig(analysisWidth=mode.width,analysisHeight=mode.height,maxInferenceFps=mode.fps,useGpu=gpu)
}
