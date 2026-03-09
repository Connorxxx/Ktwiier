package com.connor.kwitter.data.auth.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import arrow.core.raise.catch
import com.connor.kwitter.domain.auth.model.AuthError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
    val expiresIn: Long,
    val obtainedAt: Long
)

class TokenDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val USER_ID_KEY = longPreferencesKey("user_id")
        val EXPIRES_IN_KEY = longPreferencesKey("expires_in")
        val OBTAINED_AT_KEY = longPreferencesKey("obtained_at")
    }

    val tokens: Flow<StoredTokens?> = dataStore.data
        .map { preferences ->
            val accessToken = preferences[ACCESS_TOKEN_KEY] ?: return@map null
            val refreshToken = preferences[REFRESH_TOKEN_KEY] ?: return@map null
            val userId = preferences[USER_ID_KEY] ?: return@map null
            val expiresIn = preferences[EXPIRES_IN_KEY] ?: return@map null
            val obtainedAt = preferences[OBTAINED_AT_KEY] ?: return@map null
            StoredTokens(accessToken, refreshToken, userId, expiresIn, obtainedAt)
        }
        .catch { emit(null) }

    val currentUserId: Flow<Long?> = dataStore.data
        .map { preferences -> preferences[USER_ID_KEY] }
        .catch { emit(null) }

    suspend fun getAccessToken(): String? =
        dataStore.data.first()[ACCESS_TOKEN_KEY]

    suspend fun getRefreshToken(): String? =
        dataStore.data.first()[REFRESH_TOKEN_KEY]

    suspend fun getExpiresIn(): Long? =
        dataStore.data.first()[EXPIRES_IN_KEY]

    suspend fun getObtainedAt(): Long? =
        dataStore.data.first()[OBTAINED_AT_KEY]

    context(_: Raise<AuthError>)
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        expiresIn: Long
    ) = catch({
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[USER_ID_KEY] = userId
            preferences[EXPIRES_IN_KEY] = expiresIn
            preferences[OBTAINED_AT_KEY] = Clock.System.now().toEpochMilliseconds()
        }
    }) {
        raise(AuthError.StorageError("Failed to save tokens: ${it.message}"))
    }

    context(_: Raise<AuthError>)
    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ) = catch({
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[EXPIRES_IN_KEY] = expiresIn
            preferences[OBTAINED_AT_KEY] = Clock.System.now().toEpochMilliseconds()
        }
    }) {
        raise(AuthError.StorageError("Failed to update tokens: ${it.message}"))
    }

    context(_: Raise<AuthError>)
    suspend fun clearTokens() = catch({
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
            preferences.remove(USER_ID_KEY)
            preferences.remove(EXPIRES_IN_KEY)
            preferences.remove(OBTAINED_AT_KEY)
        }
    }) {
        raise(AuthError.StorageError("Failed to clear tokens: ${it.message}"))
    }
}
