package com.kit.sms2mail.util.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [DataStoreManager] is a class that manages the storage and retrieval of data in the DataStore.
 *
 * It provides methods to save, read, clear, and read data as a flow.
 *
 * @property context The application context.
 */
class DataStoreManager(val context: Context) {
    //initialize datastore
    val Context.dataStore: DataStore<Preferences>
            by preferencesDataStore(
                name = StoreKeys.STORE_PREF
            )

    /**
     * Saves data to the DataStore.
     *
     * @param key The key to save the data under.
     * @param data The data to save.
     * @param T The type of the data to save.
     */
    suspend inline fun <reified T> save(key: String, data: T) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preference ->
                when (data) {
                    is String -> preference[stringPreferencesKey(key)] = data
                    is Int -> preference[intPreferencesKey(key)] = data
                    is Long -> preference[longPreferencesKey(key)] = data
                    is Float -> preference[floatPreferencesKey(key)] = data
                    is Double -> preference[doublePreferencesKey(key)] = data
                    is Boolean -> preference[booleanPreferencesKey(key)] = data
                    else -> preference[stringPreferencesKey(key)] = Json.encodeToString(data)
                }
            }
        }
    }

    /**
     * Clears the DataStore.
     *
     * @param nextJob The next job to run after clearing the DataStore.
     */
    suspend fun clear(nextJob: () -> Unit = {}) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preference ->
                preference.clear()
                nextJob.invoke()
            }
        }
    }

    /**
     * Reads data from the DataStore.
     *
     * @param key The key to read the data from.
     * @param defaultValue The default value to return if the key is not found.
     * @param T The type of the data to read.
     * @return The data read from the DataStore.
     */
    suspend inline fun <reified T> read(key: String, defaultValue: T): T {
        var isObject = false
        val prefKey: Preferences.Key<*> = when (defaultValue) {
            is String -> stringPreferencesKey(key)
            is Int -> intPreferencesKey(key)
            is Long -> longPreferencesKey(key)
            is Float -> floatPreferencesKey(key)
            is Double -> doublePreferencesKey(key)
            is Boolean -> booleanPreferencesKey(key)
            else -> {
                isObject = true
                stringPreferencesKey(key)
            }
        }
        val data = withContext(Dispatchers.IO) {
            context.dataStore.data
                .catch { exception ->
//                exception.message.log()
                    emit(emptyPreferences())
                }.first()[prefKey]
        }
        return when (data) {
            is String -> {
                if (isObject)
                    Json.decodeFromString(data)
                else
                    data as T
            }

            is Int -> data as T
            is Long -> data as T
            is Float -> data as T
            is Double -> data as T
            is Boolean -> data as T
            else -> defaultValue
        }
    }

    /**
     * Reads data from the DataStore as a flow.
     *
     * @param key The key to read the data from.
     * @param defaultValue The default value to return if the key is not found.
     * @param T The type of the data to read.
     * @return A flow of the data read from the DataStore.
     */
    inline fun <reified T> readAsFlow(key: String, defaultValue: T): Flow<T> {
        var isObject = false
        val prefKey: Preferences.Key<*> = when (defaultValue) {
            is String -> stringPreferencesKey(key)
            is Int -> intPreferencesKey(key)
            is Long -> longPreferencesKey(key)
            is Float -> floatPreferencesKey(key)
            is Double -> doublePreferencesKey(key)
            is Boolean -> booleanPreferencesKey(key)
            else -> {
                isObject = true
                stringPreferencesKey(key)
            }
        }
        return context.dataStore.data
            .catch { exception ->
//                exception.message.log()
                emit(emptyPreferences())
            }
            .map { preference ->
                when (val data = preference[prefKey]) {
                    is String -> {
                        if (isObject)
                            Json.decodeFromString(data)
                        else
                            data as T
                    }

                    is Int -> data as T
                    is Long -> data as T
                    is Float -> data as T
                    is Double -> data as T
                    is Boolean -> data as T
                    else -> defaultValue
                }
            }
    }

    /**
     * Clears a specific key from the DataStore.
     *
     * @param key The key to clear.
     * @param T The type of the data to clear.
     */
    suspend inline fun <reified T> clearKey(key: String) {
        val prefKey: Preferences.Key<*> = when (T::class) {
            String::class -> stringPreferencesKey(key)
            Int::class -> intPreferencesKey(key)
            Long::class -> longPreferencesKey(key)
            Float::class -> floatPreferencesKey(key)
            Double::class -> doublePreferencesKey(key)
            Boolean::class -> booleanPreferencesKey(key)
            else -> stringPreferencesKey(key)
        }
        withContext(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                preferences.remove(prefKey)
            }
        }
    }

}