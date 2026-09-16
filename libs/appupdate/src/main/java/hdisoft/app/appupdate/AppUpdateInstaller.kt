package hdisoft.app.appupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.core.utils.DeviceUtils
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The verify/permission/install half of the OTA flow — everything that
 * happens to an APK *after* [AppUpdateChecker.downloadApk] finishes. Used
 * internally by [AppUpdateChecker.installDownloadedApk]; call these directly
 * only if you need finer control than that orchestration gives you.
 *
 * Root detection itself ([hdisoft.app.core.utils.DeviceUtils.isDeviceRooted])
 * stays in `:libs:core` since other, unrelated features depend on it too.
 */
object AppUpdateInstaller {

    private const val TAG = "AppUpdateInstaller"

    data class VerificationResult(val success: Boolean, val message: String)
    data class RootInstallResult(val success: Boolean, val output: String)

    fun cleanupTempApkFiles(cacheDir: File) {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Verifies size, SHA-256, and (best-effort) signing certificate against [info] and the currently installed app. */
    fun verifyApk(context: Context, file: File, info: UpdateInfo): VerificationResult {
        if (!file.isFile || file.length() <= 0L) {
            android.util.Log.w(TAG, "verify: file missing/empty")
            return VerificationResult(false, "Downloaded APK is missing or empty")
        }
        if (info.sizeBytes <= 0L || file.length() != info.sizeBytes) {
            android.util.Log.w(TAG, "verify: size mismatch expected=${info.sizeBytes} actual=${file.length()}")
            return VerificationResult(false, "APK size mismatch: expected ${info.sizeBytes}, got ${file.length()}")
        }
        if (info.sha256.isBlank()) {
            android.util.Log.w(TAG, "verify: expectedSha256 blank")
            return VerificationResult(false, "Version metadata is missing sha256")
        }
        val actualHash = file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var count: Int
            while (input.read(buffer).also { count = it } != -1) {
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (!actualHash.equals(info.sha256, ignoreCase = true)) {
            android.util.Log.w(TAG, "verify: sha256 mismatch expected=${info.sha256} actual=$actualHash")
            return VerificationResult(false, "APK SHA-256 does not match version metadata")
        }

        val packageManager = context.packageManager
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
        } ?: run {
            android.util.Log.w(TAG, "verify: getPackageArchiveInfo returned null")
            return VerificationResult(false, "Downloaded file is not a valid APK")
        }

        val requiredPackage = info.packageName.ifBlank { context.packageName }
        if (archiveInfo.packageName != requiredPackage || requiredPackage != context.packageName) {
            android.util.Log.w(TAG, "verify: package mismatch archive=${archiveInfo.packageName} required=$requiredPackage installed=${context.packageName}")
            return VerificationResult(false, "Package mismatch: expected ${context.packageName}, got ${archiveInfo.packageName}")
        }

        val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val archiveSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installedInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            installedInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        if (installedSignatures.isEmpty()) {
            android.util.Log.w(TAG, "verify: could not read installed app's own signing certificate")
            return VerificationResult(false, "Could not read the installed app's signing certificate")
        }
        if (archiveSignatures.isNotEmpty() &&
            archiveSignatures.none { archive -> installedSignatures.any { installed -> archive.contentEquals(installed) } }
        ) {
            android.util.Log.w(TAG, "verify: signature mismatch archiveCount=${archiveSignatures.size} installedCount=${installedSignatures.size}")
            return VerificationResult(false, "APK signature does not match the installed app")
        }
        if (archiveSignatures.isEmpty()) {
            // getPackageArchiveInfo can fail to read signingInfo for a v2-only
            // (no v1/JAR) APK that isn't installed yet on some API 28/29
            // devices — a known platform limitation, not a real mismatch. The
            // SHA-256 check above already proves this is byte-identical to
            // what our own server published, and the OS installer itself
            // still refuses the install if the certificate actually differs.
            android.util.Log.w(TAG, "verify: archive signing info unreadable, relying on SHA-256 match instead")
        }

        return VerificationResult(true, "APK verified")
    }

    fun hasInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Sends the user to the system settings screen to grant "install unknown apps" for this app. */
    fun requestInstallPermission(context: Context) {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)
        Toast.makeText(context, R.string.appupdate_grant_install_permission, Toast.LENGTH_LONG).show()
    }

    /** Opens the system installer via [FileProvider]. Requests the "install unknown apps" permission first if missing. */
    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists()) return false

        if (!hasInstallPermission(context)) {
            requestInstallPermission(context)
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                context.getString(R.string.appupdate_install_failed_format, e.message),
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /** Installs [file] with no user prompt via `su`/`pm install`. Requires [hdisoft.app.core.utils.DeviceUtils.isDeviceRooted]. */
    fun installApkRootSilent(file: File): RootInstallResult {
        android.util.Log.d(TAG, "Starting silent installation for file: ${file.absolutePath}")
        if (!file.exists()) {
            android.util.Log.e(TAG, "File does not exist!")
            return RootInstallResult(false, "APK file does not exist")
        }
        val tempFilePath = "/data/local/tmp/appupdate_install_temp.apk"
        val suCommand = DeviceUtils.findSuPath()

        var p1: Process? = null
        try {
            android.util.Log.d(TAG, "Process 1: Writing APK to /data/local/tmp via stdin stream using $suCommand")
            p1 = Runtime.getRuntime().exec(arrayOf(suCommand, "-c", "cat > $tempFilePath"))
            p1.outputStream.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(1024 * 64)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
                out.flush()
            }

            if (!p1.waitFor(60, TimeUnit.SECONDS)) {
                p1.destroyForcibly()
                return RootInstallResult(false, "Timed out while copying APK for root install")
            }
            val p1Exit = p1.exitValue()
            android.util.Log.d(TAG, "Process 1 exit code: $p1Exit")
            if (p1Exit != 0) {
                android.util.Log.e(TAG, "Process 1 failed to write APK.")
                return RootInstallResult(false, "Could not copy APK to $tempFilePath")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in Process 1 (su: $suCommand)", e)
            return RootInstallResult(false, e.message ?: "Root copy failed")
        } finally {
            try { p1?.destroy() } catch (e: Exception) {}
        }

        var p2: Process? = null
        var installOutput = ""
        try {
            android.util.Log.d(TAG, "Process 2: Running package installer using $suCommand")
            val command =
                "chmod 644 $tempFilePath; " +
                    "(cmd package install -r $tempFilePath || pm install -r $tempFilePath); " +
                    "result=\$?; rm -f $tempFilePath; exit \$result"
            p2 = ProcessBuilder(suCommand, "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val outputThread = thread(start = true, name = "root-install-output") {
                p2.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                        android.util.Log.d(TAG, "installer: $line")
                    }
                }
            }
            val finished = p2.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                p2.destroyForcibly()
                outputThread.join(2_000)
                return RootInstallResult(false, "Package installation timed out")
            }
            outputThread.join(2_000)
            installOutput = output.toString().trim()
            val p2Exit = p2.exitValue()
            android.util.Log.d(TAG, "Process 2 exit code: $p2Exit")
            val succeeded = p2Exit == 0 &&
                installOutput.lineSequence().any { it.trim().equals("Success", ignoreCase = true) }
            return RootInstallResult(succeeded, installOutput.ifBlank { "Installer exit code: $p2Exit" })
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in Process 2 (su: $suCommand)", e)
            return RootInstallResult(false, e.message ?: installOutput.ifBlank { "Root install failed" })
        } finally {
            try { p2?.destroy() } catch (e: Exception) {}
            try {
                val cleanupProcess = Runtime.getRuntime().exec(suCommand)
                val cleanupOs = java.io.DataOutputStream(cleanupProcess.outputStream)
                cleanupOs.writeBytes("rm -f $tempFilePath\n")
                cleanupOs.writeBytes("exit\n")
                cleanupOs.flush()
                cleanupProcess.waitFor()
                cleanupOs.close()
                cleanupProcess.destroy()
            } catch (ex: Exception) {}
        }
    }
}
