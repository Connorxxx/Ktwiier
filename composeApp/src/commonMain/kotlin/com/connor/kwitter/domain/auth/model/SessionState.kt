package com.connor.kwitter.domain.auth.model

sealed interface SessionState {
    /**
     * 应用启动后尚未完成本地会话恢复/校验。
     * UI 应停留在 Splash，避免先进入 Login 再跳 Home。
     */
    data object Bootstrapping : SessionState

    data class Authenticated(val token: AuthToken) : SessionState
    data object Unauthenticated : SessionState
}
