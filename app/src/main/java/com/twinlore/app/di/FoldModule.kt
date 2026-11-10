package com.twinlore.app.di

import com.twinlore.app.ui.fold.AndroidFoldPlatform
import com.twinlore.app.ui.fold.DataStoreOverlayMemory
import com.twinlore.app.ui.fold.FoldPlatform
import com.twinlore.app.ui.fold.OverlayHintContent
import com.twinlore.app.ui.fold.OverlayMemory
import com.twinlore.app.ui.fold.VaultOverlayHintContent
import com.twinlore.app.ui.fold.guide.AndroidFoldGuideData
import com.twinlore.app.ui.fold.guide.AndroidFoldGuidePanelContentFactory
import com.twinlore.app.ui.fold.guide.FoldGuideData
import com.twinlore.app.ui.fold.guide.FoldGuidePanelContentFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FoldModule {

    @Binds
    @Singleton
    abstract fun bindFoldPlatform(impl: AndroidFoldPlatform): FoldPlatform

    @Binds
    @Singleton
    abstract fun bindOverlayMemory(impl: DataStoreOverlayMemory): OverlayMemory

    @Binds
    @Singleton
    abstract fun bindOverlayHintContent(impl: VaultOverlayHintContent): OverlayHintContent

    @Binds
    @Singleton
    abstract fun bindFoldGuideData(impl: AndroidFoldGuideData): FoldGuideData

    @Binds
    @Singleton
    abstract fun bindFoldGuidePanelContentFactory(
        impl: AndroidFoldGuidePanelContentFactory,
    ): FoldGuidePanelContentFactory
}
