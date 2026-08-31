package com.tonezen.app.di

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.remote.catalog.CatalogRemoteApi
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.data.remote.bookwatch.BookWatchRemoteApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object RemoteApiModule {
    private fun apiRoot(): String = "${BuildConfig.BASE_URL.trimEnd('/')}/api/v1"

    @Provides
    @Singleton
    fun provideCatalogRemoteApi(httpClient: OkHttpClient): CatalogRemoteApi =
        CatalogRemoteApi(apiRoot(), httpClient)

    @Provides
    @Singleton
    fun provideDownloadsRemoteApi(httpClient: OkHttpClient): DownloadsRemoteApi =
        DownloadsRemoteApi(apiRoot(), httpClient)

    @Provides
    @Singleton
    fun provideProgressRemoteApi(httpClient: OkHttpClient): ProgressRemoteApi =
        ProgressRemoteApi(apiRoot(), httpClient)

    @Provides @Singleton
    fun provideBookWatchRemoteApi(httpClient: OkHttpClient): BookWatchRemoteApi =
        BookWatchRemoteApi(apiRoot(), httpClient)
}
