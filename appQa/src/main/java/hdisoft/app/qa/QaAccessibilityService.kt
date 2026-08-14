package hdisoft.app.qa

import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import hdisoft.app.qa.accessibility.BaseAccessibilityGestureService

class QaAccessibilityService : BaseAccessibilityGestureService() {

    companion object {
        @Volatile
        var instance: QaAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    override fun onGestureServiceConnected() {
        instance = this
    }

    override fun onGestureServiceDisconnected() {
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitoring events is optional; currently idle.
    }

    override fun onInterrupt() {
        // Required method implementation
    }

    /** Backward-compatible alias used by the action runner and overlay. */
    fun clickAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean =
        tap(x, y, 80, callback)

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

    /** Finds and clicks the OEM Recents "clear/close all" control when visible. */
    fun clickClearAllRecents(): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findClearAllNode(root)
        var current = target
        var clicked = false
        while (current != null && !clicked) {
            if (current.isClickable) {
                clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        root.recycle()
        return clicked
    }

    private fun findClearAllNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val labels = listOf(node.text, node.contentDescription)
            .mapNotNull { it?.toString()?.trim()?.lowercase() }
        val isClearAll = labels.any {
            it == "clear all" || it == "close all" || it == "dismiss all" ||
                it == "xóa tất cả" || it == "xoá tất cả" || it == "đóng tất cả"
        }
        if (isClearAll) return node

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findClearAllNode(child)
            if (match != null) return match
        }
        return null
    }
}
