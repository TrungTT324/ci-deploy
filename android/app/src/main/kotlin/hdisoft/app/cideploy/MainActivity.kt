package hdisoft.app.cideploy

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {
    private val CHANNEL = "hdisoft.app.cideploy/install"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "installApk") {
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    val success = installApk(filePath)
                    if (success) {
                        result.success(true)
                    } else {
                        result.error("INSTALL_ERROR", "Failed to start install intent", null)
                    }
                } else {
                    result.error("BAD_ARGUMENT", "filePath is null", null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun installApk(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = "$packageName.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        }

        try {
            startActivity(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
