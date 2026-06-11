package com.tplayer.app.di

import android.content.Context
import androidx.room.Room
import com.tplayer.app.BuildConfig
import com.tplayer.app.data.local.CatalogDao
import com.tplayer.app.data.local.SecureSessionStore
import com.tplayer.app.data.local.TPlayerDatabase
import com.tplayer.app.data.remote.ApiClient
import com.tplayer.app.data.remote.AuthRepository
import com.tplayer.app.data.remote.DownloadRepository
import com.tplayer.app.data.remote.SessionRepository
import com.tplayer.app.domain.session.SessionManager
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
    fun provideDatabase(@ApplicationContext context: Context): TPlayerDatabase =
        Room.databaseBuilder(context, TPlayerDatabase::class.java, "tplayer.db").build()

    @Provides
    fun provideCatalogDao(db: TPlayerDatabase): CatalogDao = db.catalogDao()

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
        AuthRepository(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun provideSessionRepository(
        @ApplicationContext context: Context,
        sessionStore: SecureSessionStore,
        authRepository: AuthRepository,
        sessionManager: SessionManager,
    ): SessionRepository = SessionRepository(context, sessionStore, authRepository, sessionManager)

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient(BuildConfig.API_BASE_URL)

    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        apiClient: ApiClient,
    ): DownloadRepository = DownloadRepository(context, apiClient)
}
