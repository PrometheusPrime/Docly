package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.data.local.dao.DiagnosticEventDao
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.data.local.mapper.toEntity
import com.docly.app.domain.model.DiagnosticEvent
import com.docly.app.domain.repository.DiagnosticsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class DiagnosticsRepositoryImpl @Inject constructor(
    private val diagnosticEventDao: DiagnosticEventDao,
    private val dispatcherProvider: DispatcherProvider
) : DiagnosticsRepository {
    override suspend fun record(event: DiagnosticEvent) = repositoryResult(dispatcherProvider) {
        diagnosticEventDao.insert(event.toEntity())
    }

    override fun observeRecent(limit: Int) = diagnosticEventDao.observeRecent(limit).map { events ->
        events.map { it.toDomain() }
    }
}
