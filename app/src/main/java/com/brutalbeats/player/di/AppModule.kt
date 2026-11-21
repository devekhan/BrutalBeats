package com.brutalbeats.player.di

import android.content.Context
import androidx.room.Room
import com.brutalbeats.player.data.db.AppDatabase
import com.brutalbeats.player.data.db.EqPresetDao
import com.brutalbeats.player.data.db.FavoriteDao
import com.brutalbeats.player.data.db.PlaylistDao
import com.brutalbeats.player.data.db.QueueDao
import com.brutalbeats.player.data.db.SongDao
import com.brutalbeats.player.data.db.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Tells Hilt how to build the database, its DAOs and the app-wide coroutine scope. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "brutal_beats.db").build()

    @Provides fun provideSongDao(db: AppDatabase): SongDao = db.songDao()
    @Provides fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()
    @Provides fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideQueueDao(db: AppDatabase): QueueDao = db.queueDao()
    @Provides fun provideEqPresetDao(db: AppDatabase): EqPresetDao = db.eqPresetDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
