package com.twinlore.app.di

import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Provides bindings that [ApkDownloadManager] and [GitHubApkResolver] need
 * beyond what Hilt can infer from their @Inject constructors (parameters with
 * Kotlin default values are invisible to Dagger's Java codegen).
 */
@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {

    @Provides
    fun provideSupportedAbis(): Array<String> = Build.SUPPORTED_ABIS

    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideGitHubBaseApiUrl(): String = "https://api.github.com"
}
