package com.airgesture.app.mode

import android.content.Context
import org.json.JSONObject

class ProfileStore(context:Context) {
    private val prefs=context.getSharedPreferences("assistant_profiles",Context.MODE_PRIVATE)
    var mode:AssistantMode
        get()=AssistantMode.entries.firstOrNull{it.name==prefs.getString("mode",null)} ?: AssistantMode.MOUSE
        set(value){prefs.edit().putString("mode",value.name).apply()}
    var autoModes:Boolean
        get()=prefs.getBoolean("auto_modes",false)
        set(value){prefs.edit().putBoolean("auto_modes",value).apply()}
    var mouseWhitelist:Boolean
        get()=prefs.getBoolean("mouse_whitelist",false)
        set(value){prefs.edit().putBoolean("mouse_whitelist",value).apply()}
    fun profiles():List<AppProfile> {
        val custom=prefs.getStringSet("custom_packages",emptySet()).orEmpty().map{AppProfile(it,prefs.getString("name_$it",it) ?: it)}
        return (AppProfile.presets+custom).distinctBy{it.packageName}.map{default->
            try {
                val json=JSONObject(prefs.getString(default.packageName,"{}") ?: "{}")
                default.copy(enabled=json.optBoolean("enabled",default.enabled),
                    mode=AssistantMode.entries.firstOrNull{it.name==json.optString("mode")} ?: default.mode,
                    gazeEnabled=json.optBoolean("gaze",true),gazeDwellMs=json.optLong("dwell",550).coerceIn(350,1000),
                    handFallback=json.optBoolean("fallback",false),
                    mappings=default.mappings+GestureInput.entries.associateWith { input->
                        MappedAction.entries.firstOrNull{it.name==json.optString(input.name)} ?: default.mappings.getValue(input)
                    })
            }catch(_:Exception){default.copy(enabled=false)}
        }
    }
    fun save(profile:AppProfile){
        val json=JSONObject().put("enabled",profile.enabled).put("mode",profile.mode.name)
            .put("gaze",profile.gazeEnabled).put("dwell",profile.gazeDwellMs).put("fallback",profile.handFallback)
        profile.mappings.forEach{(k,v)->json.put(k.name,v.name)}
        val edit=prefs.edit().putString(profile.packageName,json.toString())
        if(AppProfile.presets.none{it.packageName==profile.packageName})edit.putStringSet("custom_packages",prefs.getStringSet("custom_packages",emptySet()).orEmpty()+profile.packageName).putString("name_${profile.packageName}",profile.name)
        edit.apply()
    }
}
