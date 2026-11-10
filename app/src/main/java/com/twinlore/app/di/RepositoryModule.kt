package com.twinlore.app.di

import com.twinlore.app.journey.data.JourneyDataSource
import com.twinlore.app.journey.data.JourneyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository module for repository dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindJourneyDataSource(impl: JourneyRepository): JourneyDataSource
}



