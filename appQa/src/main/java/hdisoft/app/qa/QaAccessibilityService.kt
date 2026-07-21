package hdisoft.app.qa

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class QaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: QaAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitoring events is optional; currently idle.
    }

    override fun onInterrupt() {
        // Required method implementation
    }

    /**
     * Performs a programmatic click at the specified screen coordinates (N+).
     */
    fun clickAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(x, y)
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
        }
        callback?.invoke(false)
        return false
    }

    /**
     * Swipes between two points on the screen (N+).
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)
            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback?.invoke(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback?.invoke(false)
                }
            }, null)
        }
        callback?.invoke(false)
        return false
    }

    /**
     * Injects text into the currently active focused input view.
     */
    fun inputText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
            rootNode.recycle()
            return success
        }
        
        // Fallback: search children recursively for any editable text field
        val fallbackSuccess = findAndSetText(rootNode, text)
        rootNode.recycle()
        return fallbackSuccess
    }

    private fun findAndSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable && node.isEnabled) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndSetText(child, text)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    /**
     * Performs standard Android navigation actions (Back, Home, Recents, Notifications).
     */
    fun performNavigation(action: Int): Boolean {
        return performGlobalAction(action)
    }
}
