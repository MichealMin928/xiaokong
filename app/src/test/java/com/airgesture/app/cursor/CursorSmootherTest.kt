package com.airgesture.app.cursor

import com.airgesture.app.settings.CursorSettings
import com.airgesture.app.settings.SmoothingMethod
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class CursorSmootherTest {
    @Test fun oneEuroReducesStationaryJitterAtObservedTwelveFps() {
        val filter = CursorSmoother(CursorSettings())
        val errors = (0 until 240).map { i ->
            val noisy = .5f + if (i % 2 == 0) .01f else -.01f
            filter.filter(CursorPoint(noisy, noisy), i * 83L).x - .5f
        }.drop(30)
        val rms = sqrt(errors.map { it * it }.average())
        println("synthetic_12fps stationary raw_rms=0.01 one_euro_rms=$rms")
        assertTrue("resting jitter RMS=$rms", rms < .005)
    }

    @Test fun oneEuroTracksFastCrossScreenMoveWithoutOvershoot() {
        val filter = CursorSmoother(CursorSettings())
        repeat(20) { filter.filter(CursorPoint(.2f, .2f), it * 83L) }
        val moved = (20..22).map { filter.filter(CursorPoint(.8f, .8f), it * 83L).x }
        println("synthetic_12fps step 0.2_to_0.8 first_three=$moved")
        assertTrue("first response must cover most of the movement", moved.first() > .7f)
        assertTrue(moved.all { it in .2f..0.8f })
        assertTrue("settle within three input frames", moved.last() > .78f)
    }

    @Test fun missingHandResetDoesNotPullNewHandFromOldPosition() {
        val controller = CursorController()
        controller.update(CursorPoint(.62f, .32f), 1000, 1010)
        assertNull(controller.update(null, 1083, 1090))
        val result = controller.update(CursorPoint(.38f, .47f), 1166, 1170)!!
        assertEquals(.8333333f, result.x, .0001f)
        assertEquals(.7333333f, result.y, .0001f)
    }

    @Test fun emaUsesElapsedTimeRatherThanFixedFrameCount() {
        val settings = CursorSettings(method = SmoothingMethod.EMA)
        val a = CursorSmoother(settings)
        val b = CursorSmoother(settings)
        a.filter(CursorPoint(0f, 0f), 0)
        b.filter(CursorPoint(0f, 0f), 0)
        val slow = a.filter(CursorPoint(1f, 1f), 100)
        b.filter(CursorPoint(1f, 1f), 50)
        val fast = b.filter(CursorPoint(1f, 1f), 100)
        assertEquals(slow.x, fast.x, .00001f)
    }

    @Test fun duplicatesAreIgnoredAndLongGapResets() {
        val f = CursorSmoother(CursorSettings())
        val origin = CursorPoint(.2f, .2f)
        assertEquals(origin, f.filter(origin, 1000))
        assertEquals(origin, f.filter(CursorPoint(.8f, .8f), 1000))
        assertEquals(origin, f.filter(CursorPoint(.8f, .8f), 999))
        assertEquals(CursorPoint(.8f, .8f), f.filter(CursorPoint(.8f, .8f), 1400))
    }
}
