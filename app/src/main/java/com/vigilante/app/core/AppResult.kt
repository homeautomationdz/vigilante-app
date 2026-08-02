package com.vigilante.app.core

/** Simple result wrapper so expected errors never crash the app (SRS ch. 30). */
sealed class AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>()
    data class Failure(val userMessage: String, val cause: Throwable? = null) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}
