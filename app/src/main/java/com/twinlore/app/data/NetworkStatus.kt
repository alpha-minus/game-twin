package com.twinlore.app.data

/**
 * What the status bars draw for the connection: whether there is a network
 * that carries the internet, whether it is Wi-Fi, and how strong it is.
 *
 * [signalStrength] is the raw RSSI from the network's capabilities, or
 * [SIGNAL_UNKNOWN] when the network does not report one. Turning it into bars
 * is the bar's business.
 */
data class NetworkStatus(
    val hasInternet: Boolean,
    val isWifi: Boolean,
    val signalStrength: Int,
) {
    companion object {
        /** Mirrors NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED. */
        const val SIGNAL_UNKNOWN = Int.MIN_VALUE

        val OFFLINE = NetworkStatus(hasInternet = false, isWifi = false, signalStrength = SIGNAL_UNKNOWN)
    }
}

/**
 * Folds default-network callbacks into a [NetworkStatus].
 *
 * Pure so the ordering cases can be tested without a ConnectivityManager. [K]
 * is the network handle, android.net.Network in the app.
 *
 * Every network's last capabilities are kept, keyed by network, and the status
 * is read from whichever one is the default now. A lost network only clears
 * itself: when the default moves from A to B, B's arrival can come before A's
 * loss, and a reducer holding a single "connected" flag lets that late loss
 * turn a connected device offline until something else happens to arrive.
 */
class NetworkStatusReducer<K> {

    private var current: K? = null
    private val known = LinkedHashMap<K, NetworkStatus>()

    val status: NetworkStatus
        get() = current?.let { known[it] } ?: NetworkStatus.OFFLINE

    /** [network] is the default now. Its capabilities follow, if not already known. */
    fun onAvailable(network: K): NetworkStatus {
        current = network
        return status
    }

    /**
     * New capabilities for [network]. A default-network callback only reports
     * the default, so this also makes [network] current: a capabilities change
     * that arrived without its onAvailable (a re-registration, a missed event
     * while the process was frozen) still counts.
     */
    fun onCapabilitiesChanged(network: K, capabilities: NetworkStatus): NetworkStatus {
        known[network] = capabilities
        current = network
        return status
    }

    /** [network] is gone. Offline only when it was the default. */
    fun onLost(network: K): NetworkStatus {
        known.remove(network)
        if (current == network) current = null
        return status
    }

    /**
     * Starts again from what the system reports right now: [network] with
     * [capabilities], or nothing when there is no default network. Used when
     * the callback is registered afresh, so nothing from before can linger.
     */
    fun reset(network: K?, capabilities: NetworkStatus?): NetworkStatus {
        known.clear()
        current = null
        if (network != null && capabilities != null) {
            known[network] = capabilities
            current = network
        }
        return status
    }
}
