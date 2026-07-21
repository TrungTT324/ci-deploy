package hdisoft.app.logcat.data.datasource

import hdisoft.app.core.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLogcatDataSource {

    suspend fun clearLogcatBuffer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = if (DeviceUtils.isDeviceRooted()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -c"))
            } else {
                Runtime.getRuntime().exec("logcat -c")
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getLogcatProcess(): Process? {
        return try {
            // Unrooted apps can only read their own process' log lines (Android restricts
            // READ_LOGS to system/privileged apps since 4.1). Running via su on a rooted
            // device is required to capture the full system-wide logcat stream.
            if (DeviceUtils.isDeviceRooted()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "logcat -v time -T 1000"))
            } else {
                Runtime.getRuntime().exec("logcat -v time -T 1000")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
