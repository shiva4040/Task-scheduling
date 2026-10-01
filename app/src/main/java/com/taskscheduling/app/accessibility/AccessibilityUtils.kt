package com.taskscheduling.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.WindowManager
import com.taskscheduling.app.domain.model.DeviceProfile

object AccessibilityUtils {

    /**
     * Checks whether TaskSchedulingAccessibilityService is enabled in Android Accessibility settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, TaskSchedulingAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Opens Android Accessibility Settings.
     * Does NOT trigger any workflow automation.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Dynamically detects the current device's hardware & display profile.
     * Uses real physical screen pixels (not dp/sp).
     */
    fun getDeviceProfile(context: Context): DeviceProfile {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (widthPx, heightPx, density) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val d = context.resources.displayMetrics.density
            Triple(bounds.width(), bounds.height(), d)
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            Triple(displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.density)
        }

        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val orientation = if (isLandscape || widthPx > heightPx) "LANDSCAPE" else "PORTRAIT"

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            screenWidth = widthPx,
            screenHeight = heightPx,
            density = density,
            orientation = orientation
        )
    }
}
