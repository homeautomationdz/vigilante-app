package com.vigilante.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    /** v1 → v2: admin-managed place lists (No Data Loss — additive only). */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `municipalities` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_municipalities_name` " +
                    "ON `municipalities` (`name`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `districts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`municipalityId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "FOREIGN KEY(`municipalityId`) REFERENCES `municipalities`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_districts_municipalityId_name` " +
                    "ON `districts` (`municipalityId`, `name`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_districts_municipalityId` " +
                    "ON `districts` (`municipalityId`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VigilanteDatabase =
        Room.databaseBuilder(context, VigilanteDatabase::class.java, "vigilante.db")
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    @Singleton
    fun provideAppFolders(@ApplicationContext context: Context): AppFolders =
        AppFolders(context).also { it.ensureAll() }
}
