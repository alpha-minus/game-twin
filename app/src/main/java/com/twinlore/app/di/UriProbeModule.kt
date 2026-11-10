package com.twinlore.app.di

import com.twinlore.app.data.DocumentsContractUriProbe
import com.twinlore.app.data.UriProbe
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UriProbeModule {
    @Binds
    @Singleton
    abstract fun bindUriProbe(impl: DocumentsContractUriProbe): UriProbe
}
