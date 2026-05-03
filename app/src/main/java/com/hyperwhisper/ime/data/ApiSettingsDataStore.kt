package com.hyperwhisper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiSettingsDataStore

private val Context.apiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_api_settings",
)

@Module
@InstallIn(SingletonComponent::class)
object ApiSettingsDataStoreModule {

    @Provides
    @Singleton
    @ApiSettingsDataStore
    fun provideApiSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.apiDataStore
}
