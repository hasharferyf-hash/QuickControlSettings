package QuickControlSettings.brisker.AI

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** A single accessible target: the label, icon and state all activate the same action. */
class QuickTile(
    context: Context,
    private val label: String,
    status: String,
    icon: Int,
    active: Boolean,
    toggle: Boolean,
    private val foreground: Int,
    muted: Int,
    private val surface: Int,
    private val accent: Int,
    onClick: () -> Unit
) : LinearLayout(context) {
    private lateinit var circle: MaterialButton
    private lateinit var statusText: TextView
    private var currentActive = active
    private var currentStatus = status

    fun updateState(status: String, active: Boolean, onClick: () -> Unit) {
        setOnClickListener { onClick() }
        if (currentStatus != status) {
            currentStatus = status
            statusText.text = status
            contentDescription = "$label, $status"
        }
        if (currentActive != active) {
            currentActive = active
            circle.iconTint = ColorStateList.valueOf(if (active) Color.WHITE else foreground)
            circle.backgroundTintList = ColorStateList.valueOf(if (active) accent else surface)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        setPadding(dp(3), dp(8), dp(3), dp(8))
        minimumHeight = dp(124)
        val ripple = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        isClickable = true
        isFocusable = true
        contentDescription = "$label, $status"
        setOnClickListener { onClick() }
        circle = MaterialButton(context).apply {
            text = ""
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(29)
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setIconResource(icon)
            iconSize = dp(26)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = ColorStateList.valueOf(if (active) Color.WHITE else foreground)
            backgroundTintList = ColorStateList.valueOf(if (active) accent else surface)
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(circle, LayoutParams(dp(58), dp(58)))
        fun caption(value: String, size: Float, color: Int) = TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(caption(label, 13f, foreground).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(9), 0, 0)
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        statusText = caption(status, 11f, muted)
        addView(statusText)
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = if (toggle) "android.widget.Switch" else "android.widget.Button"
                info.isCheckable = toggle
                info.isChecked = toggle && currentActive
            }
        }
    }
}
