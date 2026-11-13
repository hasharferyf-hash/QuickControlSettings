package QuickControlSettings.brisker.AI

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real RTL activity without granting any special device permissions. */
@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(1000) // Allow system bars and activity transitions to settle.
        // Use individual shell commands: UiAutomation is not a shell parser. Store
        // captures outside app storage so Gradle's uninstall step cannot remove them.
        fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
        }
        shell("mkdir -p /data/local/tmp/quickcontrol-qa")
        val file = "/data/local/tmp/quickcontrol-qa/$name.png"
        val captureOutput = shell("screencap -p $file")
        assertTrue("Screenshot failed: $captureOutput", captureOutput.isBlank())
        val size = shell("stat -c %s $file").trim().toLongOrNull() ?: 0L
        assertTrue("Screenshot is empty: $file", size > 0)
    }

    @Test fun dashboardSurvivesThemeAndFontChanges() {
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("dark", false).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val all = views(activity.window.decorView)
                    assertTrue(all.filterIsInstance<TextView>().any { it.text.toString() == "הכול בלחיצה" })
                    assertTrue(all.filterIsInstance<QuickTile>().size >= 16)
                    assertTrue(all.filterIsInstance<QuickTile>().all { it.isClickable && it.isFocusable && !it.contentDescription.isNullOrBlank() })
                    assertFalse(all.filterIsInstance<TextView>().any { it.text.contains("WRITE_SECURE_SETTINGS") })
                    assertEquals(View.LAYOUT_DIRECTION_RTL, all.filterIsInstance<QuickTile>().first().layoutDirection)
                }
                screenshot("01-light")
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().first().fullScroll(View.FOCUS_DOWN)
                }
                screenshot("04-shortcuts")
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<android.widget.ScrollView>().first().scrollTo(0, 0)
                }
                scenario.onActivity { activity ->
                    views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString().startsWith("עוד צלילים") }.performClick()
                }
                scenario.onActivity { activity ->
                    assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "מתי המסך ייכבה?" })
                }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "מתי המסך ייכבה?" })
                    views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString().startsWith("עוד צלילים") }.performClick()
                }
                preferences.edit().putBoolean("dark", true).commit()
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                }
                screenshot("02-dark")
                instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.5").let {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
                }
                instrumentation.waitForIdleSync()
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertTrue(activity.resources.configuration.fontScale >= 1.5f)
                    val tiles = views(activity.window.decorView).filterIsInstance<QuickTile>()
                    assertTrue(tiles.all { it.width > 0 && it.height > 0 })
                }
                screenshot("03-large-text")
            }
        } finally {
            instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0").let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
            preferences.edit().clear().commit()
        }
    }
    @Test fun repeatedBatteryUpdatesKeepControlsAndScrollAttached() {
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                Thread.sleep(350)
                lateinit var scroll: android.widget.ScrollView
                lateinit var tile: QuickTile
                lateinit var slider: android.widget.SeekBar
                var position = 0
                scenario.onActivity { activity ->
                    val all = views(activity.window.decorView)
                    scroll = all.filterIsInstance<android.widget.ScrollView>().first()
                    tile = all.filterIsInstance<QuickTile>().first()
                    slider = all.filterIsInstance<android.widget.SeekBar>().last()
                    // Set the test position immediately; fullScroll animates asynchronously.
                    scroll.scrollTo(0, scroll.getChildAt(0).height)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { position = scroll.scrollY; assertTrue(position > 0) }
                for (level in 86..89) {
                    shell("dumpsys battery set level $level")
                    Thread.sleep(400) // Past the broadcast coalescing interval.
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val all = views(activity.window.decorView)
                        assertSame("Battery update replaced the scroll view", scroll, all.filterIsInstance<android.widget.ScrollView>().first())
                        assertSame("Battery update replaced the tile", tile, all.filterIsInstance<QuickTile>().first())
                        assertSame("Battery update replaced the slider", slider, all.filterIsInstance<android.widget.SeekBar>().last())
                        assertEquals("Battery update moved the page", position, scroll.scrollY)
                        assertTrue(all.filterIsInstance<TextView>().any { it.text.contains("$level%") })
                    }
                }
                // A radio/settings callback arriving mid-gesture must keep the active slider.
                scenario.onActivity {
                    slider.requestFocus()
                    val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, (slider.width / 2).toFloat(), (slider.height / 2).toFloat(), 0)
                    slider.onTouchEvent(event)
                    event.recycle()
                    val move = android.view.MotionEvent.obtain(0, 10, android.view.MotionEvent.ACTION_MOVE, (slider.width * 3 / 4).toFloat(), (slider.height / 2).toFloat(), 0)
                    slider.onTouchEvent(move)
                    move.recycle()
                }
                shell("dumpsys battery set level 90")
                Thread.sleep(400)
                scenario.onActivity { activity ->
                    assertSame(slider, views(activity.window.decorView).filterIsInstance<android.widget.SeekBar>().last())
                    val event = android.view.MotionEvent.obtain(0, 10, android.view.MotionEvent.ACTION_UP, (slider.width / 2).toFloat(), (slider.height / 2).toFloat(), 0)
                    slider.onTouchEvent(event)
                    event.recycle()
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.contains("90%") })
                }
            }
        } finally { shell("dumpsys battery reset") }
    }

}

