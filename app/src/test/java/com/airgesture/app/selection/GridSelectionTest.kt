package com.airgesture.app.selection
import org.junit.Assert.*
import org.junit.Test
class GridSelectionTest {
    @Test fun all81CentersCoverDistinctCells(){
        val centers=mutableSetOf<Pair<Float,Float>>()
        for(a in 1..9)for(b in 1..9){val g=GridSelectionController();assertNull(g.select(a));val p=checkNotNull(g.select(b));assertTrue(p.x>0 && p.x<1 && p.y>0 && p.y<1);centers.add(p.x to p.y)
            val col=((a-1)%3)*3+(b-1)%3;val row=((a-1)/3)*3+(b-1)/3
            assertEquals((col+.5f)/9,p.x,.00001f);assertEquals((row+.5f)/9,p.y,.00001f)
        };assertEquals(81,centers.size)
    }
    @Test fun invalidNumberNeverAdvancesAndResetClearsRefinement(){val g=GridSelectionController();assertNull(g.select(0));assertEquals(0,g.level);g.select(5);assertNull(g.select(20));assertEquals(1,g.level);g.reset();assertEquals(0,g.level);assertEquals(SelectionBounds(0f,0f,1f,1f),g.bounds)}
}
