package com.docly.app.core

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.LOW_STORAGE_USER_MESSAGE
import com.docly.app.core.result.defaultUserMessage
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.flatMap
import com.docly.app.core.result.fold
import com.docly.app.core.result.getOrNull
import com.docly.app.core.result.isError
import com.docly.app.core.result.isSuccess
import com.docly.app.core.result.map
import com.docly.app.core.result.onError
import com.docly.app.core.result.onSuccess
import com.docly.app.core.result.toUserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {
    @Test
    fun onSuccessRunsForSuccessOnly() {
        var successValue = ""
        var errorCalled = false

        AppResult.Success("ok")
            .onSuccess { successValue = it }
            .onError { errorCalled = true }

        assertEquals("ok", successValue)
        assertFalse(errorCalled)
    }

    @Test
    fun onErrorRunsForErrorOnly() {
        var successCalled = false
        var category = AppErrorCategory.UNKNOWN

        AppResult.Error("failed", AppErrorCategory.STORAGE)
            .onSuccess { successCalled = true }
            .onError { category = it.category }

        assertFalse(successCalled)
        assertEquals(AppErrorCategory.STORAGE, category)
    }

    @Test
    fun resultHelpersReturnOriginalInstance() {
        val result = AppResult.Success(42)

        val returned = result.onSuccess { assertEquals(42, it) }

        assertTrue(result === returned)
    }

    @Test
    fun statusHelpersIdentifySuccessAndErrorResults() {
        val success = AppResult.Success("ok")
        val error = AppResult.Error("failed")

        assertTrue(success.isSuccess)
        assertFalse(success.isError)
        assertTrue(error.isError)
        assertFalse(error.isSuccess)
    }

    @Test
    fun getOrNullReturnsSuccessDataOnly() {
        assertEquals("ok", AppResult.Success("ok").getOrNull())
        assertNull(AppResult.Error("failed").getOrNull())
    }

    @Test
    fun errorOrNullReturnsErrorOnly() {
        val error = AppResult.Error("failed", AppErrorCategory.PDF)

        assertSame(error, error.errorOrNull())
        assertNull(AppResult.Success("ok").errorOrNull())
    }

    @Test
    fun foldDispatchesToMatchingBranch() {
        val successResult = AppResult.Success(3).fold(
            onSuccess = { value -> value * 2 },
            onError = { 0 }
        )
        val errorResult = AppResult.Error("failed").fold(
            onSuccess = { "success" },
            onError = { error -> error.category.name }
        )

        assertEquals(6, successResult)
        assertEquals(AppErrorCategory.UNKNOWN.name, errorResult)
    }

    @Test
    fun mapTransformsSuccessAndPreservesError() {
        val error = AppResult.Error("failed", AppErrorCategory.STORAGE)

        val mappedSuccess = AppResult.Success(4).map { it * 2 }
        val mappedError = error.map { value: Int -> value * 2 }

        assertEquals(8, mappedSuccess.getOrNull())
        assertSame(error, mappedError.errorOrNull())
    }

    @Test
    fun flatMapChainsSuccessAndPreservesError() {
        val error = AppResult.Error("failed", AppErrorCategory.PROCESSING)

        val chainedSuccess = AppResult.Success(4).flatMap { AppResult.Success(it * 2) }
        val chainedError = error.flatMap { value: Int -> AppResult.Success(value * 2) }

        assertEquals(8, chainedSuccess.getOrNull())
        assertSame(error, chainedError.errorOrNull())
    }

    @Test
    fun defaultUserMessageMapsEveryErrorCategory() {
        AppErrorCategory.entries.forEach { category ->
            assertTrue(category.defaultUserMessage().isNotBlank())
        }
    }

    @Test
    fun validationErrorUsesSpecificMessageForUserMessage() {
        val error = AppResult.Error(
            message = "Year must be between 1980 and 2027.",
            category = AppErrorCategory.VALIDATION
        )

        assertEquals("Year must be between 1980 and 2027.", error.toUserMessage())
    }

    @Test
    fun nonValidationErrorUsesDefaultUserMessage() {
        val error = AppResult.Error(
            message = "java.io.FileNotFoundException: /internal/path",
            category = AppErrorCategory.STORAGE
        )

        assertEquals(AppErrorCategory.STORAGE.defaultUserMessage(), error.toUserMessage())
    }

    @Test
    fun lowStorageErrorUsesSpecificUserMessage() {
        val error = AppResult.Error(
            message = LOW_STORAGE_USER_MESSAGE,
            category = AppErrorCategory.STORAGE
        )

        assertEquals(LOW_STORAGE_USER_MESSAGE, error.toUserMessage())
    }
}
