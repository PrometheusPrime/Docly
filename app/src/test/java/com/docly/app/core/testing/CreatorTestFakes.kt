package com.docly.app.core.testing

import com.docly.app.core.pdf.HtmlToPdfExporter
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.data.storage.DoclyStorageManager
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.DocumentRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDocumentRepository(documents: List<DoclyDocument> = emptyList()) : DocumentRepository {
    private val documentsFlow = MutableStateFlow(documents)
    var upsertError: AppResult.Error? = null
    val documents: List<DoclyDocument>
        get() = documentsFlow.value

    override fun observeDocuments(): Flow<List<DoclyDocument>> = documentsFlow

    override fun searchDocuments(query: String): Flow<List<DoclyDocument>> = documentsFlow.map { documents ->
        documents.filter { document -> document.name.contains(query, ignoreCase = true) }
    }

    override suspend fun getDocument(documentId: String): AppResult<DoclyDocument?> =
        AppResult.Success(documentsFlow.value.firstOrNull { document -> document.id == documentId })

    override suspend fun importDocument(uriString: String): AppResult<DoclyDocument> = AppResult.Error(
        message = "Import is not available in this test.",
        category = AppErrorCategory.VALIDATION
    )

    override suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit> {
        upsertError?.let { error -> return error }
        documentsFlow.value = documentsFlow.value.filterNot { existing -> existing.id == document.id } + document
        return AppResult.Success(Unit)
    }

    override suspend fun renameDocument(documentId: String, name: String): AppResult<Unit> {
        documentsFlow.value = documentsFlow.value.map { document ->
            if (document.id == documentId) document.copy(name = name) else document
        }
        return AppResult.Success(Unit)
    }

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
        documentsFlow.value = documentsFlow.value.filterNot { document -> document.id == documentId }
        return AppResult.Success(Unit)
    }

    override suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit> {
        documentsFlow.value = documentsFlow.value.map { document ->
            if (document.id == documentId) document.copy(isFavorite = isFavorite) else document
        }
        return AppResult.Success(Unit)
    }

    override suspend fun updateLastOpened(documentId: String): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeDoclyStorageManager(private val rootDirectory: File) : DoclyStorageManager {
    val createdPaths = mutableListOf<String>()
    val deletedPaths = mutableListOf<String>()
    var createError: AppResult.Error? = null

    override suspend fun createDocumentFile(name: String, type: DocumentType): AppResult<String> {
        createError?.let { error -> return error }
        val directory = File(rootDirectory, type.name.lowercase(Locale.US)).apply { mkdirs() }
        val file = File(directory, name.toSafeName(type.defaultExtension())).uniqueFile()
        createdPaths += file.absolutePath
        return AppResult.Success(file.absolutePath)
    }

    override suspend fun copyUriToInternalStorage(
        uriString: String,
        targetName: String?,
        type: DocumentType?
    ): AppResult<String> = AppResult.Error(
        message = "Import is not available in this test.",
        category = AppErrorCategory.VALIDATION
    )

    override suspend fun deleteFile(fileRef: FileRef): AppResult<Unit> {
        val path = (fileRef as? FileRef.InternalFile)?.path ?: return AppResult.Success(Unit)
        deletedPaths += path
        File(path).delete()
        return AppResult.Success(Unit)
    }

    override suspend fun getFileSize(fileRef: FileRef): AppResult<Long> {
        val path = (fileRef as? FileRef.InternalFile)?.path ?: return AppResult.Success(0L)
        return AppResult.Success(File(path).length())
    }

    private fun File.uniqueFile(): File {
        if (!exists()) return this
        val extension = extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        var candidate: File
        do {
            candidate = File(parentFile, "${nameWithoutExtension}_$index$extension")
            index += 1
        } while (candidate.exists())
        return candidate
    }

    private fun String.toSafeName(defaultExtension: String): String {
        val safeName = trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.')
            .ifBlank { "document" }
        return if (safeName.contains('.') || defaultExtension.isBlank()) safeName else "$safeName.$defaultExtension"
    }

    private fun DocumentType.defaultExtension(): String = when (this) {
        DocumentType.PDF -> "pdf"
        DocumentType.TXT -> "txt"
        DocumentType.MARKDOWN -> "md"
        DocumentType.HTML -> "html"
        DocumentType.DOCX -> "docx"
        DocumentType.XLSX -> "xlsx"
        DocumentType.CSV -> "csv"
        DocumentType.IMAGE -> "jpg"
        DocumentType.UNKNOWN -> ""
    }
}

class FakeHtmlToPdfExporter : HtmlToPdfExporter {
    var lastHtml: String? = null
    var error: AppResult.Error? = null

    override suspend fun generate(html: String, outputPdfPath: String): AppResult<String> {
        lastHtml = html
        error?.let { exportError -> return exportError }
        File(outputPdfPath).apply {
            parentFile?.mkdirs()
            writeText("%PDF-1.4\n", Charsets.UTF_8)
        }
        return AppResult.Success(outputPdfPath)
    }
}
