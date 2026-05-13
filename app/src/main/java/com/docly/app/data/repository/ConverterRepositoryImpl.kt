package com.docly.app.data.repository

import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.local.dao.ConversionJobDao
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.data.local.mapper.toEntity
import com.docly.app.data.storage.DoclyStorageManager
import com.docly.app.domain.converter.ConverterOutput
import com.docly.app.domain.converter.ConverterRegistry
import com.docly.app.domain.model.ConversionJob
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.ConversionResult
import com.docly.app.domain.model.ConversionStatus
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.repository.ConverterRepository
import com.docly.app.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConverterRepositoryImpl @Inject constructor(
    private val conversionJobDao: ConversionJobDao,
    private val documentRepository: DocumentRepository,
    private val storageManager: DoclyStorageManager,
    private val converterRegistry: ConverterRegistry,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) : ConverterRepository {
    override fun getSupportedOutputs(inputType: DocumentType): List<DocumentType> =
        converterRegistry.getSupportedOutputs(inputType)

    override suspend fun createJob(request: ConversionRequest): AppResult<ConversionJob> {
        val job = request.toQueuedJob(id = idProvider.generateId(), now = timeProvider.now())
        return when (val result = persist(job)) {
            is AppResult.Error -> result
            is AppResult.Success -> AppResult.Success(job)
        }
    }

    override fun observeJob(jobId: String): Flow<ConversionJob?> =
        conversionJobDao.observeJob(jobId).map { job -> job?.toDomain() }

    override fun observeRecentJobs(limit: Int): Flow<List<ConversionJob>> =
        conversionJobDao.observeRecent(limit).map { jobs -> jobs.map { job -> job.toDomain() } }

    override suspend fun convert(request: ConversionRequest): AppResult<ConversionResult> {
        var currentJob = request.toQueuedJob(id = idProvider.generateId(), now = timeProvider.now())
        var outputPath: String? = null

        suspend fun fail(error: AppResult.Error): AppResult.Error {
            outputPath?.let { path -> storageManager.deleteFile(FileRef.InternalFile(path)) }
            currentJob = currentJob.copy(
                status = ConversionStatus.FAILED,
                progress = 100,
                errorMessage = error.message,
                updatedAt = timeProvider.now()
            )
            persist(currentJob)
            return error
        }

        when (val persistResult = persist(currentJob)) {
            is AppResult.Error -> return persistResult
            is AppResult.Success -> Unit
        }

        val sourceDocument = when (val documentResult = loadInputDocument(request)) {
            is AppResult.Error -> return fail(documentResult)
            is AppResult.Success -> documentResult.data
        }
        currentJob = currentJob.copy(inputType = sourceDocument.type, updatedAt = timeProvider.now())

        val engine = converterRegistry.findEngine(sourceDocument.type, request.outputType)
            ?: return fail(unsupportedConversionError(sourceDocument.type, request.outputType))

        val normalizedRequest = request.copy(inputType = sourceDocument.type)
        outputPath = when (
            val outputPathResult = storageManager.createDocumentFile(
                name = normalizedRequest.outputFileName,
                type = normalizedRequest.outputType
            )
        ) {
            is AppResult.Error -> return fail(outputPathResult)
            is AppResult.Success -> outputPathResult.data
        }

        currentJob = currentJob.copy(
            status = ConversionStatus.RUNNING,
            progress = RUNNING_PROGRESS,
            outputPath = outputPath,
            updatedAt = timeProvider.now()
        )
        when (val persistResult = persist(currentJob)) {
            is AppResult.Error -> return fail(persistResult)
            is AppResult.Success -> Unit
        }

        val output = when (
            val conversionResult = engine.convert(
                request = normalizedRequest,
                sourceDocument = sourceDocument,
                outputPath = outputPath
            )
        ) {
            is AppResult.Error -> return fail(conversionResult)
            is AppResult.Success -> conversionResult.data
        }

        val outputDocument = output.toDocument(sourceDocument = sourceDocument, request = normalizedRequest)
        when (val saveResult = documentRepository.upsertDocument(outputDocument)) {
            is AppResult.Error -> return fail(saveResult)
            is AppResult.Success -> Unit
        }

        currentJob = currentJob.copy(
            status = ConversionStatus.COMPLETED,
            progress = COMPLETED_PROGRESS,
            outputPath = output.outputPath,
            outputDocumentId = outputDocument.id,
            errorMessage = null,
            updatedAt = timeProvider.now()
        )
        when (val persistResult = persist(currentJob)) {
            is AppResult.Error -> {
                documentRepository.deleteDocument(outputDocument.id)
                return fail(persistResult)
            }

            is AppResult.Success -> Unit
        }

        return AppResult.Success(
            ConversionResult(
                job = currentJob,
                outputDocument = outputDocument,
                outputPath = output.outputPath
            )
        )
    }

    private suspend fun loadInputDocument(request: ConversionRequest): AppResult<DoclyDocument> {
        val inputDocumentId = request.inputDocumentId?.trim().orEmpty()
        if (inputDocumentId.isBlank()) {
            return validationError("Choose a document to convert.")
        }

        return when (val result = documentRepository.getDocument(inputDocumentId)) {
            is AppResult.Error -> result

            is AppResult.Success -> result.data?.let { document -> AppResult.Success(document) }
                ?: validationError("Document not found.")
        }
    }

    private suspend fun persist(job: ConversionJob): AppResult<Unit> = try {
        withContext(dispatcherProvider.io) {
            conversionJobDao.upsert(job.toEntity())
        }
        AppResult.Success(Unit)
    } catch (throwable: Throwable) {
        AppResult.Error(
            message = "Could not save conversion job.",
            category = AppErrorCategory.STORAGE,
            throwable = throwable
        )
    }

    private fun ConversionRequest.toQueuedJob(id: String, now: Long): ConversionJob = ConversionJob(
        id = id,
        inputDocumentId = inputDocumentId,
        inputUri = inputUri,
        inputType = inputType,
        outputType = outputType,
        outputPath = null,
        outputDocumentId = null,
        status = ConversionStatus.QUEUED,
        progress = QUEUED_PROGRESS,
        errorMessage = null,
        createdAt = now,
        updatedAt = now
    )

    private fun ConverterOutput.toDocument(sourceDocument: DoclyDocument, request: ConversionRequest): DoclyDocument {
        val now = timeProvider.now()
        val outputFile = File(outputPath)
        return DoclyDocument(
            id = idProvider.generateId(),
            name = request.outputFileName.toDisplayName(request.outputType),
            type = request.outputType,
            mimeType = mimeType ?: request.outputType.defaultMimeType(),
            fileRef = FileRef.InternalFile(outputPath),
            source = DocumentSource.CONVERTED,
            folderId = sourceDocument.folderId,
            thumbnailPath = null,
            fileSize = outputFile.length(),
            pageCount = pageCount,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
            isFavorite = false,
            isScanned = false,
            ocrStatus = OcrStatus.NOT_STARTED
        )
    }

    private fun String.toDisplayName(type: DocumentType): String {
        val extension = type.defaultExtension()
        return trim()
            .removeSuffix(".$extension")
            .removeSuffix(".${extension.uppercase()}")
            .ifBlank { "Converted document" }
    }

    private fun DocumentType.defaultExtension(): String = when (this) {
        DocumentType.PDF -> "pdf"
        DocumentType.TXT -> "txt"
        DocumentType.MARKDOWN -> "md"
        DocumentType.HTML -> "html"
        DocumentType.CSV -> "csv"
        DocumentType.DOCX -> "docx"
        DocumentType.XLSX -> "xlsx"
        DocumentType.IMAGE -> "jpg"
        DocumentType.UNKNOWN -> ""
    }

    private fun DocumentType.defaultMimeType(): String = when (this) {
        DocumentType.PDF -> "application/pdf"
        DocumentType.TXT -> "text/plain"
        DocumentType.MARKDOWN -> "text/markdown"
        DocumentType.HTML -> "text/html"
        DocumentType.CSV -> "text/csv"
        DocumentType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DocumentType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        DocumentType.IMAGE -> "image/jpeg"
        DocumentType.UNKNOWN -> "application/octet-stream"
    }

    private companion object {
        const val QUEUED_PROGRESS = 0
        const val RUNNING_PROGRESS = 10
        const val COMPLETED_PROGRESS = 100
    }
}

private fun unsupportedConversionError(inputType: DocumentType, outputType: DocumentType): AppResult.Error =
    validationError("${inputType.label} to ${outputType.label} is not supported yet.")

private fun validationError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.VALIDATION
)

private val DocumentType.label: String
    get() = when (this) {
        DocumentType.PDF -> "PDF"
        DocumentType.TXT -> "TXT"
        DocumentType.MARKDOWN -> "Markdown"
        DocumentType.HTML -> "HTML"
        DocumentType.DOCX -> "DOCX"
        DocumentType.XLSX -> "XLSX"
        DocumentType.CSV -> "CSV"
        DocumentType.IMAGE -> "Image"
        DocumentType.UNKNOWN -> "Unknown"
    }
