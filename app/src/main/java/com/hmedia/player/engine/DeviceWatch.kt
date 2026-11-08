package com.hmedia.player.engine

import android.app.ActivityManager
import android.content.Context

// True on phones Android marks as low-RAM. The engine starts those with a lighter preset.
fun isLowRam(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isLowRamDevice
}
