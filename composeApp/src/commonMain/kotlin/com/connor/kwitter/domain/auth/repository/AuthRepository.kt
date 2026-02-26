package com.connor.kwitter.domain.auth.repository

import arrow.core.Either
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.auth.model.SessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun register(
        email: String,
        name: String,
        password: String
    ): Either<AuthError, Unit>

    suspend fun login(
        email: String,
        password: String
    ): Either<AuthError, Unit>

    suspend fun logout(): Either<AuthError, Unit>

    val session: Flow<SessionState>

    val authEvents: Flow<AuthEvent>

    val currentUserId: Flow<Long?>
}


