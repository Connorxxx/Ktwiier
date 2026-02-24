package com.connor.kwitter.data.auth.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.domain.auth.model.AuthError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresIn: Long,
    val obtainedAt: Long
)

class TokenDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
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

    val currentUserId: Flow<String?> = dataStore.data
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

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        expiresIn: Long
    ): Either<AuthError, Unit> = either {
        try {
            dataStore.edit { preferences ->
                preferences[ACCESS_TOKEN_KEY] = accessToken
                preferences[REFRESH_TOKEN_KEY] = refreshToken
                preferences[USER_ID_KEY] = userId
                preferences[EXPIRES_IN_KEY] = expiresIn
                preferences[OBTAINED_AT_KEY] = Clock.System.now().toEpochMilliseconds()
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to save tokens: ${e.message}"))
        }
    }

    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ): Either<AuthError, Unit> = either {
        try {
            dataStore.edit { preferences ->
                preferences[ACCESS_TOKEN_KEY] = accessToken
                preferences[REFRESH_TOKEN_KEY] = refreshToken
                preferences[EXPIRES_IN_KEY] = expiresIn
                preferences[OBTAINED_AT_KEY] = Clock.System.now().toEpochMilliseconds()
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to update tokens: ${e.message}"))
        }
    }

    suspend fun clearTokens(): Either<AuthError, Unit> = either {
        try {
            dataStore.edit { preferences ->
                preferences.remove(ACCESS_TOKEN_KEY)
                preferences.remove(REFRESH_TOKEN_KEY)
                preferences.remove(USER_ID_KEY)
                preferences.remove(EXPIRES_IN_KEY)
                preferences.remove(OBTAINED_AT_KEY)
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to clear tokens: ${e.message}"))
        }
    }
}
