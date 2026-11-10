package com.twinlore.app.di

import com.twinlore.app.data.library.ResourceResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing ResourceResolver from non-injectable contexts
 * (e.g. composables in the Guide screen that need to resolve a SavedTab.resourceId
 * to a usable Uri).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ResourceResolverEntryPoint {
    fun resourceResolver(): ResourceResolver
}
