package com.airgesture.app

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

/** Isolated navigation target: synthetic Back never targets a third-party app. */
class ProofChildActivity:Activity(){
    companion object { var resumed=false;private set }
    override fun onResume(){super.onResume();resumed=true}
    override fun onPause(){resumed=false;super.onPause()}
    override fun onCreate(state:Bundle?){super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(TextView(this).apply{text="本应用返回键验收页面";textSize=24f})
    }
}
