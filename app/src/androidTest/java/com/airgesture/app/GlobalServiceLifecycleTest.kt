package com.airgesture.app

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.airgesture.app.service.GestureForegroundService
import com.airgesture.app.service.GlobalSession
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the user's existing grants; exercises real capture, Home and notification PendingIntents. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class GlobalServiceLifecycleTest {
    @Test fun cameraContinuesAtHomeAndNotificationActionsReleaseOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("User must authorize overlays first", Settings.canDrawOverlays(context))
        fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun awaitCondition(label: String, check: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 8_000
            while (SystemClock.uptimeMillis() < deadline) {
                var ready = false
                onMain { ready = check() }
                if (ready) return
                SystemClock.sleep(50)
            }
            fail(label)
        }
        fun cursorWindow() = WindowInspector.getGlobalWindowViews().firstOrNull {
            (it.layoutParams as? WindowManager.LayoutParams)?.title == "AirGestureCursor"
        }
        fun frameCount() = GlobalSession.log.export().lineSequence().count { it.startsWith("global_frame ") }
        val notifications = context.getSystemService(NotificationManager::class.java)
        val profileStore=com.airgesture.app.mode.ProfileStore(context)
        val originalWhitelist=profileStore.mouseWhitelist
        profileStore.mouseWhitelist=false
        try { ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                var initialFrames=0
                onMain { initialFrames=frameCount() }
                scenario.onActivity {
                    ContextCompat.startForegroundService(it, Intent(it, GestureForegroundService::class.java)
                        .setAction(GestureForegroundService.ACTION_START))
                }
                awaitCondition("Capture must produce a new real frame") { frameCount() > initialFrames }
                val sizePattern = Regex("global_started=1 width=(\\d+) height=(\\d+)")
                val sizes = sizePattern.findAll(GlobalSession.log.export()).last().groupValues
                val portraitWidth = sizes[1].toInt()
                val portraitHeight = sizes[2].toInt()
                assumeTrue("Rotation test starts in portrait", portraitHeight > portraitWidth)
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                awaitCondition("Overlay must read landscape dimensions") {
                    GlobalSession.log.export().contains("width=$portraitHeight height=$portraitWidth")
                }
                var rotatedFrames = 0
                onMain { rotatedFrames = frameCount() }
                awaitCondition("Activity recreation must not interrupt service capture") { frameCount() >= rotatedFrames + 2 }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                awaitCondition("Overlay must return to portrait dimensions") {
                    GlobalSession.log.export().lineSequence().any {
                        it.startsWith("global_display ") && it.contains("width=$portraitWidth height=$portraitHeight")
                    }
                }
                var beforeHome = 0
                onMain {
                    assertTrue(GlobalSession.running)
                    assertNotNull(cursorWindow())
                    beforeHome = frameCount()
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                awaitCondition("Capture must continue outside the Activity") { frameCount() >= beforeHome + 2 }
                onMain {
                    notifications.activeNotifications.first { it.id == 6 }.notification.actions[0].actionIntent.send()
                }
                awaitCondition("Notification pause must reach the service") { GlobalSession.status.value?.paused == true }
                onMain { assertEquals(View.INVISIBLE, cursorWindow()?.visibility) }
                onMain { notifications.activeNotifications.first { it.id == 6 }.notification.actions[0].actionIntent.send() }
                awaitCondition("Notification continue must reach the service") { GlobalSession.status.value?.paused == false }
                onMain { notifications.activeNotifications.first { it.id == 6 }.notification.actions[1].actionIntent.send() }
                awaitCondition("Stop must release the overlay and notification") {
                    !GlobalSession.running && cursorWindow() == null && notifications.activeNotifications.none { it.id == 6 }
                }
            } finally {
                context.stopService(Intent(context, GestureForegroundService::class.java))
            }
        } } finally { profileStore.mouseWhitelist=originalWhitelist }
    }
}
