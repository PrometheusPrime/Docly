package com.docly.app.core.testing

import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DiagnosticEvent
import com.docly.app.domain.repository.DiagnosticsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FixedIdProvider(private val id: String) : IdProvider {
    override fun generateId(): String = id
}

class SequenceIdProvider(ids: List<String>) : IdProvider {
    private val iterator = ids.iterator()

    override fun generateId(): String {
        check(iterator.hasNext()) { "No more test IDs are available." }
        return iterator.next()
    }
}

class FixedTimeProvider(private val timestampMillis: Long) : TimeProvider {
    override fun now(): Long = timestampMillis
}

class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class NoOpAppLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun warning(tag: String, message: String, throwable: Throwable?) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

class NoOpDiagnosticsRepository : DiagnosticsRepository {
    val events = mutableListOf<DiagnosticEvent>()

    override suspend fun record(event: DiagnosticEvent): AppResult<Unit> {
        events += event
        return AppResult.Success(Unit)
    }

    override fun observeRecent(limit: Int): Flow<List<DiagnosticEvent>> = flowOf(events.take(limit))
}
