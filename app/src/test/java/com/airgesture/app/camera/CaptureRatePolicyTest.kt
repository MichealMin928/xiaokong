package com.airgesture.app.camera

import org.junit.Assert.*
import org.junit.Test
class CaptureRatePolicyTest {
    @Test fun onlyAdvertisedRangesMayBeSelected(){
        val ranges=listOf(FpsBand(10,30),FpsBand(30,30),FpsBand(15,15),FpsBand(10,15))
        assertEquals(FpsBand(10,15),CaptureRatePolicy.idleBand(ranges))
        assertNull(CaptureRatePolicy.idleBand(listOf(FpsBand(30,30))))
        assertNull(CaptureRatePolicy.idleBand(emptyList()))
        assertNull(CaptureRatePolicy.idleBand(listOf(FpsBand(15,10),FpsBand(0,15))))
    }
}
