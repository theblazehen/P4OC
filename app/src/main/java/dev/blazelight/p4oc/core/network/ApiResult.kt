package dev.blazelight.p4oc.core.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int? = null, val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw throwable ?: Exception(message)
    }

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (Error) -> Unit): ApiResult<T> {
        if (this is Error) action(this)
        return this
    }

    companion object {
        fun <T> success(data: T): ApiResult<T> = Success(data)
        fun error(message: String, code: Int? = null, throwable: Throwable? = null): ApiResult<Nothing> =
            Error(code, message, throwable)
    }
}

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: Exception) {
        ApiResult.Error(message = e.message ?: "Unknown error", throwable = e)
    }
}
