package com.airgesture.app

import android.content.Intent
import android.os.*
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.airgesture.app.accessibility.AccessibilityBridge
import com.airgesture.app.command.*
import com.airgesture.app.mode.AssistantMode
import com.airgesture.app.selection.*
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.session.*
import com.airgesture.app.voice.*
import org.json.*

/** DUMP-protected, own test buttons only. Acoustic mode never injects a keyword or command. */
class SelectionTestActivity:ComponentActivity(){
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var root:LinearLayout
    private lateinit var primary:Button
    private lateinit var ticker:TextView
    private lateinit var scroller:ScrollView
    private var clicks=0
    private var done=false
    private val checks=JSONArray()
    private val begin=SystemClock.uptimeMillis()
    private val acoustic get()=intent.getBooleanExtra("acoustic",false)
    private val observer=androidx.lifecycle.Observer<ControlStatus>{s->
        val arr=JSONArray();s.choices.forEach{t->arr.put(JSONObject().put("number",t.number).put("bounds",JSONArray(listOf(t.bounds.left,t.bounds.top,t.bounds.right,t.bounds.bottom))))}
        val data=JSONObject().put("mode",s.mode.name).put("phase",s.selectionPhase).put("digits_ready",s.audio.digitsActive).put("audio",s.audio.state)
            .put("audio_ms",s.audio.audioMs).put("input_db",s.audio.inputLevelDb).put("peak_db",s.audio.inputPeakDb).put("decodes",s.audio.inferenceCount)
            .put("keyword",s.lastKeyword).put("decision",s.lastDecision).put("choices",arr).put("clicks",clicks)
        java.io.File(filesDir,"selection-live.json").writeText(data.toString(2))
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24);setBackgroundColor(0xfff4f6fb.toInt())}
        ticker=TextView(this).apply{text="本机按钮选择验收 · 无第三方操作";textSize=18f};root.addView(ticker,LinearLayout.LayoutParams(-1,150))
        primary=Button(this).apply{text="测试按钮 A";setOnClickListener{clicks++;record("primary_click",true)}};root.addView(primary,LinearLayout.LayoutParams(-1,180))
        root.addView(TextView(this).apply{text="可聚焦文字，不是按钮";isFocusable=true},LinearLayout.LayoutParams(-1,100))
        root.addView(Button(this).apply{text="禁用按钮，不应标号";isEnabled=false},LinearLayout.LayoutParams(-1,140))
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(0xffdae6f5.toInt());setOnClickListener{record("card_click",true)}}
        card.addView(TextView(this).apply{text="可点击卡片（保留父目标）"},LinearLayout.LayoutParams(-1,130))
        card.addView(Button(this).apply{text="卡片内的小按钮";setOnClickListener{record("child_click",true)}},LinearLayout.LayoutParams(380,160))
        val scrollContent=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scrollContent.addView(card,LinearLayout.LayoutParams(-1,410));scrollContent.addView(View(this),LinearLayout.LayoutParams(-1,240))
        scroller=ScrollView(this).apply{addView(scrollContent)};root.addView(scroller,LinearLayout.LayoutParams(-1,410))
        root.addView(View(this),LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply{text="底部测试按钮";setOnClickListener{record("bottom_click",true)}},LinearLayout.LayoutParams(-1,180))
        setContentView(root)
        ControlSessionState.status.observeForever(observer)
        stopService(Intent(this,GestureForegroundService::class.java))
        later(1200){
            check(AccessibilityBridge.available)
            ContextCompat.startForegroundService(this,Intent(this,GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START)
                .putExtra(GestureForegroundService.EXTRA_MODE,AssistantMode.GLOBAL.name).putExtra(GestureForegroundService.EXTRA_CONTROL,true).putExtra(GestureForegroundService.EXTRA_VOICE,true))
            ready{if(acoustic)record("acoustic_ready_no_injection",true) else automatic()}
        }
    }
    private fun record(name:String,passed:Boolean){checks.put(JSONObject().put("check",name).put("passed",passed).put("clicks",clicks).put("elapsed_ms",SystemClock.uptimeMillis()-begin));write()}
    private fun write(){java.io.File(filesDir,"selection-proof.json").writeText(JSONObject().put("done",done).put("acoustic",acoustic).put("checks",checks).toString(2))}
    private fun later(ms:Long,block:()->Unit){handler.postDelayed({if(!done)try{block()}catch(e:Exception){record(e.message ?: e.javaClass.simpleName,false);done=true;write();stopService(Intent(this,GestureForegroundService::class.java))}},ms)}
    private fun ready(block:()->Unit){check(SystemClock.uptimeMillis()-begin<25000){"Microphone start deadline"};if(ControlSessionState.current.audio.state=="ACTIVE")block() else later(250){ready(block)}}
    private fun keyword(word:String){check(hasWindowFocus());ControlSessionState.keywordHandler?.invoke(KeywordEvent(word,SystemClock.uptimeMillis(),1f,0)) ?: error("No keyword handler")}
    private fun select(n:Int){check(hasWindowFocus());check(ControlSessionState.request(ControlCommand(CommandKind.SELECT_NUMBER,SystemClock.uptimeMillis(),number=n)))}
    private fun numberFor(view:View):Int {val p=IntArray(2);view.getLocationOnScreen(p);return ControlSessionState.current.choices.single{it.bounds.left==p[0].toFloat() && it.bounds.top==p[1].toFloat() && it.bounds.width==view.width.toFloat()}.number}
    private fun automatic(){
        keyword("显示按钮")
        later(1100){
            check(ControlSessionState.current.mode==ControlMode.NUMBER_SELECT){"Direct entry failed"}
            check(ControlSessionState.current.choices.size==4){"Expected four actionable targets, got ${ControlSessionState.current.choices.size}"}
            record("direct_entry_and_actionable_only_parent_child_retained",true)
            ticker.text="非按钮文字更新，不应清除编号"
            later(900){
                check(ControlSessionState.current.mode==ControlMode.NUMBER_SELECT){"Unrelated update cleared selection"};record("unrelated_update_retains_numbers",true)
                select(numberFor(primary))
                later(850){
                    check(clicks==1 && ControlSessionState.current.mode==ControlMode.VOICE_ONLY){"Native click failed"};record("native_number_click",true)
                    root.addView(Button(this).apply{text="稍后加载的按钮";setOnClickListener{record("loaded_click",true)}},LinearLayout.LayoutParams(-1,90))
                    // Allow the new view to be laid out before requesting its screen bounds.
                    later(350){keyword("显示按钮")}
                    later(1200){
                        check(ControlSessionState.current.choices.size==5){"Newly loaded target missing from cached tree"};record("new_content_after_idle_is_scanned",true)
                        val n=numberFor(primary);primary.text="同一位置的新内容"
                        later(650){select(n);later(850){
                            check(clicks==1 && ControlSessionState.current.mode==ControlMode.VOICE_ONLY){"Stale target clicked"};record("changed_identity_same_bounds_rejected",true)
                            keyword("屏幕分区")
                            later(1100){
                                val s=ControlSessionState.current;check(s.selectionPhase=="REGIONS" && s.choices.size in 2..8){"Occupied regions failed"}
                                select((1..9).first{n->s.choices.none{it.number==n}})
                                later(500){
                                    check(clicks==1 && ControlSessionState.current.selectionPhase=="REGIONS");record("empty_region_cannot_click",true)
                                    val p=IntArray(2);primary.getLocationOnScreen(p);val x=p[0]+primary.width/2f;val y=p[1]+primary.height/2f
                                    select(s.choices.first{x>=it.bounds.left && x<it.bounds.right && y>=it.bounds.top && y<it.bounds.bottom}.number)
                                    later(850){
                                        check(clicks==1 && ControlSessionState.current.selectionPhase=="BUTTONS");record("region_choice_does_not_click",true)
                                        select(numberFor(primary))
                                        later(850){
                                            check(clicks==2);record("region_button_native_click",true);keyword("显示按钮")
                                            later(850){scroller.scrollTo(0,100);later(850){
                                                check(ControlSessionState.current.mode==ControlMode.VOICE_ONLY){"Actual ScrollView scroll failed to invalidate"};record("scroll_invalidates",true)
                                                done=true;write();stopService(Intent(this,GestureForegroundService::class.java))
                                            }}
                                        }
                                    }
                                }
                            }
                        }}
                    }
                }
            }
        }
    }
    override fun onDestroy(){done=true;handler.removeCallbacksAndMessages(null);ControlSessionState.status.removeObserver(observer);super.onDestroy()}
}
