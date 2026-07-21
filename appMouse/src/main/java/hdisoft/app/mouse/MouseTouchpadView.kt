package hdisoft.app.mouse

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * A touch surface that turns finger gestures into mouse-style events:
 * one-finger drag moves the pointer, a quick one-finger tap left-clicks,
 * a quick two-finger tap right-clicks, and a two-finger vertical drag scrolls.
 */
class MouseTouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onMove(dx: Float, dy: Float)
        fun onLeftClick()
        fun onRightClick()
        fun onScroll(steps: Int)
    }

    var listener: Listener? = null
    var sensitivity: Float = 1.6f

    private var lastX = 0f
    private var lastY = 0f
    private var lastTwoFingerY = 0f
    private var downTimeMs = 0L
    private var totalMove = 0f
    private var maxPointers = 0

    private companion object {
        const val TAP_TIMEOUT_MS = 200L
        const val TAP_SLOP_PX = 16f
        const val SCROLL_STEP_PX = 24f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                maxPointers = 1
                lastX = event.x
                lastY = event.y
                downTimeMs = System.currentTimeMillis()
                totalMove = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, event.pointerCount)
                lastTwoFingerY = averageY(event)
                // A second finger joining means this gesture can no longer be a tap.
                totalMove += TAP_SLOP_PX + 1f
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val avgY = averageY(event)
                    val dy = lastTwoFingerY - avgY
                    if (abs(dy) >= SCROLL_STEP_PX) {
                        listener?.onScroll(if (dy > 0) 1 else -1)
                        lastTwoFingerY = avgY
                    }
                } else {
                    val dx = (event.x - lastX) * sensitivity
                    val dy = (event.y - lastY) * sensitivity
                    totalMove += abs(event.x - lastX) + abs(event.y - lastY)
                    if (dx != 0f || dy != 0f) listener?.onMove(dx, dy)
                    lastX = event.x
                    lastY = event.y
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastTwoFingerY = averageY(event)
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - downTimeMs
                if (elapsed < TAP_TIMEOUT_MS && totalMove < TAP_SLOP_PX) {
                    if (maxPointers >= 2) listener?.onRightClick() else listener?.onLeftClick()
                    performClick()
                }
                maxPointers = 0
            }

            MotionEvent.ACTION_CANCEL -> {
                maxPointers = 0
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun averageY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) sum += event.getY(i)
        return sum / event.pointerCount
    }
}
