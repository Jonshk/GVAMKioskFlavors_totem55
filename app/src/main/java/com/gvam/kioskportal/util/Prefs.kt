package com.gvam.kioskportal.util

import android.content.Context

object Prefs {
    private const val FILE = "portal_prefs"

    // Claves básicas
    private const val KEY_PKGS              = "selected_packages"
    private const val KEY_PIN_HASH          = "admin_pin_hash"
    private const val KEY_TITLE             = "portal_title"
    private const val KEY_DEVICE_NUMBER     = "device_number"
    private const val KEY_HIDE_SETTINGS     = "hide_settings"
    private const val KEY_BG_URI            = "bg_uri"               // compat (fondo)
    private const val KEY_LAYOUT_HORIZONTAL = "layout_horizontal"
    private const val KEY_MAINTENANCE       = "kiosk_maintenance"

    // Kiosco extendido
    private const val KEY_ALLOWLIST   = "kiosk_allowlist"            // lista blanca dinámica
    private const val KEY_KIOSK_STRICT = "kiosk_strict"              // modo estricto (default true)

    // ---- Helpers genéricos ----
    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun putBool(ctx: Context, key: String, value: Boolean) =
        sp(ctx).edit().putBoolean(key, value).apply()
    fun getBool(ctx: Context, key: String, def: Boolean = false): Boolean =
        sp(ctx).getBoolean(key, def)

    // ---- Selección de apps ----
    fun saveSelectedPackages(ctx: Context, packages: Set<String>) {
        sp(ctx).edit().putStringSet(KEY_PKGS, packages).apply()
    }
    fun loadSelectedPackages(ctx: Context): Set<String> =
        sp(ctx).getStringSet(KEY_PKGS, emptySet()) ?: emptySet()

    // ---- PIN admin ----
    fun hasPin(ctx: Context): Boolean = sp(ctx).contains(KEY_PIN_HASH)
    fun savePinHash(ctx: Context, hash: String) {
        sp(ctx).edit().putString(KEY_PIN_HASH, hash).apply()
    }
    fun getPinHash(ctx: Context): String? = sp(ctx).getString(KEY_PIN_HASH, null)

    // ---- Título del portal ----
    /**
     * Guarda el título tal cual:
     *  - null  -> borra la preferencia
     *  - ""    -> título vacío (no se muestra texto)
     *  - texto -> se guarda tal cual
     */
    fun saveTitle(ctx: Context, title: String?) {
        val e = sp(ctx).edit()
        if (title == null) e.remove(KEY_TITLE) else e.putString(KEY_TITLE, title)
        e.apply()
    }
    fun loadTitle(ctx: Context): String? = sp(ctx).getString(KEY_TITLE, null)
    fun clearTitle(ctx: Context) = saveTitle(ctx, null)

    // ---- Número de dispositivo ----
    fun saveDeviceNumber(ctx: Context, number: String) {
        sp(ctx).edit().putString(KEY_DEVICE_NUMBER, number).apply()
    }
    fun loadDeviceNumber(ctx: Context): String? =
        sp(ctx).getString(KEY_DEVICE_NUMBER, null)

    // ---- Ocultar Ajustes ----
    fun setHideSettings(ctx: Context, hide: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HIDE_SETTINGS, hide).apply()
    }
    fun isSettingsHidden(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_HIDE_SETTINGS, false)

    // ---- Fondo (compat) ----
    fun setBackgroundUri(ctx: Context, uri: String?) {
        val e = sp(ctx).edit()
        if (uri.isNullOrBlank()) e.remove(KEY_BG_URI) else e.putString(KEY_BG_URI, uri)
        e.apply()
    }
    fun getBackgroundUri(ctx: Context): String? =
        sp(ctx).getString(KEY_BG_URI, null)

    // ---- Layout ----
    fun setHorizontal(ctx: Context, horizontal: Boolean) {
        sp(ctx).edit().putBoolean(KEY_LAYOUT_HORIZONTAL, horizontal).apply()
    }
    fun isHorizontal(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_LAYOUT_HORIZONTAL, false)

    // ---- Mantenimiento (pausa kiosco) ----
    fun setMaintenance(ctx: Context, active: Boolean) {
        sp(ctx).edit().putBoolean(KEY_MAINTENANCE, active).apply()
    }
    fun isMaintenance(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_MAINTENANCE, false)

    // ---- Allowlist dinámica ----
    fun getAllowlist(ctx: Context): Set<String> =
        sp(ctx).getStringSet(KEY_ALLOWLIST, emptySet()) ?: emptySet()

    fun addToAllowlist(ctx: Context, pkg: String) {
        val s = getAllowlist(ctx).toMutableSet()
        s += pkg
        sp(ctx).edit().putStringSet(KEY_ALLOWLIST, s).apply()
    }

    fun removeFromAllowlist(ctx: Context, pkg: String) {
        val s = getAllowlist(ctx).toMutableSet()
        s -= pkg
        sp(ctx).edit().putStringSet(KEY_ALLOWLIST, s).apply()
    }

    fun clearAllowlist(ctx: Context) {
        sp(ctx).edit().remove(KEY_ALLOWLIST).apply()
    }

    // ---- Modo estricto ----
    fun setKioskStrict(ctx: Context, strict: Boolean) {
        sp(ctx).edit().putBoolean(KEY_KIOSK_STRICT, strict).apply()
    }
    fun isKioskStrict(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_KIOSK_STRICT, true)
}
