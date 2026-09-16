package com.airgesture.app.camera

data class FpsBand(val lower:Int,val upper:Int)
object CaptureRatePolicy {
    /** Request only a band actually advertised by the selected camera; never invent a 3 FPS sensor mode. */
    fun idleBand(supported:List<FpsBand>):FpsBand? = supported.filter{it.lower>0 && it.lower<=it.upper && it.upper<=20}
        .minWithOrNull(compareBy<FpsBand>{it.upper}.thenBy{it.lower})
}
