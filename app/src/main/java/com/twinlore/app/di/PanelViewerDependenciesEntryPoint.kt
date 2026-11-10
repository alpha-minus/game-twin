package com.twinlore.app.di

import com.twinlore.app.data.PageGroupRepository
import com.twinlore.app.data.SavedTabRepository
import com.twinlore.app.data.library.ResourceResolver
import com.twinlore.app.data.local.PageGroupEntryDao
import com.twinlore.app.data.local.SavedTabDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * What the app's guide view models are built from.
 *
 * The fold panel constructs them itself. hiltViewModel() needs an activity it
 * does not have, and Hilt refuses direct injection of an @HiltViewModel class,
 * so the way through is to build one by hand from its dependencies. Every
 * dependency here is already a singleton, which is the fact that makes that
 * possible at all.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PanelViewerDependenciesEntryPoint {
    fun savedTabDao(): SavedTabDao
    fun pageGroupEntryDao(): PageGroupEntryDao
    fun pageGroupRepository(): PageGroupRepository
    fun savedTabRepository(): SavedTabRepository
    fun resourceResolver(): ResourceResolver
}
