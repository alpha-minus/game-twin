package com.twinlore.app.di

import com.twinlore.app.data.license.LicenseManager
import com.twinlore.app.data.license.LicenseManagerUiStateContract
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the license layer. Exposes [LicenseManager] as the
 * implementation of [LicenseManagerUiStateContract] so components can depend
 * on the narrower interface without pulling in the full concrete class.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LicenseBindingModule {

    @Binds
    @Singleton
    abstract fun bindLicenseManagerUiStateContract(
        impl: LicenseManager,
    ): LicenseManagerUiStateContract
}
