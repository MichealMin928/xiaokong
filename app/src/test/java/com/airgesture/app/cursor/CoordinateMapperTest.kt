package com.airgesture.app.cursor

import org.junit.Assert.*
import org.junit.Test

class CoordinateMapperTest {
    private val mapper = CoordinateMapper()
    @Test fun frontCameraMirrorsExactlyOnceAndUsesComfortRegion() {
        val region = ControlRegion()
        assertEquals(0f, mapper.normalize(CursorPoint(.8f, .2f), region)!!.x, 0.00001f)
        assertEquals(1f, mapper.normalize(CursorPoint(.2f, .8f), region)!!.x, 0.00001f)
        val center = mapper.normalize(CursorPoint(.5f, .5f), region)!!
        assertEquals(.5f, center.x, .00001f)
        assertEquals(.5f, center.y, .00001f)
        assertEquals(CursorPoint(0f, 0f), mapper.normalize(CursorPoint(1f, 0f), region))
    }
    @Test fun mappingUsesCurrentDestinationBoundsIncludingTinyViews() {
        val center = CursorPoint(.5f, .5f)
        assertEquals(CursorPoint(149.5f, 299.5f), mapper.toPixels(center, 300, 600, 14f))
        assertEquals(CursorPoint(299.5f, 149.5f), mapper.toPixels(center, 600, 300, 14f))
        assertEquals(CursorPoint(0f, 0f), mapper.toPixels(center, 1, 1, 14f))
        assertNull(mapper.toPixels(center, 0, 600, 14f))
        assertNull(mapper.normalize(CursorPoint(Float.NaN, .5f), ControlRegion()))
    }
    @Test fun staleOrMissingHandCannotProduceCursor() {
        val controller = CursorController()
        assertNull(controller.update(CursorPoint(.5f, .5f), 1000, 1301))
        assertNull(controller.update(CursorPoint(.5f, .5f), 1001, 1000))
        assertNull(controller.update(null, 1000, 1100))
    }

    @Test fun targetFiveCanBeReachedWithTipNearMiddleOfCameraAndPalmRoomBelow() {
        val region = CursorController().region
        // Before: target 5 required raw x=.272, y=.716. Now it is x=.3632, y=.508.
        val point = mapper.normalize(CursorPoint(.3632f, .508f), region)!!
        assertEquals(.88f, point.x, .0001f)
        assertEquals(.86f, point.y, .0001f)
        assertTrue(region.maxY <= .56f)
    }
}
