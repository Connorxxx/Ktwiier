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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/**
 * Token 数据源
 * 负责从 DataStore 读写认证令牌
 */
class TokenDataSource(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val jwtPayloadDecoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /**
     * 获取存储的令牌
     */
    val token: Flow<AuthToken?> = dataStore.data
        .map { preferences ->
            val token = preferences[TOKEN_KEY]
            val userId = preferences[USER_ID_KEY]
            token?.let {
                AuthToken(
                    token = it,
                    userId = userId ?: extractUserIdFromJwt(it)
                )
            }
        }
        .catch { emit(null) }

    /**
     * 获取当前用户ID
     */
    val currentUserId: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[USER_ID_KEY] ?: preferences[TOKEN_KEY]?.let(::extractUserIdFromJwt)
        }
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
                val resolvedUserId = token.userId
                    ?.takeIf { it.isNotBlank() }
                    ?: extractUserIdFromJwt(token.token)
                if (resolvedUserId != null) {
                    preferences[USER_ID_KEY] = resolvedUserId
                } else {
                    preferences.remove(USER_ID_KEY)
                }
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
                preferences.remove(USER_ID_KEY)
            }
        } catch (e: Exception) {
            raise(AuthError.StorageError("Failed to clear token: ${e.message}"))
        }
    }

    private fun extractUserIdFromJwt(token: String): String? {
        val payloadSegment = token.split('.').getOrNull(1) ?: return null
        val payload = runCatching {
            jwtPayloadDecoder.decode(payloadSegment).decodeToString()
        }.getOrNull() ?: return null

        return runCatching {
            json.parseToJsonElement(payload)
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
