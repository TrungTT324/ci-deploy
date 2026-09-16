package hdisoft.app.qa

import android.accessibilityservice.AccessibilityService
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ClearRecentsResult(
    val success: Boolean,
    val method: String = "accessibility",
    val message: String
)

/**
 * Removes tasks through the device's Recents UI using Accessibility.
 *
 * This deliberately does not claim to force-stop processes: an ordinary,
 * non-root Android app has no permission to force-stop another application.
 */
object ClearRecentsTool {
    suspend fun clear(context: Context): ClearRecentsResult {
        val service = QaAccessibilityService.instance
            ?: return ClearRecentsResult(
                success = false,
                message = "The appQa Accessibility service is not enabled."
            )

        val opened = withContext(Dispatchers.Main) {
            service.performNavigation(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
        if (!opened) {
            return ClearRecentsResult(
                success = false,
                message = "Could not open Android Recents."
            )
        }

        delay(800)
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        val height = context.resources.displayMetrics.heightPixels.toFloat()
        repeat(6) { attempt ->
            val clicked = withContext(Dispatchers.Main) { service.clickClearAllRecents() }
            if (clicked) {
                delay(500)
                return ClearRecentsResult(
                    success = true,
                    message = "Requested removal of all tasks exposed by Android Recents."
                )
            }

            val swipeRight = attempt < 3
            withContext(Dispatchers.Main) {
                service.swipe(
                    if (swipeRight) width * 0.2f else width * 0.8f,
                    height * 0.5f,
                    if (swipeRight) width * 0.85f else width * 0.15f,
                    height * 0.5f,
                    250
                )
            }
            delay(400)
        }

        return ClearRecentsResult(
            success = false,
            message = "The Recents clear-all control was not exposed by this device."
        )
    }
}
