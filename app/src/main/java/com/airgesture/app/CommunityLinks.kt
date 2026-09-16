package com.airgesture.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens only public project pages. No report or device data is put in the URL. */
object CommunityLinks {
    const val PROJECT = "https://github.com/MichealMin928/xiaokong"
    const val ISSUES = "$PROJECT/issues/new/choose"
    fun open(context: Context, url: String) {
        require(url == PROJECT || url == ISSUES)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "请在浏览器打开：$url", Toast.LENGTH_LONG).show()
        }
    }
}
