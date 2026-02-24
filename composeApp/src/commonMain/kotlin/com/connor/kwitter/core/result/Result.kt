package com.connor.kwitter.core.result

import arrow.core.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface Result<out T, out E> {
    data object Loading : Result<Nothing, Nothing>
    data class Success<T>(val data: T) : Result<T, Nothing>
    data class Error<E>(val error: E) : Result<Nothing, E>
}

fun <T> Flow<T>.asThrowableResult(): Flow<Result<T, Throwable>> =
    map<T, Result<T, Throwable>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { throwable -> emit(Result.Error(throwable)) }

fun <E, T> Flow<Either<E, T>>.asResult(): Flow<Result<T, E>> =
    map<Either<E, T>, Result<T, E>> { either ->
        either.fold(
            ifLeft = { error -> Result.Error(error) },
            ifRight = { value -> Result.Success(value) }
        )
    }.onStart { emit(Result.Loading) }

fun <E, T> Flow<Either<E, T>>.asResult(
    mapThrowable: (Throwable) -> E
): Flow<Result<T, E>> =
    asResult().catch { throwable -> emit(Result.Error(mapThrowable(throwable))) }

fun <E, UIE, T> Flow<Either<E, T>>.asResult(
    mapError: (E) -> UIE,
    mapThrowable: ((Throwable) -> UIE)? = null
): Flow<Result<T, UIE>> {
    val mappedFlow = map { either ->
        either.fold(
            ifLeft = { error -> Either.Left(mapError(error)) },
            ifRight = { value -> Either.Right(value) }
        )
    }
    return if (mapThrowable == null) {
        mappedFlow.asResult()
    } else {
        mappedFlow.asResult(mapThrowable)
    }
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
