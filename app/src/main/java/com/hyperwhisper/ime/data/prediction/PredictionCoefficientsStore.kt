package com.hyperwhisper.data.prediction

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PredictionDataStore

private val Context.predictionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hyperwhisper_prediction",
)

@Module
@InstallIn(SingletonComponent::class)
object PredictionDataStoreModule {

    @Provides
    @Singleton
    @PredictionDataStore
    fun providePredictionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.predictionDataStore
}

/**
 * Persists the recomputed-on-demand [PredictionCoefficients] as a single JSON
 * blob. All I/O is best-effort: failures degrade to [PredictionCoefficients.EMPTY]
 * so a bad read can never break a transcription's progress estimate.
 */
@Singleton
class PredictionCoefficientsStore @Inject constructor(
    @PredictionDataStore private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
) {
    private val key = stringPreferencesKey("coefficients_json")

    suspend fun load(): PredictionCoefficients {
        return try {
            val json = dataStore.data.first()[key] ?: return PredictionCoefficients.EMPTY
            gson.fromJson(json, PredictionCoefficients::class.java) ?: PredictionCoefficients.EMPTY
        } catch (t: Throwable) {
            PredictionCoefficients.EMPTY
        }
    }

    suspend fun save(coefficients: PredictionCoefficients) {
        try {
            dataStore.edit { it[key] = gson.toJson(coefficients) }
        } catch (t: Throwable) {
            // best-effort — losing a calibration just means the next estimate
            // falls back to the byte heuristic until the user recalculates.
        }
    }
}
