package com.connor.kwitter.data.auth.repository

import arrow.core.Either
import arrow.core.raise.either
import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.model.SessionState
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenDataSource: TokenDataSource,
    private val notificationService: NotificationService
) : AuthRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _session: StateFlow<SessionState> = tokenDataSource.tokens
        .map { tokens ->
            if (tokens != null) {
                SessionState.Authenticated(AuthToken(tokens.userId))
            } else {
                SessionState.Unauthenticated
            }
        }
        .stateIn(repositoryScope, SharingStarted.Eagerly, SessionState.Bootstrapping)

    override val session: Flow<SessionState> = _session

    override val authEvents: Flow<AuthEvent> = notificationService.authEvents

    override val currentUserId: Flow<String?> = tokenDataSource.currentUserId

    init {
        // Start/stop WebSocket based on session state
        repositoryScope.launch {
            _session.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> notificationService.connect(repositoryScope)
                    is SessionState.Unauthenticated -> notificationService.disconnect()
                    is SessionState.Bootstrapping -> Unit
                }
            }
        }

        // Observe force logout events → clear tokens
        repositoryScope.launch {
            notificationService.authEvents.collect { event ->
                when (event) {
                    is AuthEvent.ForceLogout -> tokenDataSource.clearTokens()
                }
            }
        }
    }

    override suspend fun register(
        email: String,
        name: String,
        password: String
    ): Either<AuthError, Unit> = either {
        val response = remoteDataSource.register(email, name, password).bind()
        tokenDataSource.saveTokens(
            accessToken = response.token,
            refreshToken = response.refreshToken,
            userId = response.id
        ).bind()
    }

    override suspend fun login(
        email: String,
        password: String
    ): Either<AuthError, Unit> = either {
        val response = remoteDataSource.login(email, password).bind()
        tokenDataSource.saveTokens(
            accessToken = response.token,
            refreshToken = response.refreshToken,
            userId = response.id
        ).bind()
    }

    override suspend fun logout(): Either<AuthError, Unit> {
        notificationService.disconnect()
        return tokenDataSource.clearTokens()
    }
}
