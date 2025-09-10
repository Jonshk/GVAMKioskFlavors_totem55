package com.gvam.kioskportal.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gvam.kioskportal.BuildConfig
import com.gvam.kioskportal.ui.MainActivity

private const val PREF_FILE = "portal_prefs"
private const val KEY_MAINTENANCE = "kiosk_maintenance"
private const val KEY_PENDING_ACTION = "pending_action" // lo leerá MainActivity
private const val PENDING_OPEN_ADMIN = "open_admin"

class KioskGuardService : AccessibilityService() {

    private var lastBounceAt = 0L
    private var f12Count = 0
    private var f12WindowStart = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!shouldRunKiosk()) return
        if (event == null) return

        val now = SystemClock.uptimeMillis()
        if (now - lastBounceAt < 700) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.startsWith(packageName)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                lastBounceAt = now
                bounceHome()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!shouldRunKiosk()) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Ctrl + Alt + K
        if (event.isCtrlPressed && event.isAltPressed && event.keyCode == KeyEvent.KEYCODE_K) {
            openAdminPanel()
            return true
        }

        // F12 × 3 (≤ 4 s)
        if (event.keyCode == KeyEvent.KEYCODE_F12) {
            val now = SystemClock.uptimeMillis()
            if (now - f12WindowStart > 4000) {
                f12WindowStart = now
                f12Count = 1
            } else {
                f12Count += 1
            }
            if (f12Count >= 3) {
                f12Count = 0
                openAdminPanel()
            }
            return true
        }
        return false
    }

    override fun onInterrupt() {}

    /* ------------ helpers ------------ */

    private fun shouldRunKiosk(): Boolean {
        val isKioskMode = BuildConfig.IS_TOTEM || BuildConfig.KIOSK_FORCE
        return isKioskMode && !isMaintenanceActive()
    }

    private fun isMaintenanceActive(): Boolean =
        getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_MAINTENANCE, false)

    private fun bounceHome() {
        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(i)
    }

    private fun openAdminPanel() {
        getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING_ACTION, PENDING_OPEN_ADMIN).apply()

        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(i)
    }
}
