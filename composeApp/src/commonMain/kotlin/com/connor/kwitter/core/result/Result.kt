package com.connor.kwitter.core.result

import arrow.core.raise.Raise
import arrow.core.raise.Effect
import arrow.core.raise.effect
import arrow.core.raise.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface Result<out T, out E> {
    data object Loading : Result<Nothing, Nothing>
    data class Success<T>(val data: T) : Result<T, Nothing>
    data class Error<E>(val error: E) : Result<Nothing, E>
}

internal fun <E, UIE, T> resultFlow(
    mapError: (E) -> UIE,
    block: suspend Raise<E>.() -> T
): Flow<Result<T, UIE>> {
    return flow {
        emit(Result.Loading)
        emit(
            runRaise(block).fold(
                recover = { error -> Result.Error(mapError(error)) },
                transform = { value -> Result.Success(value) }
            )
        )
    }
}

internal fun <E, UIE, T> resultFlow(
    mapError: (E) -> UIE,
    mapThrowable: (Throwable) -> UIE,
    block:  suspend Raise<E>.() -> T
): Flow<Result<T, UIE>> {
    return flow {
        emit(Result.Loading)
        emit(
            runRaise(block).fold(
                catch = { throwable -> Result.Error(mapThrowable(throwable)) },
                recover = { error -> Result.Error(mapError(error)) },
                transform = { value -> Result.Success(value) }
            )
        )
    }
}

private fun <E, T> runRaise(
    block: suspend Raise<E>.() -> T
): Effect<E, T> {
    return effect(block)
}

@Suppress("UNCHECKED_CAST")
fun <T, E> Result<T, E>.errorOrNull(): E? = when (this) {
    Result.Loading -> null
    is Result.Success -> null
    is Result.Error<*> -> error as E
}

fun <E> uiResultOf(
    isLoading: Boolean,
    error: E?
): Result<Unit, E> = when {
    isLoading -> Result.Loading
    error != null -> Result.Error(error)
    else -> Result.Success(Unit)
}
