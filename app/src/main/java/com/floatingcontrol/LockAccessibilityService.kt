package com.floatingcontrol

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Only used to lock the screen like the power button does,
 * so fingerprint unlock keeps working. It does not read anything on screen.
 */
class LockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LockAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty
    }

    override fun onInterrupt() {
        // Intentionally empty
    }
}
