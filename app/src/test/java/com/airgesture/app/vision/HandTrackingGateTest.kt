package com.airgesture.app.vision

import com.airgesture.app.cursor.CursorPoint
import org.junit.Assert.*
import org.junit.Test

class HandTrackingGateTest {
    private fun hand(tip: CursorPoint = CursorPoint(.5f, .4f)): List<CursorPoint> = MutableList(21) { CursorPoint(.5f, .55f) }.apply {
        this[0] = CursorPoint(.5f, .70f)
        this[5] = CursorPoint(.43f, .55f)
        this[9] = CursorPoint(.5f, .55f)
        this[17] = CursorPoint(.62f, .58f)
        this[8] = tip
    }
    private fun update(gate: HandTrackingGate, p: List<CursorPoint>?, t: Long) = gate.update(p, t, t + 80, 480, 640)
    private fun acquire(gate: HandTrackingGate, t: Long = 1000): CursorPoint? {
        assertNull(update(gate, hand(), t))
        assertNull(update(gate, hand(), t + 100))
        return update(gate, hand(), t + 200)
    }

    @Test fun initialDetectionNeedsMultipleContinuousStableFrames() {
        val gate = HandTrackingGate()
        assertEquals(CursorPoint(.5f, .4f), acquire(gate))
        assertEquals(TrackingState.TRACKING, gate.state)
    }

    @Test fun clippedWristStopsCursorEvenWhenTipIsCenteredAndAll21PointsExist() {
        val gate = HandTrackingGate()
        acquire(gate)
        val cropped = hand().toMutableList().apply { this[0] = CursorPoint(.5f, .995f) }
        assertEquals(21, cropped.size)
        assertNull(update(gate, cropped, 1300))
        assertEquals(TrackingState.EDGE, gate.state)
        assertNull(update(gate, hand(), 1400))
        assertNull(update(gate, hand(), 1500))
        assertNotNull(update(gate, hand(), 1600))
    }

    @Test fun reentryMustMoveFartherInsideThanActiveEdgeThreshold() {
        val gate = HandTrackingGate()
        acquire(gate)
        val closeToEdge = hand().toMutableList().apply { this[4] = CursorPoint(.98f, .5f) }
        assertNotNull(update(gate, closeToEdge, 1300))
        assertNull(update(gate, null, 1400))
        assertNull(update(gate, closeToEdge, 1500))
        assertEquals(TrackingState.EDGE, gate.state)
    }

    @Test fun oneOffRecoveredPointCannotRestoreTrackingAfterLoss() {
        val gate = HandTrackingGate()
        acquire(gate)
        assertNull(update(gate, null, 1300))
        assertEquals(TrackingState.MISSING, gate.state)
        assertNull(update(gate, hand(), 1400))
        assertEquals(TrackingState.ACQUIRING, gate.state)
        assertNull(update(gate, null, 1500))
        assertNull(update(gate, hand(), 1600))
    }

    @Test fun unstableAcquisitionDoesNotEnableCursor() {
        val gate = HandTrackingGate()
        repeat(6) { i -> assertNull(update(gate, hand(CursorPoint(.35f + i * .04f, .4f)), 1000 + i * 100L)) }
        assertEquals(TrackingState.ACQUIRING, gate.state)
    }

    @Test fun suddenIndexJumpMustReacquireInsteadOfJumpingToTarget() {
        val gate = HandTrackingGate()
        acquire(gate)
        val jumped = hand(CursorPoint(.85f, .4f))
        assertNull(update(gate, jumped, 1300))
        assertEquals(TrackingState.UNSTABLE, gate.state)
        assertNull(update(gate, jumped, 1400))
        assertEquals(CursorPoint(.85f, .4f), update(gate, jumped, 1500))
    }

    @Test fun normalFastTravelWithinComfortRegionIsStillAllowed() {
        val gate = HandTrackingGate()
        acquire(gate)
        assertNotNull(update(gate, hand(CursorPoint(.35f, .51f)), 1300))
        assertEquals(TrackingState.TRACKING, gate.state)
    }

    @Test fun delayedInvalidOrCollapsedPointsAreRejected() {
        val gate = HandTrackingGate()
        assertNull(gate.update(hand(), 1000, 1251, 480, 640))
        assertEquals(TrackingState.STALE, gate.state)
        assertNull(update(gate, hand().take(20), 1100))
        assertEquals(TrackingState.INVALID, gate.state)
        assertNull(update(gate, hand().toMutableList().apply { this[12] = CursorPoint(Float.NaN, .5f) }, 1200))
        assertNull(update(gate, List(21) { CursorPoint(.5f, .5f) }, 1300))
    }

    @Test fun longGapStartsNewAcquisition() {
        val gate = HandTrackingGate()
        acquire(gate)
        assertNull(update(gate, hand(), 1500))
        assertEquals(TrackingState.ACQUIRING, gate.state)
    }
}
