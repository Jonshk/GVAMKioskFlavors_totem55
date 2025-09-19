package com.gvam.kioskportal.kiosk

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import kotlin.math.roundToInt

object StatusBarBlocker {
    private var overlay: View? = null

    fun hasPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true

    fun requestPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission(ctx)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    fun start(ctx: Context) {
        if (overlay != null || !hasPermission(ctx)) return
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bigScreen = ctx.resources.configuration.smallestScreenWidthDp >= 720
        val heightDp = if (bigScreen) 72f else 48f
        val h = (heightDp * ctx.resources.displayMetrics.density).roundToInt()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            h,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        overlay = object : FrameLayout(ctx) {
            override fun onTouchEvent(e: MotionEvent) = true
        }.also { v ->
            v.setBackgroundColor(0x00000000)
            wm.addView(v, lp)
        }
    }

    fun stop(ctx: Context) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }
}