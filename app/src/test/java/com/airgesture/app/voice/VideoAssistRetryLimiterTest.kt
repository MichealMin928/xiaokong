package com.airgesture.app.voice

import org.junit.Assert.*
import org.junit.Test

class VideoAssistRetryLimiterTest {
    @Test fun rapidFocusStealingStopsAndNormalLaterClipCanResume(){
        val limiter=VideoAssistRetryLimiter()
        assertTrue(limiter.allow(1000));assertTrue(limiter.allow(2000))
        assertFalse(limiter.allow(3000));assertFalse(limiter.allow(10999))
        assertTrue(limiter.allow(11000))
        assertFalse(limiter.allow(11001))
        assertTrue(limiter.allow(12000))
    }
    @Test fun explicitUserRestartClearsOnlyTheFocusRetryBudget(){
        val limiter=VideoAssistRetryLimiter()
        limiter.allow(1000);limiter.allow(2000);assertFalse(limiter.allow(3000))
        limiter.reset();assertTrue(limiter.allow(3001))
    }
}
