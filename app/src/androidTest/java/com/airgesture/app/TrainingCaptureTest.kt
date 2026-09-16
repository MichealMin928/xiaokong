package com.airgesture.app

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.training.TrainingRecord
import com.airgesture.app.service.GlobalSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrainingCaptureTest {
    @Test fun numericalRecordRoundTripsMissingFramesAndPartialTail(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"training-codec-test.jsonl")
        val points=JSONArray().apply{repeat(21){put(JSONArray().put(.5).put(.6).put(0))}}
        val row=JSONObject().put("type","frame").put("accepted",false).put("landmarks",points).put("t",1200).put("cursor",JSONObject.NULL)
        TrainingRecord(file,JSONObject().put("instruction","codec test")).use{it.append(row)}
        file.appendText("{partial")
        val read=TrainingRecord.read(file)
        assertEquals(2,read.size);assertEquals(21,read[1].getJSONArray("landmarks").length())
        assertNull(TrainingRecord.observation(read[1]));assertEquals(1,read[0].getInt("schema"))
        file.delete()
    }
    @Test fun guidedSegmentRecordsCameraMetadataAndStopsWithoutSystemControl(){
        val instrument=InstrumentationRegistry.getInstrumentation();val context=instrument.targetContext
        val folder=File(context.filesDir,"training");val before=folder.walkTopDown().filter{it.extension=="jsonl"}.map{it.absolutePath}.toSet()
        val activity=instrument.startActivitySync(Intent(context,TrainingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TrainingActivity
        fun nodes(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap{nodes(view.getChildAt(it))} else emptyList())
        try{
            instrument.runOnMainSync{
                val start=nodes(activity.window.decorView).filterIsInstance<Button>().single{it.text.startsWith("开始本段")}
                assertTrue(start.performClick())
            }
            var completed=false
            val deadline=SystemClock.uptimeMillis()+22000
            while(SystemClock.uptimeMillis()<deadline){
                SystemClock.sleep(250)
                instrument.runOnMainSync{completed=nodes(activity.window.decorView).filterIsInstance<Button>().any{it.isEnabled && it.text.startsWith("确认按提示")}}
                if(completed)break
            }
            assertTrue("Guided six-second clip should finish and save",completed)
            assertFalse(GlobalSession.actionsEnabled)
            val created=folder.walkTopDown().filter{it.extension=="jsonl" && it.absolutePath !in before}.toList()
            assertEquals(1,created.size)
            val rows=TrainingRecord.read(created.single());val frames=rows.filter{it.optString("type")=="frame" && it.optString("phase")=="record"}
            assertTrue(frames.size>=5)
            assertTrue(frames.all{it.has("landmarks") && it.has("cursor") && it.has("age_ms") && it.has("action")})
            val summary=rows.last();assertFalse(summary.getBoolean("interrupted"));assertFalse(summary.getBoolean("system_actions"))
            // This is an automated camera plumbing check, never a labelled user-performance sample.
            val archive=File(context.filesDir,"training-self-test").apply{mkdirs()}
            assertTrue(created.single().renameTo(File(archive,created.single().name)))
            File(archive,"result.json").writeText(JSONObject().put("passed",true).put("record_frames",frames.size).put("user_gesture_acceptance",false).put("system_actions",false).toString(2))
        }finally{instrument.runOnMainSync{activity.finish()}}
    }
}
