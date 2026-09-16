package hdisoft.app.qa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

class ScreenCaptureHelper(private val context: Context) {
    interface CaptureCallback {
        fun onCaptureSuccess(bitmap: Bitmap)
        fun onCaptureError(e: Throwable)
    }

    fun captureScreen(mediaProjection: MediaProjection, callback: CaptureCallback) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        // Instantiating ImageReader to retrieve raw screen buffers
        @SuppressLint("WrongConstant")
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        var virtualDisplay: VirtualDisplay? = null
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )
        } catch (e: Exception) {
            imageReader.close()
            callback.onCaptureError(e)
            return
        }

        var hasCaptured = false
        val handler = Handler(Looper.getMainLooper())
        
        // Safety timeout to handle unresponsive virtual displays
        val timeoutRunnable = Runnable {
            if (!hasCaptured) {
                hasCaptured = true
                virtualDisplay?.release()
                imageReader.close()
                callback.onCaptureError(Exception("Capture timed out waiting for frame"))
            }
        }
        handler.postDelayed(timeoutRunnable, 2500)

        imageReader.setOnImageAvailableListener({ reader ->
            if (hasCaptured) return@setOnImageAvailableListener
            hasCaptured = true
            handler.removeCallbacks(timeoutRunnable)

            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    // Allocating bitmap with row stride padding
                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    // Cropping out row padding to get exact screen bounds
                    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    
                    image.close()
                    virtualDisplay?.release()
                    reader.close()
                    
                    callback.onCaptureSuccess(croppedBitmap)
                } else {
                    virtualDisplay?.release()
                    reader.close()
                    callback.onCaptureError(Exception("No screen buffer image acquired"))
                }
            } catch (e: Exception) {
                virtualDisplay?.release()
                reader.close()
                callback.onCaptureError(e)
            }
        }, handler)
    }
}
