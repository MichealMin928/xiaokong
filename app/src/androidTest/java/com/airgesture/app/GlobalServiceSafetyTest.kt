package com.airgesture.app

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.service.GlobalSession
import com.airgesture.app.service.GlobalStatus
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs before the user's overlay grant. Does not change any permission or acquire a camera. */
@RunWith(AndroidJUnit4::class)
class GlobalServiceSafetyTest {
    @Test fun deniedOverlayStopsServiceWithoutStartingCamera() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeFalse("Run this before the user authorizes overlays", Settings.canDrawOverlays(context))
        val stopped = CountDownLatch(1)
        val observer = Observer<GlobalStatus> {
            if (!it.running && it.message.contains("启动失败")) stopped.countDown()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                GlobalSession.status.observeForever(observer)
                ContextCompat.startForegroundService(it,
                    Intent(it, GestureForegroundService::class.java).setAction(GestureForegroundService.ACTION_START))
            }
            try {
                assertTrue("Denied permission must shut down promptly", stopped.await(8, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertFalse(GlobalSession.running)
                    assertFalse(GlobalSession.log.export().contains("global_started=1"))
                    assertTrue(GlobalSession.log.export().contains("global_start_failed="))
                    val notifications = it.getSystemService(NotificationManager::class.java).activeNotifications
                    assertFalse(notifications.any { notice -> notice.id == 6 })
                }
                @Suppress("DEPRECATION")
                val remaining = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(100)
                assertFalse(remaining.any { it.service.className == GestureForegroundService::class.java.name })
            } finally {
                instrumentation.runOnMainSync { GlobalSession.status.removeObserver(observer) }
                context.stopService(Intent(context, GestureForegroundService::class.java))
            }
        }
    }
}
