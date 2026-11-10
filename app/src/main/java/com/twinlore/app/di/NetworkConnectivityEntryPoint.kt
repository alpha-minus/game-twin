package com.twinlore.app.di

import com.twinlore.app.data.NetworkConnectivityManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing NetworkConnectivityManager from non-injectable contexts (e.g. composables).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkConnectivityEntryPoint {
    fun networkConnectivityManager(): NetworkConnectivityManager
}
