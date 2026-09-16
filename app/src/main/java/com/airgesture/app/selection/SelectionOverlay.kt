package com.airgesture.app.selection

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*

/** Read-only overlay. Every badge refers to a real actionable node or an occupied region. */
class SelectionOverlay(context:Context):AutoCloseable {
    private val manager=context.getSystemService(WindowManager::class.java)
    private val density=context.resources.displayMetrics.density
    private var targets=emptyList<NumberTarget>()
    private var regions=emptyList<TargetRegion>()
    private var regionMode=false
    private var ready=false
    private var attached=false
    private val view=object:View(context){
        override fun getAccessibilityClassName():CharSequence=WINDOW_CLASS
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){
            paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=Color.rgb(10,150,120)
            for(t in targets){val b=t.bounds;c.drawRoundRect(b.left,b.top,b.right,b.bottom,5*density,5*density,paint)}
            if(regionMode){
                for(r in regions){
                    val b=r.bounds;paint.style=Paint.Style.FILL;paint.color=Color.argb(24,10,150,120);c.drawRect(b.left,b.top,b.right,b.bottom,paint)
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;paint.color=Color.rgb(47,211,172);c.drawRect(b.left,b.top,b.right,b.bottom,paint)
                    badge(c,r.number,b.center.x,b.center.y)
                    paint.color=Color.rgb(11,60,55);c.drawRoundRect(b.center.x-42*density,b.center.y+17*density,b.center.x+42*density,b.center.y+39*density,8*density,8*density,paint)
                    paint.color=Color.WHITE;paint.textSize=12*resources.displayMetrics.scaledDensity
                    c.drawText("${r.count} 个按钮",b.center.x,b.center.y+32*density,paint)
                }
            }else for(t in targets){
                val b=t.bounds
                badge(c,t.number,(b.left+17*density).coerceIn(18*density,width-18*density),(b.top+15*density).coerceIn(24*density,height-24*density))
            }
            label(c,if(!ready)"正在准备数字识别，请稍候…" else if(regionMode)"说区域号，再选按钮 · 取消退出" else "例如说「点击一号」· 取消退出")
        }
        fun badge(c:Canvas,n:Int,x:Float,y:Float){
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(11,60,55);c.drawRoundRect(x-16*density,y-14*density,x+16*density,y+14*density,9*density,9*density,paint)
            paint.color=Color.WHITE;paint.textSize=17*resources.displayMetrics.scaledDensity;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textAlign=Paint.Align.CENTER
            c.drawText(n.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
        }
        fun label(c:Canvas,text:String){
            val free=selectionHintTop(targets,height.toFloat(),42*density,8*density)
            val top=free ?: 0f;val h=(if(free==null)24 else 42)*density
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(11,60,55);c.drawRoundRect(16*density,top,width-16*density,top+h,12*density,12*density,paint)
            paint.color=Color.WHITE;paint.textSize=(if(free==null)11 else 13)*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER
            while(paint.measureText(text)>width-44*density && paint.textSize>9*density)paint.textSize-=density
            c.drawText(text,width/2f,top+h/2-(paint.ascent()+paint.descent())/2,paint)
        }
    }.apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO;setWillNotDraw(false)}
    private fun attach(){if(attached)return
        val flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT).apply{
            gravity=Gravity.TOP or Gravity.LEFT;title="小空按钮选择";alpha=.65f
            if(Build.VERSION.SDK_INT>=30){setFitInsetsTypes(0);layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS}
        };manager.addView(view,p);attached=true
    }
    fun numbers(items:List<NumberTarget>){regionMode=false;regions=emptyList();targets=items;attach();view.invalidate()}
    fun regions(items:List<TargetRegion>,buttons:List<NumberTarget>){regionMode=true;regions=items;targets=buttons;attach();view.invalidate()}
    fun ready(value:Boolean){if(ready!=value){ready=value;view.invalidate()}}
    override fun close(){if(attached){manager.removeViewImmediate(view);attached=false};targets=emptyList();regions=emptyList();ready=false}
    companion object {const val WINDOW_CLASS="com.airgesture.app.selection.SelectionOverlay"}
}
