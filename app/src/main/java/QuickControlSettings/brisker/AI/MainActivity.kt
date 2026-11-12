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
import android.os.BatteryManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.nfc.NfcAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private var detailsExpanded = false
    private var advancedExpanded = false
    private var scrollPosition = 0
    private var activeScroll: ScrollView? = null
    private var torchAvailable = true
    private var monitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { if (!isFinishing && !isDestroyed) render() }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchEnabled = enabled
                torchAvailable = true
                scheduleRefresh()
            }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchId) { torchAvailable = false; scheduleRefresh() }
        }
    }
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { scheduleRefresh() }
    }
    private val dark get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val secondary get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
    private val accent get() = Color.rgb(49, 109, 244)
    private val cardColor get() = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, Color.WHITE)

    override fun attachBaseContext(newBase: Context) {
        val preferences = newBase.getSharedPreferences("appearance", MODE_PRIVATE)
        if (preferences.contains("dark")) {
            val config = Configuration(newBase.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (preferences.getBoolean("dark", false)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 27) {
            window.decorView.systemUiVisibility = if (isAppDark()) 0 else
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            // Android 8.0 has white navigation icons; keep them readable in the light theme.
            window.navigationBarColor = Color.BLACK
        }
        detailsExpanded = savedInstanceState?.getBoolean("details") ?: false
        advancedExpanded = savedInstanceState?.getBoolean("advanced") ?: false
        scrollPosition = savedInstanceState?.getInt("scroll") ?: 0
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        camera = getSystemService(CAMERA_SERVICE) as CameraManager
        notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        torchId = findFlashCamera()
        render()
    }

    override fun onResume() {
        super.onResume()
        torchId = findFlashCamera()
        monitoring = true
        try { camera.registerTorchCallback(torchCallback, handler) } catch (_: Exception) { }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
        render()
    }

    override fun onPause() {
        monitoring = false
        handler.removeCallbacksAndMessages(null)
        camera.unregisterTorchCallback(torchCallback)
        unregisterReceiver(stateReceiver)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("details", detailsExpanded)
        outState.putBoolean("advanced", advancedExpanded)
        outState.putInt("scroll", activeScroll?.scrollY ?: scrollPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 410) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleTorch()
            else {
                toast("בלי גישה למצלמה אי אפשר להדליק את הפנס. אפשר לאשר דרך הגדרות האפליקציה.")
                render()
            }
        }
    }

    private fun scheduleRefresh() {
        if (monitoring) { handler.removeCallbacks(refresh); handler.postDelayed(refresh, 150) }
    }

    private fun render() {
        scrollPosition = activeScroll?.scrollY ?: scrollPosition
        val scroll = ScrollView(this).apply {
            setBackgroundColor(if (isAppDark()) Color.rgb(18, 21, 28) else Color.rgb(242, 245, 251))
            isFillViewport = true
            clipToPadding = false
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        activeScroll = scroll
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(page)
        setContentView(scroll)
        scroll.requestApplyInsets()
        topBar()
        quickControls()
        gap(10)
        brightnessCard()
        volumeCard("עוצמת מדיה", AudioManager.STREAM_MUSIC)
        sectionButton("עוד צלילים והגדרות מסך", detailsExpanded) {
            detailsExpanded = !detailsExpanded; render()
        }
        if (detailsExpanded) {
            volumeCard("צלצול", AudioManager.STREAM_RING)
            volumeCard("התראות", AudioManager.STREAM_NOTIFICATION)
            volumeCard("שעון מעורר", AudioManager.STREAM_ALARM)
            screenTimeoutCard()
            hapticCard()
            autoBrightnessCard()
        }
        heading("קיצורים שימושיים")
        addText("פותחים ישר את ההגדרה שצריך", 13, secondary)
        shortcuts()
        sectionButton("כלים מתקדמים", advancedExpanded) {
            advancedExpanded = !advancedExpanded; render()
        }
        if (advancedExpanded) {
            capabilityCard("לפני שמתחילים", "הכלים כאן צריכים אישור חד־פעמי דרך מחשב. גם אחרי האישור, חלק מהמכשירים לא מאפשרים את כל השינויים.")
            globalDarkModeCard()
            batterySaverCard()
            animationScaleCard()
            stayAwakeCard()
            secureSettingsCard()
        }
        scroll.post {
            scroll.scrollTo(0, scrollPosition)
            if (Build.VERSION.SDK_INT >= 30) {
                val lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (isAppDark()) 0 else lightBars, lightBars)
            }
        }
    }

    private fun sectionButton(title: String, expanded: Boolean, action: () -> Unit) {
        val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = title + if (expanded) "  −" else "  +"
            isAllCaps = false
            cornerRadius = dp(20)
            minimumHeight = dp(56)
            contentDescription = title + if (expanded) ", פתוח, לחיצה לסגירה" else ", סגור, לחיצה לפתיחה"
            setOnClickListener { action() }
        }
        page.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private data class Tile(
        val label: String, val status: String, val icon: Int,
        val active: Boolean = false, val toggle: Boolean = false, val action: () -> Unit
    )

    private fun tileGrid(tiles: List<Tile>) {
        val columns = if (resources.configuration.screenWidthDp >= 600) 6
            else if (resources.configuration.fontScale >= 1.3f || resources.configuration.screenWidthDp < 360) 3 else 4
        tiles.chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { tile ->
                row.addView(QuickTile(this, tile.label, tile.status, tile.icon, tile.active, tile.toggle,
                    dark, secondary, cardColor, accent, tile.action), LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            page.addView(row)
        }
    }

    private fun quickControls() {
        val write = Settings.System.canWrite(this)
        val rotation = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
        val wifi = (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled
        val location = try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
            else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
        // Read the device setting, not this app's automatic DND rule (Android 15+).
        val dnd = Settings.Global.getInt(contentResolver, "zen_mode", 0) != 0
        tileGrid(listOf(
            Tile("Wi-Fi", if (wifi) "פועל · פתיחה" else "כבוי · פתיחה", R.drawable.ic_wifi, wifi) {
                openShortcut(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIFI_SETTINGS)
            },
            Tile("Bluetooth", "פתיחת הגדרות", R.drawable.ic_bluetooth) { openShortcut(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Tile("פנס", when { torchId == null -> "אין במכשיר"; !torchAvailable -> "לא זמין כרגע"; torchEnabled -> "דולק"; else -> "כבוי" }, R.drawable.ic_flashlight, torchEnabled, true) { toggleTorch() },
            Tile("מצב צליל", when (audio.ringerMode) { AudioManager.RINGER_MODE_NORMAL -> "צלצול"; AudioManager.RINGER_MODE_VIBRATE -> "רטט"; else -> "שקט" }, R.drawable.ic_sound, silent, true) { cycleRinger() },
            Tile("סיבוב מסך", if (!write) "צריך אישור" else if (rotation) "אוטומטי" else "נעול", R.drawable.ic_rotation, rotation, true) {
                if (!write) openWriteSettings() else {
                    try { Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotation) 0 else 1) }
                    catch (_: SecurityException) { openWriteSettings() }
                    render()
                }
            },
            Tile("לא להפריע", if (dnd) "פועל · פתיחה" else "כבוי · פתיחה", R.drawable.ic_dnd, dnd) {
                openShortcut("android.settings.ZEN_MODE_SETTINGS", Settings.ACTION_SOUND_SETTINGS)
            },
            Tile("מיקום", if (location) "פועל · פתיחה" else "כבוי · פתיחה", R.drawable.ic_location, location) { openShortcut(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
            Tile("חיסכון בסוללה", if ((getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode) "פועל · פתיחה" else "פתיחת הגדרות", R.drawable.ic_battery,
                (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode) { openShortcut(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
        ))
    }

    private fun shortcuts() {
        val tiles = mutableListOf(
            Tile("אינטרנט", "פתיחת הגדרות", R.drawable.ic_internet) { openShortcut(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS) },
            Tile("מצב טיסה", "פתיחת הגדרות", R.drawable.ic_airplane) { openShortcut(Settings.ACTION_AIRPLANE_MODE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS) },
            Tile("שיתוף מסך", "פתיחת הגדרות", R.drawable.ic_cast) { openShortcut(Settings.ACTION_CAST_SETTINGS) },
            Tile("אחסון", "פתיחת הגדרות", R.drawable.ic_storage) { openShortcut(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
            Tile("שעון מעורר", "פתיחת השעון", R.drawable.ic_alarm) { openShortcut(android.provider.AlarmClock.ACTION_SHOW_ALARMS, Settings.ACTION_DATE_SETTINGS) },
            Tile("נגישות", "פתיחת הגדרות", R.drawable.ic_accessibility) { openShortcut(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            Tile("גודל טקסט", "פתיחת הגדרות", R.drawable.ic_text_size) { openShortcut(Settings.ACTION_DISPLAY_SETTINGS) },
            Tile("הגדרות", "כל ההגדרות", R.drawable.ic_settings) { openShortcut(Settings.ACTION_SETTINGS) }
        )
        @Suppress("DEPRECATION")
        if (NfcAdapter.getDefaultAdapter(this) != null) tiles.add(0,
            Tile("NFC", "פתיחת הגדרות", R.drawable.ic_nfc) { openShortcut(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS, Settings.ACTION_NFC_SETTINGS) })
        tileGrid(tiles)
    }

    private fun toggleTorch() {
        val id = torchId ?: return toast("לא מצאנו פנס במכשיר הזה")
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (getPreferences(MODE_PRIVATE).getBoolean("cameraAsked", false) &&
                !shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                openAppDetails()
            } else {
                getPreferences(MODE_PRIVATE).edit().putBoolean("cameraAsked", true).apply()
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 410)
            }
            return
        }
        try {
            camera.setTorchMode(id, !torchEnabled)
            // The torch callback updates the tile only after the camera confirms the change.
        } catch (_: Exception) { toast("הפנס לא זמין כרגע. אולי המצלמה פתוחה?") }
    }

    private fun cycleRinger() {
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try { audio.ringerMode = next }
        catch (_: SecurityException) { openNotificationPolicySettings() }
        render()
    }

    /** OEMs may omit an activity; try the standard destination, then a useful fallback. */
    private fun openShortcut(action: String, fallback: String = Settings.ACTION_SETTINGS) {
        for (candidate in listOf(action, fallback, Settings.ACTION_SETTINGS).distinct()) {
            try { startActivity(Intent(candidate)); return }
            catch (_: android.content.ActivityNotFoundException) { }
            catch (_: SecurityException) { }
        }
        toast("לא הצלחנו לפתוח את ההגדרות במכשיר הזה")
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
                cornerRadius = dp(28).toFloat()
            }
            elevation = 0f
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
        val number = TextView(this).apply { text = "${value * 100 / 255}%"; setTextColor(secondary); textSize = 12f }
        val seek = SeekBar(this).apply { max = 255; progress = value; isEnabled = canWrite; contentDescription = "בהירות מסך"; progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent) }
        line.addView(seek, LinearLayout.LayoutParams(0, dp(48), 1f))
        line.addView(number)
        c.addView(line)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                number.text = "${p * 100 / 255}%"
                if (user && Settings.System.canWrite(this@MainActivity)) {
                    try {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, p.coerceAtLeast(1))
                    } catch (_: SecurityException) { openWriteSettings() }
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { render() }
        })
        if (!canWrite) {
            textIn(c, "כדי לשנות מכאן, צריך לאפשר לאפליקציה לעדכן את הגדרות המסך.", 12, secondary)
            permissionButton(c, "בואו נאפשר את זה") { openWriteSettings() }
        }
    }

    private fun isAppDark(): Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun topBar() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        textIn(row, "Quick Control", 13, secondary, true).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val about = MaterialButton(this).apply {
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(accent)
            text = "אודות"
            isAllCaps = false
            minimumHeight = dp(48)
            setOnClickListener { showAbout() }
        }
        row.addView(about)
        val theme = MaterialButton(this).apply {
            text = ""
            contentDescription = if (isAppDark()) "מעבר למראה בהיר באפליקציה" else "מעבר למראה כהה באפליקציה"
            setIconResource(if (isAppDark()) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
            iconTint = ColorStateList.valueOf(dark)
            backgroundTintList = ColorStateList.valueOf(cardColor)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(24)
            setOnClickListener {
                getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark", !isAppDark()).apply()
                recreate()
            }
        }
        row.addView(theme, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(row)
        gap(24)
        addText("הכול בלחיצה", 34, dark, true)
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val date = SimpleDateFormat("EEEE, d בMMMM", Locale.forLanguageTag("he")).format(Date())
        addText(date + if (level >= 0 && scale > 0) "  ·  סוללה ${level * 100 / scale}%" else "", 13, secondary)
        gap(18)
    }

    private fun showAbout() {
        android.app.AlertDialog.Builder(this)
            .setTitle("קצת על Quick Control")
            .setMessage("ההגדרות שצריך ביום־יום, במקום אחד.\n\nפותח על ידי @הבריסקער-רב\nפורום מתמחים טופ")
            .setPositiveButton("סגירה", null)
            .show()
    }

    private fun globalDarkModeCard() {
        val c = card()
        val granted = hasSecureSettings()
        val isDark = (applicationContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        switch(c, "מראה כהה בכל המכשיר", isDark, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                val mode = if (checked) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
                val written = Settings.Secure.putInt(contentResolver, "ui_night_mode", mode)
                if (!written) {
                    toast("השינוי לא נשמר. אפשר לנסות שוב.")
                    render()
                } else {
                    handler.postDelayed({
                        val savedMode = Settings.Secure.getInt(contentResolver, "ui_night_mode", -1)
                        val activeDark = (applicationContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                        if (savedMode == mode && activeDark == checked) {
                            toast(if (checked) "מצב כהה הופעל במכשיר" else "מצב בהיר הופעל במכשיר")
                        } else {
                            toast("המכשיר לא הפעיל את השינוי. אפשר לשנות דרך הגדרות התצוגה.")
                        }
                        render()
                    }, 700)
                }
            } catch (_: SecurityException) {
                toast("צריך אישור חד־פעמי דרך מחשב כדי להשתמש באפשרות הזו")
                render()
            } catch (_: Exception) {
                toast("המכשיר הזה לא מאפשר לשנות מכאן את המראה שלו")
                render()
            }
        }
        textIn(c, "מחליף בין מראה בהיר לכהה בכל המכשיר. לא עובד בכל הדגמים.", 12, secondary)
        if (!granted) {
            textIn(c, "כדי להפעיל מכאן, צריך קודם לתת אישור דרך מחשב.", 12, secondary)
            permissionButton(c, "העתקת הפקודה למחשב") { secureSettingsHelp() }
        }
    }

    private fun volumeCard(label: String, stream: Int) {
        val c = card()
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        val current = audio.getStreamVolume(stream)
        textIn(c, label, 14, dark, true)
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val number = TextView(this).apply { text = "$current/$max"; setTextColor(secondary); textSize = 12f }
        val seek = SeekBar(this).apply { this.max = max; progress = current; contentDescription = label; progressTintList = ColorStateList.valueOf(accent); thumbTintList = ColorStateList.valueOf(accent) }
        line.addView(seek, LinearLayout.LayoutParams(0, dp(48), 1f))
        line.addView(number)
        c.addView(line)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                if (user) {
                    try { audio.setStreamVolume(stream, p, 0) }
                    catch (_: SecurityException) {
                        toast("כדי לשנות את הצליל הזה, צריך לאפשר גישה למצב לא להפריע")
                        openNotificationPolicySettings()
                    }
                    val actual = audio.getStreamVolume(stream)
                    number.text = "$actual/$max"
                    if (actual != p) s?.progress = actual
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
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
                    toast("השינוי לא נשמר. אפשר לנסות שוב.")
                    render()
                } else {
                    // The system observes this setting asynchronously; verify after its observer runs.
                    handler.postDelayed({
                        val setting = Settings.Global.getInt(contentResolver, "low_power", 0) == 1
                        val active = power.isPowerSaveMode
                        when {
                            setting == checked && active == checked -> toast(if (checked) "חיסכון בסוללה הופעל" else "חיסכון בסוללה כובה")
                            else -> toast("המכשיר לא הפעיל את השינוי. אפשר לפתוח את הגדרות הסוללה.")
                        }
                        render()
                    }, 500)
                }
            } catch (_: SecurityException) {
                toast("צריך אישור חד־פעמי דרך מחשב כדי להשתמש באפשרות הזו")
                render()
            } catch (_: Exception) {
                toast("לא ניתן לשנות את מצב חיסכון בסוללה במכשיר זה")
                render()
            }
        }
        textIn(c, "בחלק מהמכשירים אפשר לשנות את זה רק דרך הגדרות הסוללה.", 12, secondary)
        if (!granted) {
            textIn(c, "כאן צריך אישור דרך מחשב, רק פעם אחת.", 12, secondary)
            permissionButton(c, "העתקת הפקודה למחשב") {
                copyToClipboard("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
                toast("הפקודה הועתקה")
            }
        }
    }

    private fun animationScaleCard() {
        val c = card()
        val keys = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val granted = hasSecureSettings()
        val disabled = try { Settings.Global.getFloat(contentResolver, keys[0], 1f) == 0f } catch (_: Exception) { false }
        switch(c, "פחות תנועה במסך", disabled, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                val value = if (checked) 0f else 1f
                val results = keys.map { Settings.Global.putFloat(contentResolver, it, value) }
                if (results.all { it }) toast(if (checked) "האנימציות בוטלו" else "האנימציות הוחזרו")
                else toast("חלק מהגדרות האנימציה לא עודכנו")
            } catch (_: SecurityException) { toast("צריך קודם לתת אישור דרך מחשב") }
            render()
        }
        textIn(c, "מבטל את אפקטי המעבר בין מסכים. בכיבוי חוזרים למהירות הרגילה.", 12, secondary)
    }

    private fun stayAwakeCard() {
        val c = card()
        val granted = hasSecureSettings()
        val mask = try { Settings.Global.getInt(contentResolver, "stay_on_while_plugged_in", 0) } catch (_: Exception) { 0 }
        switch(c, "מסך דולק בזמן טעינה", mask != 0, granted, onPermissionMissing = { secureSettingsHelp() }) { checked ->
            try {
                Settings.Global.putInt(contentResolver, "stay_on_while_plugged_in", if (checked) 7 else 0)
            } catch (_: SecurityException) { toast("צריך קודם לתת אישור דרך מחשב") }
            render()
        }
        textIn(c, "המסך יישאר דולק כשהמכשיר מחובר למטען.", 12, secondary)
    }

    private fun screenTimeoutCard() {
        val c = card()
        val allowed = Settings.System.canWrite(this)
        val options = listOf(15000, 30000, 60000, 120000, 300000, 600000)
        val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
        val labels = options.map { timeoutLabel(it) }.toTypedArray()
        textIn(c, "מתי המסך ייכבה?", 14, dark, true)
        val button = MaterialButton(this).apply {
            text = "כרגע: " + timeoutLabel(current)
            isAllCaps = false
            isEnabled = allowed
            setOnClickListener {
                val selected = options.indexOf(current)
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("מתי המסך ייכבה?")
                    .setSingleChoiceItems(labels, selected) { dialog, which ->
                        try { Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, options[which]) }
                        catch (_: SecurityException) { openWriteSettings() }
                        dialog.dismiss()
                        render()
                    }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
        }
        c.addView(button)
        if (!allowed) {
            textIn(c, "כדי לשנות מכאן, צריך לאפשר לאפליקציה לעדכן את הגדרות המסך.", 12, secondary)
            permissionButton(c, "בואו נאפשר את זה") { openWriteSettings() }
        }
    }

    private fun timeoutLabel(ms: Int): String = when {
        ms < 60000 -> "${ms / 1000} שניות"
        ms == 60000 -> "דקה"
        ms % 60000 == 0 -> "${ms / 60000} דקות"
        else -> "${ms / 1000} שניות"
    }

    private fun autoBrightnessCard() {
        val c = card()
        val enabled = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1
        switch(c, "בהירות אוטומטית", enabled, Settings.System.canWrite(this), onPermissionMissing = { openWriteSettings() }) { checked ->
            try { Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, if (checked) 1 else 0) }
            catch (_: SecurityException) { openWriteSettings() }
        }
        textIn(c, "גרירת פס הבהירות מחזירה לבהירות ידנית.", 12, secondary)
    }

    private fun hapticCard() {
        val c = card()
        val allowed = Settings.System.canWrite(this)
        val enabled = Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        switch(c, "רטט בלחיצה", enabled, allowed, onPermissionMissing = { openWriteSettings() }) { checked ->
            try { Settings.System.putInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (checked) 1 else 0) }
            catch (_: SecurityException) { openWriteSettings() }
        }
    }

    private fun capabilityCard(title: String, explanation: String) {
        val c = card()
        textIn(c, title, 14, dark, true)
        textIn(c, explanation, 12, secondary)
    }

    private fun secureSettingsCard() {
        val c = card()
        textIn(c, "אישור דרך המחשב", 14, dark, true)
        textIn(c, if (hasSecureSettings()) "האישור כבר ניתן. הכלים המתקדמים מוכנים לשימוש." else
            "מחברים את המכשיר למחשב עם ADB, מפעילים ניפוי באגים ב־USB ומריצים את הפקודה. זה פותח חלק מהאפשרויות המתקדמות, לפי מה שהמכשיר מאפשר.", 13, secondary)
        val command = "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
        val b = MaterialButton(this).apply {
            text = "העתקת הפקודה למחשב"
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
            contentDescription = label
            setOnCheckedChangeListener { _, value -> action(value) }
        }
        row.addView(control)
        parent.addView(row)
        if (!enabled && onPermissionMissing != null) {
            permissionButton(parent, "איך מאפשרים את זה?") { onPermissionMissing() }
        }
    }

    private fun permissionButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        button.minimumHeight = dp(48)
        parent.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    toast("לא הצלחנו לפתוח את ההרשאה. אפשר לחפש אותה בהגדרות האפליקציה.")
                }
            } else {
                toast("לא הצלחנו לפתוח את ההרשאה. אפשר לחפש אותה בהגדרות האפליקציה.")
            }
        }
    }

    private fun secureSettingsHelp() {
        copyToClipboard("adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        toast("הפקודה הועתקה. מריצים אותה במחשב כשהמכשיר מחובר ו־USB debugging פועל.")
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

