package com.connor.kwitter.domain.auth.repository

import arrow.core.raise.context.Raise
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.auth.model.SessionState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    context(_: Raise<AuthError>)
    suspend fun register(
        email: String,
        name: String,
        password: String
    )

    context(_: Raise<AuthError>)
    suspend fun login(
        email: String,
        password: String
    )

    context(_: Raise<AuthError>)
    suspend fun logout()

    val session: Flow<SessionState>

    val authEvents: Flow<AuthEvent>

    val currentUserId: Flow<Long?>
}


