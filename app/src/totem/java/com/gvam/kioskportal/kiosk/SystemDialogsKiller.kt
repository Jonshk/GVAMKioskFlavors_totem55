package com.gvam.kioskportal.kiosk

import android.content.Context
import android.content.Intent

object SystemDialogsKiller {
    fun closeAll(ctx: Context) {
        runCatching { ctx.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) }
    }
}