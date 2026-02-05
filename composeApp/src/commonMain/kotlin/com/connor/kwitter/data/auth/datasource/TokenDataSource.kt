package com.connor.kwitter.data.auth.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Token 数据源
 * 负责从 DataStore 读写认证令牌
 */
class TokenDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }

    /**
     * 获取存储的令牌
     */
    val token: Flow<AuthToken?> = dataStore.data
        .map { preferences -> preferences[TOKEN_KEY] }
        .map { token -> token?.let { AuthToken(it) } }
        .catch { emit(null) }

    /**
     * 保存令牌
     */
    suspend fun saveToken(token: AuthToken): Either<AuthError, Unit> = either {
        try {
            ensure(token.token.isNotBlank()) {
                AuthError.InvalidCredentials("Token cannot be blank")
            }

            dataStore.edit { preferences ->
                preferences[TOKEN_KEY] = token.token
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to save token: ${e.message}"))
        }
    }

    /**
     * 清除令牌
     */
    suspend fun clearToken(): Either<AuthError, Unit> = either {
        try {
            dataStore.edit { preferences ->
                preferences.remove(TOKEN_KEY)
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to clear token: ${e.message}"))
        }
    }
}
