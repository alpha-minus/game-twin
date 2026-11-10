package com.twinlore.app.di

import com.twinlore.app.data.EpubCacheManager
import com.twinlore.app.data.MhtCacheManager
import com.twinlore.app.data.MhtParser
import com.twinlore.app.data.OfflineGuideLoader
import com.twinlore.app.data.TxtConversionCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing OfflineGuideLoader, MhtParser, MhtCacheManager, and EpubCacheManager
 * from non-injectable contexts.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineGuideLoaderEntryPoint {
    fun offlineGuideLoader(): OfflineGuideLoader
    fun mhtParser(): MhtParser
    fun mhtCacheManager(): MhtCacheManager
    fun txtConversionCache(): TxtConversionCache
    fun epubCacheManager(): EpubCacheManager
}
