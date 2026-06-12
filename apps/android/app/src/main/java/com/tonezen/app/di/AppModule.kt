package com.tonezen.app.di

import android.content.Context
import androidx.room.Room
import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.data.local.SecureSessionStore
import com.tonezen.app.data.local.TonezenDatabase
import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TonezenDatabase =
        Room.databaseBuilder(context, TonezenDatabase::class.java, "tonezen.db").build()

    @Provides
    fun provideCatalogDao(db: TonezenDatabase): CatalogDao = db.catalogDao()

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SecureSessionStore =
        SecureSessionStore(context)

    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager = SessionManager()

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository =
        AuthRepository(BuildConfig.BASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient(BuildConfig.BASE_URL)

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        apiClient: ApiClient,
    ): DownloadRepository = DownloadRepository(context, apiClient)
}
