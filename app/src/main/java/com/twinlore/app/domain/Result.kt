package com.twinlore.app.domain

/**
 * Sealed class representing the result of an operation
 * Either Success with data or Failure with error
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: Throwable) : Result<Nothing>() {
        constructor(message: String) : this(Exception(message))
    }
    /** A fuzzy match was found — caller should confirm with the user before using the existing data. */
    data class FuzzyMatch<out T>(val data: T, val similarity: Double) : Result<T>()

    companion object {
        /**
         * Create a success result
         */
        fun <T> success(value: T): Result<T> = Success(value)

        /**
         * Create a failure result
         */
        fun <T> failure(error: Throwable): Result<T> = Failure(error)
        fun <T> failure(message: String): Result<T> = Failure(message)
    }

    /**
     * Get the data if success, null otherwise
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is FuzzyMatch -> data
        is Failure -> null
    }

    /**
     * Get the data or throw the exception
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is FuzzyMatch -> data
        is Failure -> throw error
    }

    /**
     * Check if result is success
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * Check if result is failure
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * Execute block if success
     */
    inline fun onSuccess(action: (value: T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute block if failure
     */
    inline fun onFailure(action: (error: Throwable) -> Unit): Result<T> {
        if (this is Failure) action(error)
        return this
    }
}


