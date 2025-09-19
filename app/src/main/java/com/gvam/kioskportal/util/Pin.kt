package com.gvam.kioskportal.util

import android.content.Context
import java.security.MessageDigest

object Pin {

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun savePin(ctx: Context, pin: String) {
        Prefs.savePinHash(ctx, sha256(pin))
    }

    fun verifyPin(ctx: Context, pin: String): Boolean {
        val saved = Prefs.getPinHash(ctx) ?: return false
        return saved == sha256(pin)
    }
}
