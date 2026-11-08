package QuickControlSettings.brisker.AI

import android.app.Activity
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var audio: AudioManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var cameraManager: CameraManager
    private var torchOn = false
    private var torchCameraId: String? = null
    private var refreshing = false

    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val blue = Color.rgb(56, 103, 214)
    private val surface = Color.WHITE
    private val page = Color.rgb(244, 246, 250)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        torchCameraId = findBackCameraWithFlash()
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) refreshDynamicControls()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(page)
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(22), dp(18), dp(30))
        }
        scroll.addView(root)
        setContentView(scroll)

        text(root, "Quick Control Settings", 25, ink, true)
        text(root, "מרכז שליטה מהיר להגדרות היומיומיות של המכשיר", 14, muted)
        spacer(root, 18)

        section("תצוגה")
        brightnessControl()
        rotationControl()
        actionRow("מצב כהה", "פתיחת הגדרות תצוגה של Android") {
            openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }
        actionRow("זמן כיבוי מסך", "הגדרת משך הזמן עד לכיבוי המסך") {
            openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }

        section("צלילים והתראות")
        volumeControl("עוצמת מדיה", AudioManager.STREAM_MUSIC)
        volumeControl("עוצמת צלצול", AudioManager.STREAM_RING)
        volumeControl("עוצמת התראות", AudioManager.STREAM_NOTIFICATION)
        volumeControl("עוצמת שעון מעורר", AudioManager.STREAM_ALARM)
        ringerControl()
        dndControl()

        section("כלים מהירים")
        torchControl()
        batterySaverControl()
        actionRow("Wi-Fi ואינטרנט", "פתיחת לוח בקרת חיבורי הרשת") {
            if (Build.VERSION.SDK_INT >= 29) openSettings("android.settings.panel.action.INTERNET_CONNECTIVITY")
            else openSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        actionRow("Bluetooth", "ניהול חיבורי Bluetooth") {
            if (Build.VERSION.SDK_INT >= 29) openSettings("android.settings.panel.action.BLUETOOTH")
            else openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        actionRow("נקודה חמה", "הגדרות שיתוף אינטרנט") {
            openSettings(Settings.ACTION_TETHER_SETTINGS)
        }
        actionRow("מיקום", "הגדרות שירותי מיקום") {
            openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
        actionRow("NFC", "הגדרות תקשורת לטווח קצר") {
            openSettings(Settings.ACTION_NFC_SETTINGS)
        }
        actionRow("חיסכון בנתונים", "הגדרות שימוש בנתונים") {
            openSettings(Settings.ACTION_DATA_USAGE_SETTINGS)
        }

        section("הרשאות והגדרות מתקדמות")
        actionRow("הדרכה: הרשאת WRITE_SECURE_SETTINGS", "הצגת פקודת ADB והסבר") {
            showSecureSettingsGuide()
        }
        actionRow("הרשאת שינוי הגדרות מערכת", "נדרשת לבהירות, סיבוב מסך וחלק מההגדרות") {
            requestWriteSettingsAccess()
        }
        actionRow("הרשאת מדיניות נא לא להפריע", "נדרשת לשליטה ישירה במצב נא לא להפריע") {
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
        text(root, "הערה: Android מגבילה חלק מהפעולות לאפליקציות רגילות. כאשר שינוי ישיר אינו מותר, האפליקציה פותחת את מסך ההגדרה המתאים.", 12, muted)
    }

    private fun section(title: String) {
        spacer(root, 20)
        text(root, title, 18, ink, true)
        spacer(root, 8)
    }

    private fun brightnessControl() {
        val card = card()
        text(card, "בהירות מסך", 16, ink, true)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val value = text(row, "—", 13, muted)
        val seek = SeekBar(this).apply { max = 255 }
        row.addView(seek, LinearLayout.LayoutParams(0, dp(42), 1f))
        card.addView(row)
        val canWrite = Settings.System.canWrite(this)
        val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        seek.progress = current
        value.text = "$current"
        seek.isEnabled = canWrite
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                value.text = "$progress"
                if (fromUser && Settings.System.canWrite(this@MainActivity)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        if (!canWrite) button(card, "מתן הרשאה לשינוי הגדרות מערכת") { requestWriteSettingsAccess() }
        root.addView(card)
    }

    private fun rotationControl() {
        val card = card()
        val enabled = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        switchRow(card, "סיבוב מסך אוטומטי", enabled, Settings.System.canWrite(this)) { checked ->
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (checked) 1 else 0)
            } else {
                requestWriteSettingsAccess()
            }
        }
        if (!Settings.System.canWrite(this)) button(card, "נדרשת הרשאת שינוי הגדרות מערכת") { requestWriteSettingsAccess() }
        root.addView(card)
    }

    private fun volumeControl(label: String, stream: Int) {
        val card = card()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val current = audio.getStreamVolume(stream)
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        text(row, label, 15, ink, true)
        val value = text(row, "$current/$max", 12, muted)
        card.addView(row)
        val seek = SeekBar(this).apply {
            this.max = max
            progress = current
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audio.setStreamVolume(stream, progress, 0)
                    value.text = "$progress/$max"
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        card.addView(seek)
        root.addView(card)
    }

    private fun ringerControl() {
        val card = card()
        val modes = listOf(
            "צלצול" to AudioManager.RINGER_MODE_NORMAL,
            "רטט" to AudioManager.RINGER_MODE_VIBRATE,
            "שקט" to AudioManager.RINGER_MODE_SILENT
        )
        text(card, "מצב צלצול", 15, ink, true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modes.forEach { (label, mode) ->
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    try {
                        audio.ringerMode = mode
                        Toast.makeText(this@MainActivity, "מצב: $label", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(this@MainActivity, "אין הרשאה לשינוי מצב הצלצול במכשיר זה", Toast.LENGTH_LONG).show()
                    }
                }
            }
            row.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        card.addView(row)
        root.addView(card)
    }

    private fun dndControl() {
        val card = card()
        val granted = notificationManager.isNotificationPolicyAccessGranted
        val active = notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        switchRow(card, "נא לא להפריע", active, granted) { checked ->
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            } else {
                notificationManager.setInterruptionFilter(
                    if (checked) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        }
        if (!granted) button(card, "הענקת גישה למדיניות התראות") {
            openSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
        root.addView(card)
    }

    private fun torchControl() {
        val card = card()
        switchRow(card, "פנס", torchOn, torchCameraId != null) { checked ->
            val id = torchCameraId
            if (id == null) {
                Toast.makeText(this, "לא נמצא פנס במכשיר", Toast.LENGTH_SHORT).show()
            } else if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 410)
            } else {
                try {
                    cameraManager.setTorchMode(id, checked)
                    torchOn = checked
                } catch (e: Exception) {
                    Toast.makeText(this, "לא ניתן לשנות את מצב הפנס: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(card)
    }

    private fun batterySaverControl() {
        val card = card()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        text(card, if (power.isPowerSaveMode) "חיסכון בסוללה פעיל" else "חיסכון בסוללה אינו פעיל", 15, ink, true)
        button(card, "פתיחת הגדרות חיסכון בסוללה") {
            openSettings(if (Build.VERSION.SDK_INT >= 29) Settings.ACTION_BATTERY_SAVER_SETTINGS else Settings.ACTION_SETTINGS)
        }
        root.addView(card)
    }

    private fun actionRow(title: String, subtitle: String, action: () -> Unit) {
        val card = card()
        text(card, title, 15, ink, true)
        text(card, subtitle, 12, muted)
        button(card, "פתיחה") { action() }
        root.addView(card)
    }

    private fun switchRow(parent: LinearLayout, label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val labelView = text(row, label, 15, ink, true)
        labelView.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val toggle = Switch(this).apply {
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(toggle)
        parent.addView(row)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(15), dp(12), dp(15), dp(12))
        setBackgroundColor(surface)
        elevation = dp(1).toFloat()
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(9)
        layoutParams = params
    }

    private fun text(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        parent.addView(view)
        return view
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(blue)
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun spacer(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun refreshDynamicControls() {
        // The screen is rebuilt on resume so permission changes and external system changes are reflected.
        if (!refreshing) {
            refreshing = true
            val y = (findViewById<ScrollView>(android.R.id.content)?.scrollY) ?: 0
            buildScreen()
            refreshing = false
        }
    }

    private fun findBackCameraWithFlash(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestWriteSettingsAccess() {
        if (Build.VERSION.SDK_INT >= 23) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun showSecureSettingsGuide() {
        val command = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val message = "הרשאת WRITE_SECURE_SETTINGS אינה הרשאת Android רגילה ולכן לא ניתן לבקש אותה בחלון הרשאות רגיל.\n\n" +
            "1. הפעילו אפשרויות למפתחים במכשיר.\n" +
            "2. הפעילו ניפוי USB.\n" +
            "3. חברו את המכשיר למחשב עם ADB ואשרו את טביעת המפתח.\n" +
            "4. הריצו את הפקודה הבאה:\n\n$command\n\n" +
            "5. נתקו וחזרו לאפליקציה.\n\n" +
            "הפקודה מעניקה רק את ההרשאה הזו. היא אינה עוקפת הרשאות מיוחדות אחרות או מגבלות של יצרן המכשיר."
        android.app.AlertDialog.Builder(this)
            .setTitle("הפעלת WRITE_SECURE_SETTINGS")
            .setMessage(message)
            .setPositiveButton("העתקת פקודה") { _, _ ->
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", command))
                Toast.makeText(this, "הפקודה הועתקה", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("סגירה", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
