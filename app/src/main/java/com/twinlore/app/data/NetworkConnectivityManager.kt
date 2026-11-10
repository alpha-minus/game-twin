package com.twinlore.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to check network connectivity
 */
@Singleton
class NetworkConnectivityManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "NetworkConnectivity"

        fun statusOf(capabilities: NetworkCapabilities): NetworkStatus = NetworkStatus(
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            signalStrength = capabilities.signalStrength
                .takeUnless { it == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
                ?: NetworkStatus.SIGNAL_UNKNOWN,
        )
    }

    /**
     * Check if device has active internet connection
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network connectivity", e)
            false
        }
    }

    /**
     * The connection the status bars draw: the library's, the vault session's
     * and the overlay panel's.
     *
     * Followed rather than read. Each bar used to take its own snapshot of
     * activeNetwork. The library's was retaken only when the battery level
     * changed, and the level that drained during sleep is delivered on wake,
     * before Wi-Fi has reassociated: offline, kept until the next percent,
     * which on a charger topped out at 100 is never. The panel's was a 15
     * second poll of activeNetwork, which is null for as long as the system
     * holds a backgrounded app's network blocked, and behind a game is where
     * the panel lives. A default-network callback reports the network itself.
     *
     * Started by the first read, for the process's lifetime: one callback is
     * cheaper than the bars registering their own each time they compose.
     */
    val status: StateFlow<NetworkStatus> by lazy { startMonitoring() }

    private val statusFlow = MutableStateFlow(NetworkStatus.OFFLINE)
    private val reducer = NetworkStatusReducer<Network>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    private fun startMonitoring(): StateFlow<NetworkStatus> {
        // Everything after this runs on the main thread, callbacks included
        // (they are registered with the main handler), so the reducer needs no
        // lock.
        mainHandler.post {
            resubscribe("start")
            // The safety net. Screen on reaches the overlay, which has no
            // activity and so never moves the process lifecycle; process start
            // covers coming back to the app from another one.
            runCatching {
                context.applicationContext.registerReceiver(
                    object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            resubscribe("screen on")
                        }
                    },
                    IntentFilter(Intent.ACTION_SCREEN_ON),
                    Context.RECEIVER_NOT_EXPORTED,
                )
            }.onFailure { Log.w(TAG, "Could not watch for screen on", it) }
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = resubscribe("app start")
                })
            }.onFailure { Log.w(TAG, "Could not watch the app lifecycle", it) }
        }
        return statusFlow.asStateFlow()
    }

    /**
     * Asks the system again, as a screen on does. For a caller that cannot
     * afford to trust the callback alone: the overlay handle sits over a game
     * for hours with nothing else to correct a missed event, so it re-asks on
     * a timer while it shows the connection. Starts monitoring if nothing had.
     */
    fun recheck(reason: String) {
        status
        mainHandler.post { resubscribe(reason) }
    }

    /**
     * Drops the callback and registers a new one, starting from what the
     * system reports now.
     *
     * A fresh registration is told the current default network straight away,
     * so whatever the old one missed while the device slept or the process was
     * frozen does not matter. activeNetwork is only the seed: it is null for an
     * app whose network is blocked, which a device that is connected is not,
     * and the callback reports the network either way.
     */
    private fun resubscribe(reason: String) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        callback?.let { old -> runCatching { cm.unregisterNetworkCallback(old) } }
        callback = null

        val seed = runCatching {
            val network = cm.activeNetwork
            network to network?.let { cm.getNetworkCapabilities(it) }?.let(::statusOf)
        }.getOrNull()
        publish(reducer.reset(seed?.first, seed?.second), reason)

        val fresh = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) =
                publish(reducer.onAvailable(network), "available")

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                publish(reducer.onCapabilitiesChanged(network, statusOf(capabilities)), null)

            override fun onLost(network: Network) =
                publish(reducer.onLost(network), "lost")
        }
        runCatching { cm.registerDefaultNetworkCallback(fresh, mainHandler) }
            .onSuccess { callback = fresh }
            .onFailure { Log.w(TAG, "Could not follow the default network", it) }
    }

    private fun publish(next: NetworkStatus, reason: String?) {
        val previous = statusFlow.value
        statusFlow.value = next
        // Capability changes carry every signal fluctuation; only log what the
        // bars would change their icon for.
        if (reason != null || previous.hasInternet != next.hasInternet || previous.isWifi != next.isWifi) {
            Log.d(TAG, "status ${reason ?: "capabilities"}: online=${next.hasInternet} wifi=${next.isWifi}")
        }
    }
}
