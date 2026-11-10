package com.twinlore.app.di

import android.content.Context
import androidx.room.Room
import com.twinlore.app.data.local.VaultDatabase
import com.twinlore.app.data.local.VaultDatabaseMigration
import com.twinlore.app.data.NetworkConnectivityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Database module for Room database dependency injection
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideVaultDatabase(
        @ApplicationContext context: Context
    ): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_database"
        )
            .addMigrations(*VaultDatabaseMigration.ALL)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideVaultDao(database: VaultDatabase) = database.vaultDao()
    
    @Provides
    fun provideSavedTabDao(database: VaultDatabase) = database.savedTabDao()
    
    @Provides
    fun provideRaGameCacheDao(database: VaultDatabase) = database.raGameCacheDao()

    @Provides
    fun provideSteamGameCacheDao(database: VaultDatabase) = database.steamGameCacheDao()

    @Provides
    fun provideSavedMapDao(database: VaultDatabase) = database.savedMapDao()

    @Provides
    fun provideChecklistTagDao(database: VaultDatabase) = database.checklistTagDao()

    @Provides
    fun provideChecklistItemDao(database: VaultDatabase) = database.checklistItemDao()

    @Provides
    fun provideChecklistTabDao(database: VaultDatabase) = database.checklistTabDao()

    @Provides
    fun provideChecklistSectionDao(database: VaultDatabase) = database.checklistSectionDao()

    @Provides
    fun provideFavoriteAppDao(database: VaultDatabase) = database.favoriteAppDao()

    @Provides
    fun provideResourceFileDao(database: VaultDatabase) = database.resourceFileDao()

    @Provides
    fun provideMapMarkerDao(database: VaultDatabase) = database.mapMarkerDao()

    @Provides
    fun providePageBookmarkDao(database: VaultDatabase) = database.pageBookmarkDao()

    @Provides
    fun provideScrollMarkerDao(database: VaultDatabase) = database.scrollMarkerDao()

    @Provides
    fun provideMediaSearchIndexDao(database: VaultDatabase) = database.mediaSearchIndexDao()

    @Provides
    fun provideMediaSearchPathDao(database: VaultDatabase) = database.mediaSearchPathDao()

    @Provides
    fun provideMediaSearchDirDao(database: VaultDatabase) = database.mediaSearchDirDao()

    @Provides
    fun provideLibraryMetaDao(database: VaultDatabase) = database.libraryMetaDao()

    @Provides
    fun provideBrowserTabDao(database: VaultDatabase) = database.browserTabDao()

    @Provides
    fun providePageGroupEntryDao(database: VaultDatabase) = database.pageGroupEntryDao()

    @Provides
    fun providePageGroupEntryAnchorDao(database: VaultDatabase) = database.pageGroupEntryAnchorDao()

    @Provides
    fun provideVaultSyncStateDao(database: VaultDatabase) = database.vaultSyncStateDao()

    @Provides
    fun provideSyncTombstoneDao(database: VaultDatabase) = database.syncTombstoneDao()

    @Provides
    fun providePlaySessionDao(database: VaultDatabase) = database.playSessionDao()

    @Provides
    fun provideJourneyCacheDao(database: VaultDatabase) = database.journeyCacheDao()

    @Provides
    @Singleton
    fun provideNetworkConnectivityManager(
        @ApplicationContext context: Context
    ): NetworkConnectivityManager {
        return NetworkConnectivityManager(context)
    }
}

