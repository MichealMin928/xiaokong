package com.airgesture.app.accessibility

/** Normalized coordinates, independent of display size. Existing defaults are preserved. */
data class SwipeConfig(val startX:Float,val startY:Float,val endX:Float,val endY:Float,val duration:Long=280){
    val valid get()=listOf(startX,startY,endX,endY).all{it.isFinite() && it in .05f.. .95f} && duration in 120..600
    companion object {
        fun forAction(action:ActionKind,verticalDistance:Float=.48f,horizontalDistance:Float=.50f,duration:Long=280):SwipeConfig?{
            val v=verticalDistance.coerceIn(.24f,.70f)/2;val h=horizontalDistance.coerceIn(.24f,.70f)/2
            val ms=duration.coerceIn(120,600)
            return when(action){
                ActionKind.SWIPE_UP->SwipeConfig(.5f,.5f+v,.5f,.5f-v,ms)
                ActionKind.SWIPE_DOWN->SwipeConfig(.5f,.5f-v,.5f,.5f+v,ms)
                ActionKind.SWIPE_LEFT->SwipeConfig(.5f+h,.5f,.5f-h,.5f,ms)
                ActionKind.SWIPE_RIGHT->SwipeConfig(.5f-h,.5f,.5f+h,.5f,ms)
                else->null
            }
        }
    }
}
