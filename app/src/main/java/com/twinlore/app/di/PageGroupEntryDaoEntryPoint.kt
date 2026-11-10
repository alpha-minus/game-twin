package com.twinlore.app.di

import com.twinlore.app.data.local.PageGroupEntryDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing [PageGroupEntryDao] from non-injectable contexts
 * such as Composables. Mirrors the SavedTabRepositoryEntryPoint pattern.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PageGroupEntryDaoEntryPoint {
    fun pageGroupEntryDao(): PageGroupEntryDao
}
