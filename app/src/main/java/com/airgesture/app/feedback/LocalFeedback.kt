package com.airgesture.app.feedback

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Deliberately local: no endpoint, raw audio, screen content, or accessibility tree. */
class LocalFeedback(context:Context){
    private val directory=File(context.filesDir,"feedback").apply{mkdirs()}
    fun save(report:JSONObject,id:String=UUID.randomUUID().toString()):String {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        val target=File(directory,"$id.json");val temporary=File(directory,"$id.tmp")
        temporary.writeText(report.put("id",id).put("version",com.airgesture.app.BuildConfig.VERSION_NAME).put("delivery","LOCAL_ONLY").toString(2))
        check(temporary.renameTo(target)){"无法保存问题记录"}
        files().drop(100).forEach{it.delete()}
        return id
    }
    fun files()=directory.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()} ?: emptyList()
}
