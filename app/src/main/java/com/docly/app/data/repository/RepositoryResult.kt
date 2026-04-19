package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import kotlinx.coroutines.withContext

internal class RepositoryFailure(
    override val message: String,
    val category: AppErrorCategory,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

internal suspend fun <T> repositoryResult(
    dispatcherProvider: DispatcherProvider,
    block: suspend () -> T
): AppResult<T> = try {
    AppResult.Success(withContext(dispatcherProvider.io) { block() })
} catch (failure: RepositoryFailure) {
    AppResult.Error(
        message = failure.message,
        category = failure.category,
        throwable = failure.cause
    )
} catch (throwable: Throwable) {
    AppResult.Error(
        message = throwable.message ?: "Storage operation failed.",
        category = AppErrorCategory.STORAGE,
        throwable = throwable
    )
}

internal fun AppResult<Unit>.throwOnError() {
    if (this is AppResult.Error) {
        throw RepositoryFailure(
            message = message,
            category = category,
            cause = throwable
        )
    }
}
