package com.gvam.kioskportal.kiosk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.widget.FrameLayout
import com.gvam.kioskportal.ui.MainActivity

class KioskGuardService : AccessibilityService() {

    private val blockedPkgs = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3"
    )

    private var hotCorner: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val longPressMs = 3000L

    private var f12Count = 0
    private var f12WindowStart = 0L

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 0
        }
        ensureHotCorner()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || KioskState.isMaintenance(this)) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg in blockedPkgs) {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
                val i = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                             Intent.FLAG_ACTIVITY_CLEAR_TOP or
                             Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(i)
                sendBroadcast(Intent(MainActivity.ACTION_REPIN))
            }
        }
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (KioskState.isMaintenance(this)) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Ctrl + Alt + K
        if (event.isCtrlPressed && event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_K) {
            launchAdmin(); return true
        }

        // F12 x3 en <= 2s
        if (event.keyCode == KeyEvent.KEYCODE_F12) {
            val now = SystemClock.uptimeMillis()
            if (now - f12WindowStart > 2000) { f12WindowStart = now; f12Count = 0 }
            f12Count++
            if (f12Count >= 3) { launchAdmin(); f12Count = 0; return true }
        }
        return false
    }

    private fun ensureHotCorner() {
        if (hotCorner != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val bigScreen = resources.configuration.smallestScreenWidthDp >= 720
        val sizeDp = if (bigScreen) 56 else 36
        val size = (sizeDp * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.END }

        hotCorner = object : FrameLayout(this) {
            private var pressed = false
            private val trigger = Runnable { if (pressed) launchAdmin() }
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { pressed = true; handler.postDelayed(trigger, longPressMs) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressed = false; handler.removeCallbacks(trigger) }
                }
                return true
            }
        }.also { v ->
            v.setBackgroundColor(0x00000000)
            wm.addView(v, lp)
        }
    }

    private fun launchAdmin() {
        val i = Intent(this, AdminUnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(i)
    }

    override fun onDestroy() {
        super.onDestroy()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        hotCorner?.let { runCatching { wm.removeView(it) } }
        hotCorner = null
    }
}