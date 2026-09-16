package hdisoft.app.core.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import java.util.concurrent.TimeUnit

object DeviceUtils {
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    // Standard AOSP/community emulator fingerprints. Used to warn when a feature that
    // needs real LAN reachability (e.g. acting as a WebSocket server for other devices
    // to connect to) is running somewhere an emulator's virtual NAT will break it.
    fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.PRODUCT.contains("sdk_gphone") ||
            Build.PRODUCT.contains("sdk") ||
            Build.PRODUCT.contains("vbox86p") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT
    }

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    /**
     * Only caches a **positive** result permanently (a device doesn't
     * un-root mid-process). A negative result is never cached — `su` can
     * legitimately fail on an early call (root daemon not warmed up yet
     * right after boot, or the root manager's own superuser-grant prompt
     * not answered yet) and succeed on a later one; caching that first
     * `false` would otherwise silently and permanently disable whatever
     * depends on this for the rest of the process.
     *
     * Tries both common `su` invocation conventions — Magisk/SuperSU-style
     * (`su -c "<command>"`) and the AOSP/toybox `su` bundled with non-Magisk
     * "Google APIs" emulator system images, which has **no `-c` flag at
     * all** (usage is `su WHO COMMAND...` — passing `-c` gets parsed as a
     * literal, nonexistent username and fails immediately). Verified
     * directly against a Google APIs emulator: `su -c id` alone reports
     * `su: invalid uid/gid '-c'` even though the device is genuinely rooted
     * (`adb root` / `adb shell id` both confirm `uid=0`).
     */
    fun isDeviceRooted(): Boolean {
        cachedRootAvailable?.let { if (it) return true }
        val result = runAsRoot("id")?.contains("uid=0") == true
        if (result) cachedRootAvailable = true
        android.util.Log.d("CI_DEPLOY_ROOT", "Usable root access: $result")
        return result
    }

    /** Runs [command] as root, trying Magisk/SuperSU (`-c`) then AOSP/toybox (`0 sh -c`) `su` syntax. Returns stdout on success, or null if both fail. */
    private fun runAsRoot(command: String): String? {
        val suPath = findSuPath()
        val invocations = listOf(
            arrayOf(suPath, "-c", command),
            arrayOf(suPath, "0", "sh", "-c", command)
        )
        for (args in invocations) {
            runProcessAsRoot(args)?.let { return it }
        }
        return null
    }

    private fun runProcessAsRoot(args: Array<String>): String? {
        return try {
            val process = Runtime.getRuntime().exec(args)
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            null
        }
    }

    fun findSuPath(): String {
        val paths = arrayOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return path
        }
        return "su"
    }
}
