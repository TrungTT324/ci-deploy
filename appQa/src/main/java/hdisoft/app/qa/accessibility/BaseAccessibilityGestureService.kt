package hdisoft.app.qa.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path

/**
 * Shared gesture dispatch and lifecycle plumbing for appQa accessibility
 * services. The module requires API 24+, matching dispatchGesture().
 */
abstract class BaseAccessibilityGestureService : AccessibilityService() {

    final override fun onServiceConnected() {
        super.onServiceConnected()
        onGestureServiceConnected()
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        onGestureServiceDisconnected()
        return super.onUnbind(intent)
    }

    final override fun onDestroy() {
        onGestureServiceDisconnected()
        super.onDestroy()
    }

    /** Called once the service is connected and ready to dispatch gestures/actions. */
    protected open fun onGestureServiceConnected() {}

    /** Called on unbind/destroy — subclasses should clear their singleton `instance` here. */
    protected open fun onGestureServiceDisconnected() {}

    /**
     * Taps a single point on screen.
     */
    fun tap(x: Float, y: Float, durationMs: Long = 80, callback: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, callback?.let { toResultCallback(it) }, null)
    }

    /**
     * Swipes in a straight line between two points.
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, callback?.let { toResultCallback(it) }, null)
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    private fun toResultCallback(callback: (Boolean) -> Unit) = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) = callback(true)
        override fun onCancelled(gestureDescription: GestureDescription?) = callback(false)
    }
}
