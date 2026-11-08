package QuickControlSettings.brisker.AI

import android.app.Activity
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var page: LinearLayout
    private lateinit var audio: AudioManager
    private lateinit var camera: CameraManager
    private lateinit var notifications: NotificationManager
    private var torchId: String? = null
    private var torchEnabled = false
    private val dark get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val secondary get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
    private val accent get() = com.google.android.material.color.MaterialColors.getColor(this, android.R.attr.colorAccent, Color.BLUE)
    private val cardColor get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        camera = getSystemService(CAMERA_SERVICE) as CameraManager
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        torchId = findFlashCamera()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::page.isInitialized) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(this@MainActivity, com.google.android.material.R.attr.colorSurface, Color.WHITE))
            isFillViewport = true
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(28))
        }
        scroll.addView(page)
        setContentView(scroll)

        addText("Quick Control", 25, dark, true)
        addText("שליטה מהירה בהגדרות המכשיר", 13, secondary)
        gap(16)

        heading("תצוגה")
        brightnessCard()
        rotationCard()
        darkModeCard()

        heading("צלילים")
        volumeCard("מדיה", AudioManager.STREAM_MUSIC)
        volumeCard("צלצול", AudioManager.STREAM_RING)
        volumeCard("התראות", AudioManager.STREAM_NOTIFICATION)
        volumeCard("שעון מעורר", AudioManager.STREAM_ALARM)
        ringerCard()
        dndCard()

        heading("כלים")
        torchCard()
        batterySaverCard()
        animationScaleCard()
        stayAwakeCard()
        screenTimeoutCard()
        hapticCard()
        capabilityCard(
            "Wi-Fi, Bluetooth, נקודה חמה, מיקום ו-NFC",
            "ההרשאות המתאימות הוצהרו במניפסט, אך חלק מהפעולות מוגבלות על ידי Android. הרשאות Bluetooth/Wi-Fi מאפשרות פעולות מסוימות כמו סריקה או חיבור, ולא בהכרח הפעלה וכיבוי של המתג הגלובלי. Device Owner של אפליקציה אחרת אינו מעביר אל Quick Control את סמכויות הניהול."
        )

        heading("הרשאות")
        secureSettingsCard()
        capabilityCard(
            "הרשאות מערכת נוספות",
            "הרשאות runtime יידרשו לפי הצורך. WRITE_SETTINGS וגישת נא לא להפריע הן הרשאות מיוחדות שאינן ניתנות באמצעות pm grant רגיל. האפליקציה לא תפתח מסכי מערכת; יש להעניק אותן מראש באמצעות תהליך provisioning או ADB/AppOps מתאים, אם גרסת המכשיר מאפשרת זאת."
        )
        addText(
            "האפליקציה לא תפתח מסכי מערכת. פעולות שאינן מותרות לאפליקציה רגילה יוצגו כלא זמינות, ולא יעמידו פנים שהשינוי בוצע.",
            12, secondary
        )
    }

    private fun heading(label: String) {
        gap(18)
        addText(label, 17, dark, true)
        gap(7)
    }

    private fun card(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), com.google.android.material.color.MaterialColors.getColor(this@MainActivity, com.google.android.material.R.attr.colorOutlineVariant, Color.LTGRAY))
            }
            elevation = dp(1).toFloat()
        }
        val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        p.bottomMargin = dp(8)
        page.addView(box, p)
        return box
    }

    private fun addText(value: String, size: Int, color: Int, bold: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
        }
        page.addView(v)
        return v
    }

    private fun textIn(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
            parent.addView(this)
        }
    }

    private fun gap(h: Int) {
        page.addView(View(this), LinearLayout.LayoutParams(1, dp(h)))
    }

    private fun brightnessCard() {
        val c = card()
        textIn(c, "בהירות מסך", 15, dark, true)
        val canWrite = Settings.System.canWrite(this)
        val value = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val number = TextView(this).apply { text = value.toString(); setTextColor(secondary); textSize = 12f }
        val seek = SeekBar(this).apply { max = 255; progress = value; isEnabled = canWrite }
        line.addView(seek, LinearLayout.LayoutParams(0, dp(42), 1f))
        line.addView(number)
        c.addView(line)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                number.text = p.toString()
                if (user && Settings.System.canWrite(this@MainActivity)) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, p)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        if (!canWrite) {
            textIn(c, "נדרשת הרשאת שינוי הגדרות מערכת.", 12, secondary)
            permissionButton(c, "פתיחת הגדרות ההרשאה") { openWriteSettings() }
        }
    }

    private fun rotationCard() {
        val c = card()
        val allowed = Settings.System.canWrite(this)
        val enabled = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        switch(c, "סיבוב מסך אוטומטי", enabled, allowed, onPermissionMissing = { openWriteSettings() }) { checked ->
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (checked) 1 else 0)
        }
        if (!allowed) {
            textIn(c, "נדרשת הרשאת WRITE_SETTINGS.", 12, secondary)
            permissionButton(c, "פתיחת הגדרות ההרשאה") { openWriteSettings() }
        }
    }

    private fun darkModeCard() {
        val c = card()
        val manager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        switch(c, "ערכת נושא כהה של האפליקציה", isDark, Build.VERSION.SDK_INT >= 31) { checked ->
            if (Build.VERSION.SDK_INT >= 31) {
                manager.setApplicationNightMode(if (checked) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
            }
        }
        textIn(c, "המתג משנה את מראה Quick Control בלבד. שינוי ערכת הנושא של כל המכשיר חסום לאפליקציה רגילה.", 12, secondary)
    }

    private fun volumeCard(label: String, stream: Int) {
        val c = card()
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        val current = audio.getStreamVolume(stream)
        textIn(c, label, 14, dark, true)
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val number = TextView(this).apply { text = "$current/$max"; setTextColor(secondary); textSize = 12f }
        val seek = SeekBar(this).apply { this.max = max; progress = current }
        line.addView(seek, LinearLayout.LayoutParams(0, dp(40), 1f))
        line.addView(number)
        c.addView(line)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                if (user) {
                    audio.setStreamVolume(stream, p, 0)
                    number.text = "$p/$max"
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun ringerCard() {
        val c = card()
        textIn(c, "מצב צלצול", 14, dark, true)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("צלצול" to AudioManager.RINGER_MODE_NORMAL, "רטט" to AudioManager.RINGER_MODE_VIBRATE, "שקט" to AudioManager.RINGER_MODE_SILENT)
            .forEach { (label, mode) ->
                val b = MaterialButton(this).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener {
                        try { audio.ringerMode = mode }
                        catch (_: SecurityException) { toast("המערכת חסמה שינוי מצב צלצול") }
                    }
                }
                row.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
        c.addView(row)
    }

    private fun dndCard() {
        val c = card()
        val granted = notifications.isNotificationPolicyAccessGranted
        val active = notifications.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
        switch(c, "נא לא להפריע", active, granted, onPermissionMissing = { openNotificationPolicySettings() }) { checked ->
            if (notifications.isNotificationPolicyAccessGranted) {
                notifications.setInterruptionFilter(
                    if (checked) NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        }
        if (!granted) {
            textIn(c, "נדרשת גישת מדיניות התראות.", 12, secondary)
            permissionButton(c, "פתיחת הגדרות נא לא להפריע") { openNotificationPolicySettings() }
        }
    }

    private fun torchCard() {
        val c = card()
        val id = torchId
        switch(c, "פנס", torchEnabled, id != null, onPermissionMissing = {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 410)
            } else openAppDetails()
        }) { checked ->
            if (id == null) return@switch
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 410)
            } else {
                try {
                    camera.setTorchMode(id, checked)
                    torchEnabled = checked
                } catch (_: Exception) { toast("לא ניתן לשנות את מצב הפנס") }
            }
        }
        if (id == null) textIn(c, "במכשיר לא זוהה פנס LED.", 12, secondary)
    }


    private fun hasSecureSettings(): Boolean =
        checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun batterySaverCard() {
        val c = card()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        val granted = hasSecureSettings()
        switch(c, "חיסכון בסוללה", power.isPowerSaveMode, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                val written = Settings.Global.putInt(contentResolver, "low_power", if (checked) 1 else 0)
                if (!written) {
                    toast("הכתיבה להגדרה נכשלה")
                    render()
                } else {
                    // The system observes this setting asynchronously; verify after its observer runs.
                    Handler(Looper.getMainLooper()).postDelayed({
                        val setting = Settings.Global.getInt(contentResolver, "low_power", 0) == 1
                        val active = power.isPowerSaveMode
                        when {
                            setting && active == checked -> toast(if (checked) "חיסכון בסוללה הופעל" else "חיסכון בסוללה כובה")
                            else -> toast("ההגדרה נכתבה, אך המערכת לא החילה את מצב החיסכון")
                        }
                        render()
                    }, 500)
                }
            } catch (_: SecurityException) {
                toast("נדרשת הרשאת WRITE_SECURE_SETTINGS")
                render()
            } catch (_: Exception) {
                toast("לא ניתן לשנות את מצב חיסכון בסוללה במכשיר זה")
                render()
            }
        }
        textIn(c, "נכתב דרך Settings.Global (low_power), והמצב נבדק דרך PowerManager. ייתכן שמדיניות היצרן תחסום את הפעולה.", 12, secondary)
        if (!granted) {
            textIn(c, "ההרשאה הזו אינה ניתנת לאישור ידני במסך ההגדרות הרגיל. יש להעניק אותה באמצעות ADB מהמחשב.", 12, secondary)
            permissionButton(c, "העתקת פקודת הענקת הרשאה") {
                copyToClipboard("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
                toast("פקודת ADB הועתקה")
            }
        }
    }

    private fun animationScaleCard() {
        val c = card()
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val granted = hasSecureSettings()
        val disabled = try { Settings.Global.getFloat(contentResolver, keys[0], 1f) == 0f } catch (_: Exception) { false }
        switch(c, "ביטול אנימציות מערכת", disabled, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                val value = if (checked) 0f else 1f
                val results = keys.map { Settings.Global.putFloat(contentResolver, it, value) }
                if (results.all { it }) toast(if (checked) "האנימציות בוטלו" else "האנימציות הוחזרו")
                else toast("חלק מהגדרות האנימציה לא עודכנו")
            } catch (_: SecurityException) { toast("נדרשת WRITE_SECURE_SETTINGS") }
            render()
        }
        textIn(c, "משנה את שלושת סולמות האנימציה של Android.", 12, secondary)
    }

    private fun stayAwakeCard() {
        val c = card()
        val granted = hasSecureSettings()
        val mask = try { Settings.Global.getInt(contentResolver, "stay_on_while_plugged_in", 0) } catch (_: Exception) { 0 }
        switch(c, "השאר מסך דולק בזמן טעינה", mask != 0, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                Settings.Global.putInt(contentResolver, "stay_on_while_plugged_in", if (checked) 7 else 0)
            } catch (_: SecurityException) { toast("נדרשת WRITE_SECURE_SETTINGS") }
            render()
        }
        textIn(c, "חל על USB, מטען AC וטעינה אלחוטית.", 12, secondary)
    }

    private fun screenTimeoutCard() {
        val c = card()
        val allowed = Settings.System.canWrite(this)
        val options = listOf(15000, 30000, 60000, 120000, 300000, 600000)
        val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
        val index = options.indexOf(current).let { if (it >= 0) it else 2 }
        textIn(c, "כיבוי מסך אוטומטי", 14, dark, true)
        val button = MaterialButton(this).apply {
            text = "משך נוכחי: " + timeoutLabel(current) + " · הקש לשינוי"
            isAllCaps = false
            isEnabled = allowed
            setOnClickListener {
                val next = options[(index + 1) % options.size]
                Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, next)
                render()
            }
        }
        c.addView(button)
        if (!allowed) {
            textIn(c, "נדרשת הרשאת שינוי הגדרות מערכת.", 12, secondary)
            permissionButton(c, "פתיחת הגדרות ההרשאה") { openWriteSettings() }
        }
    }

    private fun timeoutLabel(ms: Int): String = when {
        ms < 60000 -> "${ms / 1000} שניות"
        ms % 60000 == 0 -> "${ms / 60000} דקות"
        else -> "${ms / 1000} שניות"
    }

    private fun hapticCard() {
        val c = card()
        val allowed = Settings.System.canWrite(this)
        val enabled = Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        switch(c, "משוב רטט למגע", enabled, allowed, onPermissionMissing = { openWriteSettings() }) { checked ->
            Settings.System.putInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (checked) 1 else 0)
        }
    }

    private fun capabilityCard(title: String, explanation: String) {
        val c = card()
        textIn(c, title, 14, dark, true)
        textIn(c, explanation, 12, secondary)
    }

    private fun secureSettingsCard() {
        val c = card()
        textIn(c, "WRITE_SECURE_SETTINGS", 14, dark, true)
        textIn(c, "WRITE_SECURE_SETTINGS מאפשרת כתיבה לחלק מהגדרות Secure ו-Global, אך אינה root ואינה עוקפת כל מגבלת מערכת או יצרן. הרשאות מיוחדות אחרות עדיין נדרשות בנפרד.", 12, secondary)
        val command = """
            adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS
            adb shell pm grant $packageName android.permission.CAMERA
            adb shell pm grant $packageName android.permission.ACCESS_COARSE_LOCATION
            adb shell pm grant $packageName android.permission.ACCESS_FINE_LOCATION
            adb shell pm grant $packageName android.permission.ACCESS_BACKGROUND_LOCATION
            adb shell pm grant $packageName android.permission.BLUETOOTH_CONNECT
            adb shell pm grant $packageName android.permission.BLUETOOTH_SCAN
            adb shell pm grant $packageName android.permission.BLUETOOTH_ADVERTISE
            adb shell pm grant $packageName android.permission.NEARBY_WIFI_DEVICES
            adb shell pm grant $packageName android.permission.POST_NOTIFICATIONS
            adb shell appops set $packageName WRITE_SETTINGS allow
            adb shell cmd notification allow_dnd $packageName
        """.trimIndent()
        val b = MaterialButton(this).apply {
            text = "העתקת פקודת ADB"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB", command))
                toast("הפקודה הועתקה")
            }
        }
        c.addView(b)
    }

    private fun switch(
        parent: LinearLayout,
        label: String,
        checked: Boolean,
        enabled: Boolean,
        onPermissionMissing: (() -> Unit)? = null,
        action: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = textIn(row, label, 14, dark, true)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val control = MaterialSwitch(this).apply {
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, value -> action(value) }
        }
        row.addView(control)
        parent.addView(row)
        if (!enabled && onPermissionMissing != null) {
            permissionButton(parent, "הענקת הרשאה / פתיחת הגדרות") { onPermissionMissing() }
        }
    }

    private fun permissionButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        openSettingsIntent(intent)
    }

    private fun openNotificationPolicySettings() {
        openSettingsIntent(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    private fun openAppDetails() {
        openSettingsIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun openSettingsIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            try {
                openAppDetails()
            } catch (_: Exception) {
                toast("לא ניתן לפתוח את מסך ההרשאה במכשיר הזה")
            }
        }
    }

    private fun secureSettingsHelp() {
        copyToClipboard("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        toast("ההרשאה מחייבת ADB. הפקודה הועתקה ללוח.")
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Quick Control", value))
    }

    private fun findFlashCamera(): String? = try {
        camera.cameraIdList.firstOrNull { id ->
            val info = camera.getCameraCharacteristics(id)
            info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) { null }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
