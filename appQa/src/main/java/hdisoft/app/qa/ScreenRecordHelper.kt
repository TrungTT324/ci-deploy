package hdisoft.app.qa

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class ScreenRecordHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null
    private var isRecording = false

    fun startRecording(mediaProjection: MediaProjection, file: File, callback: (Boolean, Throwable?) -> Unit) {
        if (isRecording) {
            callback(false, Exception("Already recording screen"))
            return
        }
        this.outputFile = file
        
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(3 * 1024 * 1024) // 3 Mbps
                setOutputFile(file.absolutePath)
                prepare()
            }

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecord",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null,
                null
            )

            mediaRecorder!!.start()
            isRecording = true
            callback(true, null)
        } catch (e: Exception) {
            stopRecordingInternal()
            callback(false, e)
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        stopRecordingInternal()
        return outputFile
    }

    private fun stopRecordingInternal() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        isRecording = false
    }

    fun isRecordingNow(): Boolean = isRecording
}
