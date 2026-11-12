package QuickControlSettings.brisker.AI

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real RTL activity without granting any special device permissions. */
@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(600) // Allow system bars and activity transitions to settle.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Gradle's device runner can uninstall the app after testing. Keep a shell-owned
        // copy outside its app-specific directory so CI can retrieve the screenshots.
        instrumentation.uiAutomation.executeShellCommand(
            "mkdir -p /sdcard/Download/quickcontrol-qa && cp " + file.absolutePath + " /sdcard/Download/quickcontrol-qa/"
        ).let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() } }
        bitmap.recycle()
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
}
