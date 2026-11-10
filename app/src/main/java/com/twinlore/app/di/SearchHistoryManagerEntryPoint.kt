package com.twinlore.app.di

import com.twinlore.app.data.SearchHistoryManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing SearchHistoryManager from non-injectable contexts
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SearchHistoryManagerEntryPoint {
    fun searchHistoryManager(): SearchHistoryManager
}
