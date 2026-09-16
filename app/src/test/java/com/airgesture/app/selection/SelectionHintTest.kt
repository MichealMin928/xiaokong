package com.airgesture.app.selection
import org.junit.Assert.*
import org.junit.Test

class SelectionHintTest {
    private fun target(top:Float,bottom:Float)=NumberTarget(1,SelectionBounds(0f,top,100f,bottom))
    @Test fun bottomNavigationNumbersRemainVisible(){
        val top=checkNotNull(selectionHintTop(listOf(target(120f,240f),target(2200f,2360f)),2400f,120f,20f))
        assertTrue(top>=240f);assertTrue(top+120f<2200f)
    }
    @Test fun overlappingCardsDoNotCreateFalseGaps(){
        val top=checkNotNull(selectionHintTop(listOf(target(100f,900f),target(300f,500f),target(1100f,1400f)),1500f,100f,20f))
        assertTrue(top>=900f && top+100f<1100f)
    }
    @Test fun fullyOccupiedScreenRequestsCompactFallback(){
        assertNull(selectionHintTop(listOf(target(0f,2400f)),2400f,120f,20f))
    }
}
