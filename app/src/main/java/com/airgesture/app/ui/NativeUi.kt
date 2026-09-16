package com.airgesture.app.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.view.*
import com.airgesture.app.HomeActivity

fun Activity.uiDp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()
fun Activity.page(title:String,scrollable:Boolean=false):LinearLayout {
    WindowCompat.setDecorFitsSystemWindows(window,false)
    @Suppress("DEPRECATION")
    window.statusBarColor=Color.TRANSPARENT
    @Suppress("DEPRECATION")
    window.navigationBarColor=Brand.background
    if(android.os.Build.VERSION.SDK_INT>=29)window.isStatusBarContrastEnforced=false
    WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}
    val column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Brand.background)}
    ViewCompat.setOnApplyWindowInsetsListener(column){v,i->val bars=i.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout());v.setPadding(bars.left+uiDp(20),bars.top+uiDp(12),bars.right+uiDp(20),bars.bottom+uiDp(12));i}
    val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,0,0,uiDp(14))}
    if(this !is HomeActivity)header.addView(IconView(this,AppIcon.BACK).apply{contentDescription="返回";isFocusable=true;setPadding(uiDp(10),uiDp(10),uiDp(10),uiDp(10));setOnClickListener{finish()}},LinearLayout.LayoutParams(uiDp(48),uiDp(48)))
    header.addView(TextView(this).apply{text=title;textSize=24f;setTextColor(Brand.ink);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},LinearLayout.LayoutParams(0,-2,1f))
    if(this is HomeActivity)header.addView(IconView(this,AppIcon.SETTINGS).apply{contentDescription="设置";isFocusable=true;setPadding(uiDp(12),uiDp(12),uiDp(12),uiDp(12));setOnClickListener{startActivity(android.content.Intent(this@page,com.airgesture.app.SettingsActivity::class.java))}},LinearLayout.LayoutParams(uiDp(48),uiDp(48)))
    column.addView(header);setContentView(column);ViewCompat.requestApplyInsets(column)
    if(!scrollable)return column
    val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    column.addView(ScrollView(this).apply{isFillViewport=true;isVerticalScrollBarEnabled=false;addView(content)},LinearLayout.LayoutParams(-1,0,1f));return content
}
fun Activity.button(parent:LinearLayout,label:String,action:()->Unit):Button=Button(this).apply{
    text=label;textSize=15f;isAllCaps=false;setTextColor(Brand.ink);background=Brand.surface(this@button,radius=16,border=true)
    minHeight=uiDp(54);setPadding(uiDp(16),uiDp(12),uiDp(16),uiDp(12));parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply{topMargin=uiDp(10)});setOnClickListener{action()}
}
fun Activity.primaryButton(parent:LinearLayout,label:String,action:()->Unit)=button(parent,label,action).apply{setTextColor(Color.WHITE);background=Brand.surface(this@primaryButton,Brand.green,16)}
fun Activity.note(parent:LinearLayout,label:String):TextView=TextView(this).apply{text=label;textSize=14f;setTextColor(Brand.muted);setLineSpacing(uiDp(4).toFloat(),1f);setPadding(0,uiDp(12),0,uiDp(12));parent.addView(this)}
fun Activity.section(parent:LinearLayout,label:String)=note(parent,label).apply{textSize=18f;setTextColor(Brand.ink);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);setPadding(0,uiDp(24),0,uiDp(12))}
fun Activity.feature(parent:LinearLayout,title:String,subtitle:String,icon:AppIcon,action:()->Unit):LinearLayout {
    val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=Brand.surface(this@feature,radius=20);setPadding(uiDp(16),uiDp(18),uiDp(12),uiDp(18));isFocusable=true;contentDescription="$title，$subtitle";setOnClickListener{action()}}
    row.addView(IconView(this,icon).apply{background=Brand.surface(this@feature,Brand.soft,13);setPadding(uiDp(10),uiDp(10),uiDp(10),uiDp(10))},LinearLayout.LayoutParams(uiDp(44),uiDp(44)))
    val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(uiDp(14),0,uiDp(8),0)}
    labels.addView(TextView(this).apply{text=title;textSize=16f;setTextColor(Brand.ink);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)})
    labels.addView(TextView(this).apply{text=subtitle;textSize=12f;setTextColor(Brand.muted);setPadding(0,uiDp(5),0,0);setLineSpacing(uiDp(3).toFloat(),1f)})
    row.addView(labels,LinearLayout.LayoutParams(0,-2,1f));row.addView(IconView(this,AppIcon.CHEVRON,Brand.muted),LinearLayout.LayoutParams(uiDp(20),uiDp(20)))
    parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=uiDp(10)});return row
}

/** Avoid redundant accessibility content-change events from status refreshes. */
fun TextView.updateTextIfChanged(value:CharSequence?){if(text?.toString()!=value?.toString())text=value}

fun ownNumberSelectionActive()=com.airgesture.app.session.ControlSessionState.current.let{it.running && it.mode in setOf(com.airgesture.app.session.ControlMode.NUMBER_SELECT,com.airgesture.app.session.ControlMode.GRID_SELECT)}
