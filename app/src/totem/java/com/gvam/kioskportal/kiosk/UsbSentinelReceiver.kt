package com.gvam.kioskportal.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbSentinelReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
                if (dev.vendorId == VENDOR_ID && dev.productId == PRODUCT_ID) {
                    UsbKeyState.setPresent(ctx, true)
                    ctx.startActivity(Intent(ctx, AdminUnlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
                if (dev.vendorId == VENDOR_ID && dev.productId == PRODUCT_ID) {
                    UsbKeyState.setPresent(ctx, false)
                }
            }
        }
    }
    companion object {
        const val VENDOR_ID = 1931
        const val PRODUCT_ID = 21863
    }
}