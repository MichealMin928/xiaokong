package com.airgesture.app

import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.mode.*
import org.junit.Assert.*
import org.junit.Test

class ProfileDefaultsTest {
    @Test fun freshAndroidPreferencesEnableBundledVideoAndReadingProfiles(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext.createDeviceProtectedStorageContext()
        context.deleteSharedPreferences("assistant_profiles")
        try{
            val profiles=ProfileStore(context).profiles()
            assertEquals(6,profiles.size)
            assertTrue("Default profiles must remain enabled: $profiles",profiles.all{it.enabled})
            assertTrue(ModeRouter.allowed(AssistantMode.VIDEO,"com.xingin.xhs",profiles,true))
        }finally{context.deleteSharedPreferences("assistant_profiles")}
    }
}
