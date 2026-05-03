package com.docly.app.core.result

enum class AppErrorCategory {
    CAMERA,
    PERMISSION,
    PROCESSING,
    STORAGE,
    PDF,
    VALIDATION,
    UNKNOWN
}

const val LOW_STORAGE_USER_MESSAGE = "Not enough storage space to save this document."

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()

    data class Error(
        val message: String,
        val category: AppErrorCategory = AppErrorCategory.UNKNOWN,
        val throwable: Throwable? = null
    ) : AppResult<Nothing>()
}

val <T> AppResult<T>.isSuccess: Boolean
    get() = this is AppResult.Success

val <T> AppResult<T>.isError: Boolean
    get() = this is AppResult.Error

fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> data
    is AppResult.Error -> null
}

fun <T> AppResult<T>.errorOrNull(): AppResult.Error? = when (this) {
    is AppResult.Success -> null
    is AppResult.Error -> this
}

inline fun <T, R> AppResult<T>.fold(onSuccess: (T) -> R, onError: (AppResult.Error) -> R): R = when (this) {
    is AppResult.Success -> onSuccess(data)
    is AppResult.Error -> onError(this)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Error -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) {
        block(data)
    }
    return this
}

inline fun <T> AppResult<T>.onError(block: (AppResult.Error) -> Unit): AppResult<T> {
    if (this is AppResult.Error) {
        block(this)
    }
    return this
}

fun AppErrorCategory.defaultUserMessage(): String = when (this) {
    AppErrorCategory.CAMERA -> "Camera is unavailable. Please try again."
    AppErrorCategory.PERMISSION -> "Permission is required to continue."
    AppErrorCategory.PROCESSING -> "We could not process this page. Please try again."
    AppErrorCategory.STORAGE -> "We could not save or load this file. Please try again."
    AppErrorCategory.PDF -> "We could not create the PDF. Please try again."
    AppErrorCategory.VALIDATION -> "Please check the required information and try again."
    AppErrorCategory.UNKNOWN -> "Something went wrong. Please try again."
}

fun AppResult.Error.toUserMessage(): String = if (
    category == AppErrorCategory.VALIDATION &&
    message.isNotBlank()
) {
    message
} else if (
    category == AppErrorCategory.STORAGE &&
    message == LOW_STORAGE_USER_MESSAGE
) {
    message
} else {
    category.defaultUserMessage()
}
