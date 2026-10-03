package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

data class DiscoveredDevice(
    val ip: String,
    val hostname: String,
    val description: String,
    val isReachable: Boolean
)

class DeviceDiscoveryManager(private val context: Context) {

    private val apiClient = Esp8266ApiClient()

    companion object {
        private const val TAG = "DeviceDiscovery"

        /**
         * Explicitly releases any network binding that may have been applied to the process
         * during local device discovery, NSD/mDNS resolution, or AP connection.
         * This guarantees the app and foreground services can use the best available network
         * (Cellular or default Wi-Fi) without being locked to a disconnected local Wi-Fi interface.
         */
        fun releaseNetworkBinding(context: Context) {
            try {
                val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (connectivityManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(null)
                    } else {
                        @Suppress("DEPRECATION")
                        ConnectivityManager.setProcessDefaultNetwork(null)
                    }
                    Log.d(TAG, "Network binding successfully cleared (process unbound from local network)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing network binding: ${e.message}")
            }
        }
    }

    /**
     * Auto-discovers the AutoAlert ESP8266 device on the local network using Android NsdManager
     * (Network Service Discovery / mDNS for _http._tcp), AP gateway check (192.168.4.1), and
     * subnet DHCP gateway check.
     *
     * Resolves the device strictly to a numeric IPv4 address so standard Android networking
     * can reliably communicate with it.
     */
    suspend fun discoverDevice(timeoutMs: Long = 5000L): DiscoveredDevice? = withContext(Dispatchers.IO) {
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    // Priority 1: Android NsdManager mDNS discovery (_http._tcp)
                    val nsdDeferred = async { performNsdDiscovery() }
                    // Priority 2: ESP8266 AP Default Gateway (192.168.4.1)
                    val gatewayDeferred = async { checkDefaultApGateway() }
                    // Priority 3: Local Wi-Fi DHCP Gateway
                    val subnetDeferred = async { checkSubnetGateways() }

                    val nsdRes = nsdDeferred.await()
                    if (nsdRes != null) {
                        Log.d(TAG, "Device discovered via NsdManager: ${nsdRes.ip}")
                        return@coroutineScope nsdRes
                    }

                    val gatewayRes = gatewayDeferred.await()
                    if (gatewayRes != null) {
                        Log.d(TAG, "Device discovered via AP Gateway: ${gatewayRes.ip}")
                        return@coroutineScope gatewayRes
                    }

                    val subnetRes = subnetDeferred.await()
                    if (subnetRes != null) {
                        Log.d(TAG, "Device discovered via Subnet Gateway: ${subnetRes.ip}")
                        return@coroutineScope subnetRes
                    }

                    null
                }
            }
            result
        } finally {
            // Unconditionally release network binding so ntfy.sh and global traffic are not locked
            releaseNetworkBinding(context)
        }
    }

    /**
     * Uses Android's NsdManager to discover mDNS services advertised under '_http._tcp'
     * (e.g. ESP8266 mDNS service named 'autoalert' or similar) and resolves the service
     * to its numeric IPv4 address.
     */
    private suspend fun performNsdDiscovery(): DiscoveredDevice? = withContext(Dispatchers.IO) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return@withContext null
        val deferredDevice = CompletableDeferred<DiscoveredDevice?>()
        val isResolving = AtomicBoolean(false)
        val isDiscoveryRunning = AtomicBoolean(false)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "NSD discovery start failed: code $errorCode")
                isDiscoveryRunning.set(false)
                if (!deferredDevice.isCompleted) deferredDevice.complete(null)
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "NSD discovery stop failed: code $errorCode")
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "NSD discovery started for $serviceType")
                isDiscoveryRunning.set(true)
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "NSD discovery stopped")
                isDiscoveryRunning.set(false)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null || deferredDevice.isCompleted) return
                val serviceName = serviceInfo.serviceName ?: ""
                val lowerName = serviceName.lowercase()
                Log.d(TAG, "NSD service found: $serviceName, type: ${serviceInfo.serviceType}")

                // Resolve if it matches AutoAlert or ESP8266 keywords or is an HTTP device
                val isPotentialMatch = lowerName.contains("autoalert") ||
                        lowerName.contains("esp8266") ||
                        lowerName.contains("rickshaw") ||
                        lowerName.contains("button") ||
                        lowerName.contains("auto")

                if (isPotentialMatch || !deferredDevice.isCompleted) {
                    if (isResolving.compareAndSet(false, true)) {
                        try {
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) {
                                    Log.w(TAG, "NSD resolve failed for ${info?.serviceName}: error $errorCode")
                                    isResolving.set(false)
                                }

                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                    isResolving.set(false)
                                    if (resolvedInfo == null || deferredDevice.isCompleted) return

                                    val hostInet = resolvedInfo.host
                                    val rawIp = hostInet?.hostAddress ?: ""
                                    // Clean any scope ID or leading slash from IP
                                    val numericIp = rawIp.replace("/", "").substringBefore("%").trim()

                                    Log.d(TAG, "NSD service resolved: name=${resolvedInfo.serviceName}, numericIp=$numericIp, port=${resolvedInfo.port}")

                                    if (isValidNumericIpv4(numericIp)) {
                                        deferredDevice.complete(
                                            DiscoveredDevice(
                                                ip = numericIp,
                                                hostname = resolvedInfo.serviceName ?: "autoalert",
                                                description = "Found via mDNS/NSD ($numericIp)",
                                                isReachable = true
                                            )
                                        )
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception calling resolveService: ${e.message}")
                            isResolving.set(false)
                        }
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "NSD service lost: ${serviceInfo?.serviceName}")
            }
        }

        try {
            // Android NsdManager expects "_http._tcp."
            nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            val result = withTimeoutOrNull(3200L) {
                deferredDevice.await()
            }

            if (isDiscoveryRunning.get()) {
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (e: Exception) {
                    Log.d(TAG, "Error stopping NSD discovery: ${e.message}")
                }
            }
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "NSD perform error: ${e.message}")
            return@withContext null
        }
    }

    private suspend fun checkDefaultApGateway(): DiscoveredDevice? = withContext(Dispatchers.IO) {
        val apIp = "192.168.4.1" // Standard ESP8266 Access Point IP
        try {
            if (isPortOpen(apIp, 80, 700)) {
                val ping = apiClient.pingDevice(apIp)
                if (ping.success) {
                    return@withContext DiscoveredDevice(
                        ip = apIp,
                        hostname = "autoalert",
                        description = "Direct ESP8266 AP Mode ($apIp)",
                        isReachable = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "AP gateway check error: ${e.message}")
        }
        null
    }

    private suspend fun checkSubnetGateways(): DiscoveredDevice? = withContext(Dispatchers.IO) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                @Suppress("DEPRECATION")
                val gatewayIp = Formatter.formatIpAddress(dhcp.gateway)
                if (isValidNumericIpv4(gatewayIp) && gatewayIp != "0.0.0.0" && gatewayIp != "127.0.0.1") {
                    if (isPortOpen(gatewayIp, 80, 500)) {
                        val ping = apiClient.pingDevice(gatewayIp)
                        if (ping.success) {
                            return@withContext DiscoveredDevice(
                                ip = gatewayIp,
                                hostname = "autoalert",
                                description = "Local Gateway Device ($gatewayIp)",
                                isReachable = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Subnet check error: ${e.message}")
        }
        null
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidNumericIpv4(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }
}

