package com.twinlore.app.di

import com.twinlore.app.data.library.MapThumbnailCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing MapThumbnailCache from non-injectable contexts
 * (the Maps picker composable, which cannot take a constructor parameter).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapThumbnailCacheEntryPoint {
    fun mapThumbnailCache(): MapThumbnailCache
}
