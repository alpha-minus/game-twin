package com.twinlore.app.di

import com.twinlore.app.data.SavedTabRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing SavedTabRepository from non-injectable contexts
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SavedTabRepositoryEntryPoint {
    fun savedTabRepository(): SavedTabRepository
}
