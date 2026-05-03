package com.hyperwhisper.security

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
annotation class SecretsDataStoreQualifier

private val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_secrets",
)

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    @SecretsDataStoreQualifier
    fun provideSecretsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.secretsDataStore

    @Provides
    @Singleton
    fun provideSecretCipher(): SecretCipher = AndroidKeystoreSecretCipher()

    @Provides
    @Singleton
    fun provideSecretsRepository(
        @SecretsDataStoreQualifier dataStore: DataStore<Preferences>,
        cipher: SecretCipher,
    ): SecretsRepository = SecretsRepository(dataStore, cipher)

    @Provides
    @Singleton
    fun provideBiometricGate(@ApplicationContext context: Context): BiometricGate =
        AndroidBiometricGate(context)
}
