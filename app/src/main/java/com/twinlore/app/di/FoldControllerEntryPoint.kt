package com.twinlore.app.di

import com.twinlore.app.ui.fold.FoldController
import com.twinlore.app.ui.fold.OverlayHintContent
import com.twinlore.app.ui.fold.OverlayMemory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing FoldController from composables, which have no
 * constructor to inject into.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FoldControllerEntryPoint {
    fun foldController(): FoldController
    fun overlayMemory(): OverlayMemory
    fun foldHandover(): com.twinlore.app.ui.fold.FoldHandover
    fun overlayHintContent(): OverlayHintContent
}
