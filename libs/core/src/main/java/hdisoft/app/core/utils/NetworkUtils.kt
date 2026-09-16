package hdisoft.app.core.utils

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

object NetworkUtils {

    // RFC1918 private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16.
    // Note the 172.16.0.0/12 block spans second octet 16-31 (172.17.x.x, 172.20.x.x,
    // 172.24.x.x, etc. are just as valid as 172.16./172.31.) — a plain string-prefix
    // check on only those two boundary values would silently miss the other 14 and
    // make the local IP (and therefore the LAN scan subnet) resolve to null on those
    // networks, which are common for Docker/VM host networks and some routers.
    private fun isPrivateIPv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    fun getLocalSubnet(context: Context): String? {
        val localIp = getLocalIpAddress(context) ?: return null
        val lastDotIndex = localIp.lastIndexOf('.')
        if (lastDotIndex == -1) return null
        return localIp.substring(0, lastDotIndex + 1)
    }

    fun getLocalIpAddress(context: Context): String? {
        // First try: via Network Interfaces (cleaner, permission-free, and works on all Android versions)
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // First pass: look for Wi-Fi or Ethernet interfaces
            for (networkInterface in interfaces) {
                val name = networkInterface.name.lowercase(Locale.ROOT)
                if (name.contains("wlan") || name.contains("eth")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: continue
                            if (ip.indexOf(':') < 0 && isPrivateIPv4(ip)) { // IPv4, private range only
                                return ip
                            }
                        }
                    }
                }
            }
            
            // Second pass: look at all other interfaces (fallback)
            for (networkInterface in interfaces) {
                val name = networkInterface.name.lowercase(Locale.ROOT)
                if (!name.contains("wlan") && !name.contains("eth")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: continue
                            if (ip.indexOf(':') < 0 && isPrivateIPv4(ip)) { // IPv4, private range only
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // Second try (Fallback): via WifiManager (requires location permission on Android 10+, so we catch silently)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                val ipString = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    (ipAddress and 0xff),
                    (ipAddress shr 8 and 0xff),
                    (ipAddress shr 16 and 0xff),
                    (ipAddress shr 24 and 0xff)
                )
                if (ipString != "0.0.0.0") return ipString
            }
        } catch (e: Exception) {
            // Log quietly to avoid spamming system.err when location permission is not granted
        }

        return null
    }

    fun ensureWifiEnabled(context: Context) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    Toast.makeText(context, "Enabling Wi-Fi...", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please enable Wi-Fi", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
