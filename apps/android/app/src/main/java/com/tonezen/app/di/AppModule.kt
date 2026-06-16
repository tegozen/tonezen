package com.tonezen.app.di

import android.content.Context
import androidx.room.Room
import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.SecureSessionStore
import com.tonezen.app.data.local.TonezenDatabaseMigrations
import com.tonezen.app.data.local.TonezenDatabase
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.AvatarRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.data.remote.UserProfileMirrorRepository
import com.tonezen.app.domain.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TonezenDatabase =
        Room.databaseBuilder(context, TonezenDatabase::class.java, "tonezen.db")
            .addMigrations(*TonezenDatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideDownloadQueueDao(db: TonezenDatabase): DownloadQueueDao = db.downloadQueueDao()

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
    fun provideAvatarRepository(httpClient: OkHttpClient): AvatarRepository =
        AvatarRepository(BuildConfig.BASE_URL, BuildConfig.SUPABASE_ANON_KEY, httpClient)

    @Provides
    @Singleton
    fun provideUserProfileMirrorRepository(httpClient: OkHttpClient): UserProfileMirrorRepository =
        UserProfileMirrorRepository(BuildConfig.BASE_URL, BuildConfig.SUPABASE_ANON_KEY, httpClient)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
}
