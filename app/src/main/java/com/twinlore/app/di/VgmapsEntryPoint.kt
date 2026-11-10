package com.twinlore.app.di

import android.content.Context
import com.twinlore.app.data.library.VgmapsBulkDownloader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoints
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VgmapsEntryPoint {
    fun vgmapsBulkDownloader(): VgmapsBulkDownloader

    companion object {
        fun get(context: Context): VgmapsEntryPoint =
            EntryPoints.get(
                context.applicationContext,
                VgmapsEntryPoint::class.java,
            )
    }
}
