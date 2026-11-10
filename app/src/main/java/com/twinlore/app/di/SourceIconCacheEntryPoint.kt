package com.twinlore.app.di

import com.twinlore.app.data.browser.SourceIconCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing [SourceIconCache] from non-injectable contexts
 * (the source menus' row composables, which resolve a host to a cached icon
 * file at composition time).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SourceIconCacheEntryPoint {
    fun sourceIconCache(): SourceIconCache
}
