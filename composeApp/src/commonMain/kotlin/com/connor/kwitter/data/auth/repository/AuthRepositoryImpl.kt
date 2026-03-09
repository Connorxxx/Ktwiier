package com.connor.kwitter.data.auth.repository

import arrow.core.raise.context.Raise
import arrow.core.raise.fold
import com.connor.kwitter.data.auth.datasource.AuthRemoteDataSource
import com.connor.kwitter.data.auth.datasource.TokenDataSource
import com.connor.kwitter.data.auth.datasource.TokenRefresher
import com.connor.kwitter.data.notification.NotificationService
import com.connor.kwitter.domain.auth.model.AuthError
import com.connor.kwitter.domain.auth.model.AuthEvent
import com.connor.kwitter.domain.auth.model.AuthToken
import com.connor.kwitter.domain.auth.model.SessionState
import com.connor.kwitter.domain.auth.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenDataSource: TokenDataSource,
    private val tokenRefresher: TokenRefresher,
    private val notificationService: NotificationService
) : AuthRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var proactiveRefreshJob: Job? = null

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

    override val currentUserId: Flow<Long?> = tokenDataSource.currentUserId

    init {
        // Start/stop SSE connection based on session state
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
                    is AuthEvent.ForceLogout -> fold(
                        block = { tokenDataSource.clearTokens() },
                        catch = { throw it },
                        recover = {},
                        transform = {}
                    )
                }
            }
        }

        // Proactive token refresh at 80% of JWT lifetime
        repositoryScope.launch {
            tokenDataSource.tokens.collect { tokens ->
                proactiveRefreshJob?.cancel()
                if (tokens == null) return@collect

                proactiveRefreshJob = launch {
                    scheduleProactiveRefresh(tokens.expiresIn, tokens.obtainedAt)
                }
            }
        }
    }

    private suspend fun scheduleProactiveRefresh(expiresIn: Long, obtainedAt: Long) {
        val refreshAtMs = obtainedAt + (expiresIn * 800) // expiresIn is seconds, 80% → *800ms
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val delayMs = refreshAtMs - nowMs

        if (delayMs > 0) {
            delay(delayMs)
        }

        tokenRefresher.refresh()
    }

    context(_: Raise<AuthError>)
    override suspend fun register(
        email: String,
        name: String,
        password: String
    ) {
        val response = remoteDataSource.register(email, name, password)
        tokenDataSource.saveTokens(
            accessToken = response.token,
            refreshToken = response.refreshToken,
            userId = response.id,
            expiresIn = response.expiresIn
        )
    }

    context(_: Raise<AuthError>)
    override suspend fun login(
        email: String,
        password: String
    ) {
        val response = remoteDataSource.login(email, password)
        tokenDataSource.saveTokens(
            accessToken = response.token,
            refreshToken = response.refreshToken,
            userId = response.id,
            expiresIn = response.expiresIn
        )
    }

    context(_: Raise<AuthError>)
    override suspend fun logout() {
        notificationService.disconnect()
        tokenDataSource.clearTokens()
    }
}
