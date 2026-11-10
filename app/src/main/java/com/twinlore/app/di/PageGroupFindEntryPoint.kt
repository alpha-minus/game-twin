package com.twinlore.app.di

import com.twinlore.app.data.PageGroupSidecarCache
import com.twinlore.app.data.library.LibraryFolderManager
import com.twinlore.app.data.local.PageGroupEntryDao
import com.twinlore.app.data.local.ResourceFileDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for constructing [com.twinlore.app.data.library.PageGroupFindEngine]
 * from a Composable. The find engine reads sidecars from [PageGroupSidecarCache];
 * the viewer's find sheet uses [PageGroupSidecarCache.ensureForGroup] before the
 * first search to lazily regenerate any missing sidecars (e.g. for a group
 * restored from a library snapshot on this device, or after LRU eviction).
 *
 * [LibraryFolderManager] + [ResourceFileDao] stay exposed because the lazy
 * regen path needs to materialize the MHT file (file:// direct or content://
 * spill) to feed it through `MhtParser` + `PageGroupTextIndex`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PageGroupFindEntryPoint {
    fun resourceFileDao(): ResourceFileDao
    fun pageGroupEntryDao(): PageGroupEntryDao
    fun libraryFolderManager(): LibraryFolderManager
    fun sidecarCache(): PageGroupSidecarCache
}
