package com.vigilante.app.di

import android.content.Context
import androidx.room.Room
import com.vigilante.app.core.AppFolders
import com.vigilante.app.data.local.VigilanteDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): VigilanteDatabase =
        Room.databaseBuilder(context, VigilanteDatabase::class.java, "vigilante.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    @Singleton
    fun provideAppFolders(@ApplicationContext context: Context): AppFolders =
        AppFolders(context).also { it.ensureAll() }
}
