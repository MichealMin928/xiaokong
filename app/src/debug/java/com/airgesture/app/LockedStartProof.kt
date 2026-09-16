package com.airgesture.app

import android.os.*
import com.airgesture.app.ui.SessionStarter
import com.airgesture.app.service.GlobalSession
import org.json.JSONObject
import java.io.File

/** Shell locks the physical phone during this delay. Never wakes it or starts capture. */
internal class LockedStartProof(private val activity:ActionTestActivity){
    fun start(){
        val file=File(activity.filesDir,"proof-locked-start.json")
        file.writeText(JSONObject().put("passed",false).put("message","WAITING_FOR_SCREEN_OFF").toString())
        Handler(Looper.getMainLooper()).postDelayed({
            val interactive=activity.getSystemService(PowerManager::class.java).isInteractive
            val admitted=if(!interactive)SessionStarter.channels(activity,true,false) else null
            val result=JSONObject().put("passed",!interactive && admitted==false && !GlobalSession.running).put("interactive",interactive).put("admitted",admitted).put("global_running",GlobalSession.running).put("message","Locked launch must be rejected before requesting a foreground service")
            file.writeText(result.toString(2))
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.finish()
        },8000)
    }
}
