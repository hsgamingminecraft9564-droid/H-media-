package com.hmedia.voidengine.service

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

/**
 * Picture-in-Picture helper. The Activity must declare android:supportsPictureInPicture="true" and
 * android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation" in your manifest.
 */
object PictureInPicture {

    fun isSupported(activity: Activity): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Android only accepts aspect ratios between about 1:2.39 and 2.39:1, so clamp. */
    fun aspectRatio(width: Int, height: Int): Rational {
        val ratio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
        val clamped = ratio.coerceIn(0.42f, 2.39f)
        return Rational((clamped * 1000f).toInt(), 1000)
    }

    fun enter(activity: Activity, videoWidth: Int, videoHeight: Int): Boolean {
        if (!isSupported(activity)) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio(videoWidth, videoHeight))
            .build()
        return activity.enterPictureInPictureMode(params)
    }

    /** Call while video plays so Android 12+ can enter PiP automatically when the user goes Home. */
    fun updateParams(activity: Activity, videoWidth: Int, videoHeight: Int, autoEnter: Boolean) {
        if (!isSupported(activity)) return
        val builder = PictureInPictureParams.Builder().setAspectRatio(aspectRatio(videoWidth, videoHeight))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        activity.setPictureInPictureParams(builder.build())
    }
}
