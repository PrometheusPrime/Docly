package com.docly.app.data.repository

import androidx.room.withTransaction
import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.local.dao.ScanSessionDao
import com.docly.app.data.local.dao.ScannedPageDao
import com.docly.app.data.local.db.AppDatabase
import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.data.local.mapper.toEntity
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ScanRepository
import javax.inject.Inject

class ScanRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val scanSessionDao: ScanSessionDao,
    private val scannedPageDao: ScannedPageDao,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val fileRepository: FileRepository
) : ScanRepository {
    override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> =
        repositoryResult(dispatcherProvider) {
            val now = timeProvider.now()
            val session = ScanSession(
                id = idProvider.generateId(),
                createdAt = now,
                updatedAt = now,
                status = ScanSessionStatus.IN_PROGRESS,
                scanMode = scanMode
            )

            scanSessionDao.insert(session.toEntity())
            session
        }

    override suspend fun getSession(sessionId: String): AppResult<ScanSession?> {
        return repositoryResult(dispatcherProvider) {
            val session = scanSessionDao.getById(sessionId) ?: return@repositoryResult null
            val pages = scannedPageDao.getBySessionId(sessionId).map { it.toDomain() }
            session.toDomain(pages = pages)
        }
    }

    override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> {
        return repositoryResult(dispatcherProvider) {
            val session = scanSessionDao.getLatestByStatus(ScanSessionStatus.IN_PROGRESS.name)
                ?: return@repositoryResult null
            val pages = scannedPageDao.getBySessionId(session.id).map { it.toDomain() }
            session.toDomain(pages = pages)
        }
    }

    override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            val session = scanSessionDao.requireSession(sessionId)
            scanSessionDao.update(
                session.copy(
                    updatedAt = timeProvider.now(),
                    grade = metadata.grade,
                    subject = metadata.subject,
                    year = metadata.year,
                    paperType = metadata.paperType,
                    paperNumber = metadata.paperNumber,
                    source = metadata.source,
                    notes = metadata.notes
                )
            )
        }

    override suspend fun addPage(page: ScannedPage): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        database.withTransaction {
            scanSessionDao.requireSession(page.sessionId)
            scannedPageDao.insert(page.toEntity())
            touchSession(page.sessionId)
        }
    }

    override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        database.withTransaction {
            scanSessionDao.requireSession(page.sessionId)
            scannedPageDao.update(page.toEntity())
            touchSession(page.sessionId)
        }
    }

    override suspend fun deletePage(pageId: String): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        val deletedPage = database.withTransaction {
            val page = scannedPageDao.getById(pageId)
                ?: throw RepositoryFailure("Scanned page not found.", AppErrorCategory.VALIDATION)
            scannedPageDao.delete(page)
            touchSession(page.sessionId)
            page.toDomain()
        }
        fileRepository.deletePageAssets(deletedPage).throwOnError()
    }

    override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            database.withTransaction {
                scanSessionDao.requireSession(sessionId)
                val pages = scannedPageDao.getBySessionId(sessionId)
                val pageIds = pages.map { it.id }.toSet()
                val requestedIds = orderedPageIds.toSet()

                if (pageIds != requestedIds || orderedPageIds.size != requestedIds.size) {
                    throw RepositoryFailure(
                        message = "Ordered page IDs must match existing session pages.",
                        category = AppErrorCategory.VALIDATION
                    )
                }

                val pagesById = pages.associateBy { it.id }
                orderedPageIds.forEachIndexed { index, pageId ->
                    scannedPageDao.update(pagesById.getValue(pageId).copy(pageIndex = index))
                }
                touchSession(sessionId)
            }
        }

    override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            val session = scanSessionDao.requireSession(sessionId)
            scanSessionDao.update(
                session.copy(
                    updatedAt = timeProvider.now(),
                    status = status.name
                )
            )
        }

    private suspend fun touchSession(sessionId: String) {
        val session = scanSessionDao.requireSession(sessionId)
        scanSessionDao.update(session.copy(updatedAt = timeProvider.now()))
    }

    private suspend fun ScanSessionDao.requireSession(sessionId: String): ScanSessionEntity = getById(sessionId)
        ?: throw RepositoryFailure("Scan session not found.", AppErrorCategory.VALIDATION)
}
