package com.gvam.kioskportal.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.gvam.kioskportal.ui.AdminUnlockActivity

class UsbKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Abre el panel técnico (con PIN) para salir a mantenimiento
            val i = Intent(context, AdminUnlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}
