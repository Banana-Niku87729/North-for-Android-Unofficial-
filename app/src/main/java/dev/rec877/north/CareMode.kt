package dev.rec877.north

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

data class CareConnection(val wifi: Boolean = false, val ssid: String? = null, val mobile: Boolean = false, val vpn: Boolean = false)

fun shouldActivateCare(enabled: Boolean, connection: CareConnection, trusted: Set<String>): Boolean =
    enabled && (!connection.wifi || connection.mobile || connection.vpn || connection.ssid == null || connection.ssid !in trusted)

/** 接続情報と許可リストは端末内だけに保持する。判別できない接続は許可済みとみなさない。 */
class CareModeController(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("care_mode", Context.MODE_PRIVATE)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var receiver: BroadcastReceiver? = null
    private var serial = 0
    private var currentNetwork: Network? = null
    var enabled by mutableStateOf(prefs.getBoolean("enabled", false))
        private set
    var localDetection by mutableStateOf(prefs.getBoolean("local_detection", true))
        private set
    var trusted by mutableStateOf(prefs.getStringSet("trusted_wifi", emptySet()).orEmpty().toSet())
        private set
    var connection by mutableStateOf(CareConnection())
        private set
    var epoch by mutableStateOf(0)
        private set
    var foreground by mutableStateOf(false)
        private set
    val active: Boolean get() = shouldActivateCare(enabled, connection, trusted)

    fun updateEnabled(value: Boolean) {
        enabled = value; epoch++
        prefs.edit().putBoolean("enabled", value).apply()
        restart()
    }
    fun updateLocalDetection(value: Boolean) {
        localDetection = value; epoch++
        prefs.edit().putBoolean("local_detection", value).apply()
    }
    fun addTrusted(ssid: String) {
        if (ssid.isEmpty() || ssid == WifiManager.UNKNOWN_SSID) return
        trusted = trusted + ssid; epoch++
        prefs.edit().putStringSet("trusted_wifi", trusted).apply()
    }
    fun removeTrusted(ssid: String) {
        trusted = trusted - ssid; epoch++
        prefs.edit().putStringSet("trusted_wifi", trusted).apply()
    }
    fun canReadSsid(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (app.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ||
                app.getSystemService(LocationManager::class.java)?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true)

    fun restart() { stop(); start() }
    fun start() {
        foreground = true
        if (callback != null || !enabled) return
        // 最初のcallbackが来るまでは表示を解禁しない。
        update(CareConnection())
        val token = ++serial
        val cb = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onAvailable(network: Network) { if (token == serial) { currentNetwork = network; update(CareConnection()) } }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { if (token == serial && network == currentNetwork) accept(caps) }
                override fun onLost(network: Network) { if (token == serial && network == currentNetwork) { currentNetwork = null; update(CareConnection()) } }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { if (token == serial) { currentNetwork = network; update(CareConnection()) } }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { if (token == serial && network == currentNetwork) accept(caps) }
                override fun onLost(network: Network) { if (token == serial && network == currentNetwork) { currentNetwork = null; update(CareConnection()) } }
            }
        }
        callback = cb
        try {
            connectivity.registerDefaultNetworkCallback(cb, handler)
            val locationChanges = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { if (token == serial) restart() }
            }
            ContextCompat.registerReceiver(app, locationChanges, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = locationChanges
        }
        catch (_: Exception) { stop() }
    }
    @Suppress("DEPRECATION")
    private fun accept(caps: NetworkCapabilities) {
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val raw = if (wifi && canReadSsid()) {
            val info = if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null
            (info ?: if (Build.VERSION.SDK_INT < 31) app.getSystemService(WifiManager::class.java)?.connectionInfo else null)?.ssid
        } else null
        val ssid = raw?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
        update(CareConnection(wifi, ssid, caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)))
    }
    private fun update(value: CareConnection) {
        if (connection != value) { connection = value; epoch++ }
    }
    fun stop() {
        foreground = false
        serial++
        currentNetwork = null
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        // バックグラウンド復帰時も以前の表示許可を持ち越さない。
        update(CareConnection()); epoch++
    }
}
