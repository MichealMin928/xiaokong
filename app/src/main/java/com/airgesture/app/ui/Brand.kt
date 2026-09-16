package com.airgesture.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View

object Brand {
    val ink=Color.rgb(20,46,48);val muted=Color.rgb(96,114,111);val green=Color.rgb(16,117,101)
    val background=Color.rgb(245,247,243);val line=Color.rgb(222,230,224);val soft=Color.rgb(230,240,232);val lime=Color.rgb(216,237,177)
    fun surface(context:Context,color:Int=Color.WHITE,radius:Int=22,border:Boolean=false)=GradientDrawable().apply{
        setColor(color);cornerRadius=radius*context.resources.displayMetrics.density
        if(border)setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1),line)
    }
}
enum class AppIcon { VOICE,POINTER,GRID,NUMBERS,HAND,SETTINGS,BACK,SHIELD,STOP,PLAY,RECORD,EYE,CHEVRON }
class IconView(context:Context,private val icon:AppIcon,private val tint:Int=Brand.green):View(context){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    override fun onDraw(canvas:Canvas){super.onDraw(canvas);canvas.save();val contentWidth=width-paddingLeft-paddingRight;val contentHeight=height-paddingTop-paddingBottom;val scale=minOf(contentWidth,contentHeight)/24f;canvas.translate(paddingLeft+(contentWidth-24*scale)/2,paddingTop+(contentHeight-24*scale)/2);canvas.scale(scale,scale);p.color=tint
        fun line(vararg xy:Float){val path=Path();path.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)path.lineTo(xy[i],xy[i+1]);canvas.drawPath(path,p)}
        when(icon){
            AppIcon.VOICE->{canvas.drawRoundRect(9f,3f,15f,15f,3f,3f,p);canvas.drawArc(6f,6f,18f,19f,0f,180f,false,p);line(12f,19f,12f,22f);line(9f,22f,15f,22f)}
            AppIcon.POINTER->line(5f,3f,19f,13f,12f,14f,9f,21f,5f,3f)
            AppIcon.GRID->{canvas.drawRoundRect(3f,3f,21f,21f,3f,3f,p);line(9f,3f,9f,21f);line(15f,3f,15f,21f);line(3f,9f,21f,9f);line(3f,15f,21f,15f)}
            AppIcon.NUMBERS->{line(9f,3f,6f,21f);line(18f,3f,15f,21f);line(3f,9f,21f,9f);line(2f,15f,20f,15f)}
            AppIcon.BACK->line(15f,5f,8f,12f,15f,19f)
            AppIcon.CHEVRON->line(9f,7f,14f,12f,9f,17f)
            AppIcon.STOP->canvas.drawRoundRect(5f,5f,19f,19f,3f,3f,p)
            AppIcon.PLAY->line(8f,4f,20f,12f,8f,20f,8f,4f)
            AppIcon.RECORD->{canvas.drawCircle(12f,12f,8f,p);canvas.drawCircle(12f,12f,3f,p)}
            AppIcon.EYE->{canvas.drawOval(2f,6f,22f,18f,p);canvas.drawCircle(12f,12f,3f,p)}
            AppIcon.SHIELD->{line(12f,2f,20f,6f,19f,15f,12f,22f,5f,15f,4f,6f,12f,2f);line(8f,12f,11f,15f,16f,9f)}
            AppIcon.SETTINGS->{canvas.drawCircle(12f,12f,7f,p);canvas.drawCircle(12f,12f,2.4f,p);for(i in 0..7){canvas.save();canvas.rotate(i*45f,12f,12f);line(12f,2f,12f,5f);canvas.restore()}}
            AppIcon.HAND->{line(7f,12f,7f,5f,10f,5f,10f,12f,10f,3f,13f,3f,13f,12f,13f,5f,16f,5f,16f,13f,16f,8f,19f,8f,19f,16f,16f,21f,10f,21f,4f,14f,4f,11f,7f,12f)}
        };canvas.restore()
    }
}
