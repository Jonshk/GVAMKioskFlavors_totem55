package com.gvam.kioskportal.kiosk

import android.content.Context
import android.content.Context.MODE_PRIVATE

object UsbKeyState {
    private const val FILE = "kiosk_usb_key"
    private const val KEY_PRESENT = "present"
    fun setPresent(ctx: Context, present: Boolean) {
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).edit().putBoolean(KEY_PRESENT, present).apply()
    }
    fun isPresent(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, MODE_PRIVATE).getBoolean(KEY_PRESENT, false)
}