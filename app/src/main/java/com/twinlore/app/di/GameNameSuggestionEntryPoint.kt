package com.twinlore.app.di

import com.twinlore.app.data.GameNameSuggestionProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing GameNameSuggestionProvider from non-injectable contexts
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameNameSuggestionEntryPoint {
    fun gameNameSuggestionProvider(): GameNameSuggestionProvider
}
