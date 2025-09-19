package com.gvam.kioskportal.kiosk

import android.content.Context
import android.content.Context.MODE_PRIVATE

object KioskState {
    private const val FILE = "kiosk_state"
    private const val KEY_MAINTENANCE = "maintenance"
    fun setMaintenance(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).edit().putBoolean(KEY_MAINTENANCE, enabled).apply()
    }
    fun isMaintenance(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).getBoolean(KEY_MAINTENANCE, false)
}