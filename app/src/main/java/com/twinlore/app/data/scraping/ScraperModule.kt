package com.twinlore.app.data.scraping

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {

    @Provides
    @Singleton
    fun provideTheGamesDbApi(): TheGamesDbApi {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.thegamesdb.net/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TheGamesDbApi::class.java)
    }

    @Provides
    @Singleton
    fun provideArtworkScrapers(
        sgdb: SteamGridClient,
        tgdb: TheGamesDbArtworkScraper,
    ): List<ArtworkScraper> = listOf(sgdb, tgdb)
}
