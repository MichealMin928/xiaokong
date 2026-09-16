package com.airgesture.app.training

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import org.json.JSONObject

/** Skeleton replay, with the same upright mirrored coordinates as the preview. */
class TrainingReplayView(context:Context):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var points=emptyList<Pair<Float,Float>>()
    private var aspect=1f
    private val edges=listOf(0 to 1,1 to 2,2 to 3,3 to 4,0 to 5,5 to 6,6 to 7,7 to 8,5 to 9,9 to 10,10 to 11,11 to 12,9 to 13,13 to 14,14 to 15,15 to 16,13 to 17,17 to 18,18 to 19,19 to 20,0 to 17)
    fun show(row:JSONObject){
        val a=row.optJSONArray("landmarks")
        points=runCatching{if(a==null)emptyList() else (0 until a.length()).map{val p=a.getJSONArray(it);(1-p.getDouble(0).toFloat()) to p.getDouble(1).toFloat()}}.getOrDefault(emptyList())
        aspect=row.optInt("width",480).toFloat()/row.optInt("height",640).coerceAtLeast(1)
        invalidate()
    }
    override fun onDraw(canvas:Canvas){
        canvas.drawColor(Color.rgb(24,35,43))
        if(points.size!=21)return
        val h=minOf(height.toFloat(),width/aspect);val w=h*aspect;val ox=(width-w)/2;val oy=(height-h)/2
        fun x(i:Int)=ox+points[i].first*w
        fun y(i:Int)=oy+points[i].second*h
        paint.color=Color.CYAN;paint.strokeWidth=3f
        edges.forEach{(a,b)->canvas.drawLine(x(a),y(a),x(b),y(b),paint)}
        points.indices.forEach{paint.color=if(it==4)Color.YELLOW else Color.WHITE;canvas.drawCircle(x(it),y(it),5f,paint)}
    }
}
