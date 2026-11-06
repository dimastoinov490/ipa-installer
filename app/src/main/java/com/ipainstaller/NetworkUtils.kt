package com.ipainstaller

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class NetworkAddress(
    val ip: String,
    val interfaceName: String,
    val displayName: String,
    val isWifiOrAp: Boolean
)

object NetworkUtils {

    fun getAvailableIpAddresses(context: Context): List<NetworkAddress> {
        val result = mutableListOf<NetworkAddress>()

        try {
            // First attempt: WifiManager if connected
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIpInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            val wifiIpString = if (wifiIpInt != 0) {
                @Suppress("DEPRECATION")
                Formatter.formatIpAddress(wifiIpInt)
            } else null

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val name = intf.name.lowercase()
                val isApOrWifi = name.startsWith("wlan") || name.startsWith("ap") ||
                        name.startsWith("softap") || name.startsWith("swlan") ||
                        name.startsWith("rndis") || name.startsWith("eth")

                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (hostAddress.startsWith("127.")) continue

                        val label = when {
                            hostAddress == wifiIpString -> "Wi-Fi ($hostAddress)"
                            name.startsWith("ap") || name.startsWith("softap") -> "Hotspot ($hostAddress)"
                            name.startsWith("wlan") -> "Wireless ($hostAddress)"
                            name.startsWith("rndis") -> "USB Tether ($hostAddress)"
                            name.startsWith("eth") -> "Ethernet ($hostAddress)"
                            else -> "${intf.displayName} ($hostAddress)"
                        }

                        result.add(
                            NetworkAddress(
                                ip = hostAddress,
                                interfaceName = intf.name,
                                displayName = label,
                                isWifiOrAp = isApOrWifi
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort so wifi or hotspot interfaces appear first
        result.sortByDescending { it.isWifiOrAp }

        // Fallback if no network found
        if (result.isEmpty()) {
            result.add(
                NetworkAddress(
                    ip = "127.0.0.1",
                    interfaceName = "lo",
                    displayName = "Localhost (127.0.0.1)",
                    isWifiOrAp = false
                )
            )
        }

        return result
    }

    fun getPrimaryIpAddress(context: Context): String {
        val addresses = getAvailableIpAddresses(context)
        val preferred = addresses.firstOrNull { it.isWifiOrAp } ?: addresses.firstOrNull()
        return preferred?.ip ?: "127.0.0.1"
    }
}
