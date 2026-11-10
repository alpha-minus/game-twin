package com.twinlore.app.di

import com.twinlore.app.data.library.ArtworkThumbnailCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing ArtworkThumbnailCache from non-injectable contexts
 * (the Library carousel composable, which can't take a constructor parameter).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArtworkThumbnailCacheEntryPoint {
    fun artworkThumbnailCache(): ArtworkThumbnailCache
}
