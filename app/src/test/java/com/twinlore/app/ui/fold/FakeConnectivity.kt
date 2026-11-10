package com.twinlore.app.ui.fold

import com.twinlore.app.data.NetworkConnectivityManager
import com.twinlore.app.data.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** A connectivity manager that reports [status] and records nothing else. */
internal fun onlineConnectivity(
    status: MutableStateFlow<NetworkStatus> =
        MutableStateFlow(NetworkStatus(hasInternet = true, isWifi = true, signalStrength = -50)),
): NetworkConnectivityManager = mock<NetworkConnectivityManager>().also {
    whenever(it.status).thenReturn(status)
}
