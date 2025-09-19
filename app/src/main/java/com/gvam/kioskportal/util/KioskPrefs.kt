// app/src/main/java/com/gvam/kioskportal/system/KioskPrefs.kt
package com.gvam.kioskportal.system

import android.content.Context
import com.gvam.kioskportal.util.Prefs

/** Proxy de compatibilidad: delega 100% en Prefs para el estado de mantenimiento. */
object KioskPrefs {
    fun isMaintenance(ctx: Context): Boolean = Prefs.isMaintenance(ctx)
    fun setMaintenance(ctx: Context, value: Boolean) = Prefs.setMaintenance(ctx, value)
}
