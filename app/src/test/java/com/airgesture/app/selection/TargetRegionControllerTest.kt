package com.airgesture.app.selection

import org.junit.Assert.*
import org.junit.Test

class TargetRegionControllerTest {
    private fun target(id:Int,x:Float,y:Float)=NumberTarget(id,SelectionBounds(x,y,x+20,y+20))
    @Test fun onlyOccupiedRegionsHaveNumbersAndEmptyCannotAdvance(){
        val c=TargetRegionController();c.start(listOf(target(3,10f,10f),target(7,250f,250f)),300,300)
        assertEquals(listOf(1,9),c.regions.map{it.number})
        assertFalse(c.chooseRegion(5));assertFalse(c.choosingButtons);assertNull(c.target(1))
        assertTrue(c.chooseRegion(9));assertTrue(c.choosingButtons)
        assertEquals(7,c.target(1)?.number);assertNull(c.target(9))
    }
    @Test fun choosingRegionNeverClicksEvenIfItContainsOnlyOneButton(){
        val c=TargetRegionController();c.start(listOf(target(19,120f,120f)),300,300)
        assertNull(c.target(5));assertTrue(c.chooseRegion(5));assertEquals(1,c.displayButtons().single().number)
        assertEquals(19,c.target(1)?.number)
        assertEquals(SelectionBounds(120f,120f,140f,140f),c.target(1)?.bounds)
    }
    @Test fun denseAreaRefinesAndPreservesOriginalNodeIds(){
        val items=(1..30).map{target(it,((it-1)%6)*12f,((it-1)/6)*12f)}
        val c=TargetRegionController();c.start(items,900,900)
        assertTrue(c.chooseRegion(1));assertFalse(c.choosingButtons)
        assertTrue(c.chooseRegion(1));assertFalse(c.choosingButtons)
        val n=c.regions.first().number;assertTrue(c.chooseRegion(n));assertTrue(c.choosingButtons)
        assertTrue(c.buttons.size<=20);assertEquals(c.buttons.first().number,c.target(1)?.number)
    }
    @Test fun resetAndEmptyPageNeverProduceAnArbitraryPoint(){
        val c=TargetRegionController();c.start(emptyList(),1080,2400)
        assertTrue(c.regions.isEmpty());assertFalse(c.chooseRegion(1));assertNull(c.target(1))
        c.start(listOf(target(2,1f,1f)),300,300);c.chooseRegion(1);c.reset()
        assertFalse(c.choosingButtons);assertNull(c.target(1));assertTrue(c.regions.isEmpty())
    }
}
