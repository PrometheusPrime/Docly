# Low-Level Design

## 1. Design Target

Docly is a local-first Android document utility. It supports scanning, reading, creating, editing, converting, managing, and sharing documents.

Low-level implementation rule:

```text
Do not directly edit every file type.
Edit formats Docly can own safely.
Use internal models where needed.
Export new copies to target formats.
```

This keeps MVP behavior realistic while leaving room for advanced document tools later.

## 2. Package Structure

Use a single `:app` module for MVP unless build times or ownership boundaries require module extraction.

```text
com.docly.app
|-- app
|   |-- di
|   |-- navigation
|   `-- MainActivity.kt
|-- core
|   |-- common
|   |-- dispatchers
|   |-- errors
|   |-- file
|   |-- permissions
|   |-- result
|   |-- settings
|   `-- time
|-- data
|   |-- local
|   |   |-- dao
|   |   |-- db
|   |   `-- entity
|   |-- mapper
|   |-- parser
|   |-- repository
|   |-- storage
|   `-- converter
|-- domain
|   |-- capability
|   |-- model
|   |-- repository
|   `-- usecase
|-- documents
|-- scanner
|-- reader
|-- creator
|-- editor
|-- converter
|-- tools
|-- ocr
|-- settings
`-- ui
```

## 3. Core Domain Models

### Document Type

```kotlin
enum class DocumentType {
    PDF,
    TXT,
    MARKDOWN,
    HTML,
    DOCX,
    XLSX,
    CSV,
    IMAGE,
    UNKNOWN
}
```

### Document Source

```kotlin
enum class DocumentSource {
    INTERNAL,
    EXTERNAL_URI,
    SCANNED,
    IMPORTED,
    CREATED,
    CONVERTED
}
```

### File Reference

```kotlin
sealed class FileRef {
    data class InternalFile(val path: String) : FileRef()
    data class ExternalUri(val uri: String) : FileRef()
}
```

### OCR Status

```kotlin
enum class OcrStatus {
    NOT_STARTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    NOT_SUPPORTED
}
```

### Docly Document

```kotlin
data class DoclyDocument(
    val id: String,
    val name: String,
    val type: DocumentType,
    val mimeType: String?,
    val fileRef: FileRef,
    val source: DocumentSource,
    val folderId: String?,
    val thumbnailPath: String?,
    val fileSize: Long,
    val pageCount: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val isFavorite: Boolean,
    val isScanned: Boolean,
    val ocrStatus: OcrStatus
)
```

### Document Capabilities

```kotlin
data class DocumentCapabilities(
    val canView: Boolean,
    val canCreate: Boolean,
    val canEdit: Boolean,
    val canAnnotate: Boolean,
    val canConvert: Boolean,
    val supportedOutputs: Set<DocumentType>,
    val isSimplifiedView: Boolean,
    val limitationMessage: String? = null
)
```

Capability defaults:

- `PDF`: view, create, convert/share; page tools only for Docly-created scan PDFs; annotate later.
- `TXT`: view, create, edit, convert to TXT/PDF/HTML/Markdown.
- `MARKDOWN`: view, create, edit, convert to Markdown/HTML/PDF/TXT.
- `HTML`: view, create, source edit, convert to HTML/PDF/TXT.
- `IMAGE`: view, create from scanner/import, crop/rotate/filter in scan review, convert to PDF.
- `DOCX`: simplified view after core readers; later TXT/simple HTML conversion; no direct editing.
- `XLSX`: simplified table view after core readers; later CSV/TXT conversion; no full spreadsheet editing.

## 4. Database Design

Room stores metadata and job state only. Files store document content.

### Document Entity

```kotlin
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val mimeType: String?,
    val filePath: String?,
    val uri: String?,
    val source: String,
    val folderId: String?,
    val thumbnailPath: String?,
    val fileSize: Long,
    val pageCount: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val isFavorite: Boolean,
    val isScanned: Boolean,
    val ocrStatus: String
)
```

### Folder Entity

```kotlin
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

### Scan Session

```kotlin
@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val outputDocumentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String
)
```

### Scan Page

```kotlin
@Entity(
    tableName = "scan_pages",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ScanPageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val imagePath: String,
    val originalImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val filterType: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

### Conversion Job

```kotlin
@Entity(tableName = "conversion_jobs")
data class ConversionJobEntity(
    @PrimaryKey val id: String,
    val inputDocumentId: String?,
    val inputUri: String?,
    val inputType: String,
    val outputType: String,
    val outputPath: String?,
    val outputDocumentId: String?,
    val status: String,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

### OCR Result

```kotlin
@Entity(tableName = "ocr_results")
data class OcrResultEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int?,
    val text: String,
    val language: String?,
    val confidence: Float?,
    val createdAt: Long,
    val updatedAt: Long
)
```

## 5. DAO Layer

### Document DAO

```kotlin
@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchByName(query: String): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE documents SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun updateLastOpened(id: String, openedAt: Long)

    @Delete
    suspend fun delete(document: DocumentEntity)
}
```

### Scan DAO

```kotlin
@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: ScanPageEntity)

    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun observePages(sessionId: String): Flow<List<ScanPageEntity>>

    @Query("DELETE FROM scan_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("UPDATE scan_pages SET pageIndex = :pageIndex, updatedAt = :updatedAt WHERE id = :pageId")
    suspend fun updatePageIndex(pageId: String, pageIndex: Int, updatedAt: Long)
}
```

### Conversion Job DAO

```kotlin
@Dao
interface ConversionJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: ConversionJobEntity)

    @Query("SELECT * FROM conversion_jobs WHERE id = :id LIMIT 1")
    fun observeJob(id: String): Flow<ConversionJobEntity?>

    @Query("SELECT * FROM conversion_jobs ORDER BY updatedAt DESC")
    fun observeRecentJobs(): Flow<List<ConversionJobEntity>>
}
```

## 6. Repository Contracts

### Document Repository

```kotlin
interface DocumentRepository {
    fun observeDocuments(): Flow<List<DoclyDocument>>
    fun searchDocuments(query: String): Flow<List<DoclyDocument>>
    suspend fun getDocument(id: String): AppResult<DoclyDocument?>
    suspend fun importDocument(uri: String): AppResult<DoclyDocument>
    suspend fun createDocument(name: String, type: DocumentType): AppResult<DoclyDocument>
    suspend fun renameDocument(id: String, newName: String): AppResult<Unit>
    suspend fun deleteDocument(id: String): AppResult<Unit>
    suspend fun duplicateDocument(id: String): AppResult<DoclyDocument>
    suspend fun updateLastOpened(id: String): AppResult<Unit>
}
```

### Scanner Repository

```kotlin
interface ScannerRepository {
    suspend fun createSession(title: String? = null): AppResult<ScanSession>
    fun observePages(sessionId: String): Flow<List<ScanPage>>
    suspend fun addPage(sessionId: String, page: ScanPage): AppResult<Unit>
    suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit>
    suspend fun rotatePage(pageId: String, degrees: Int): AppResult<Unit>
    suspend fun deletePage(pageId: String): AppResult<Unit>
    suspend fun exportSessionToPdf(sessionId: String, outputName: String): AppResult<DoclyDocument>
    suspend fun exportSessionToImages(sessionId: String): AppResult<List<DoclyDocument>>
}
```

### Converter Repository

```kotlin
interface ConverterRepository {
    fun getSupportedOutputs(inputType: DocumentType): List<DocumentType>
    suspend fun createJob(request: ConversionRequest): AppResult<ConversionJob>
    fun observeJob(jobId: String): Flow<ConversionJob?>
    suspend fun convert(request: ConversionRequest): AppResult<ConversionResult>
}
```

### Settings Repository

```kotlin
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateDefaultPdfQuality(quality: PdfQuality)
    suspend fun updateAutoSaveEnabled(enabled: Boolean)
}
```

## 7. Scanner LLD

### Scanner Models

```kotlin
data class ScanSession(
    val id: String,
    val title: String?,
    val outputDocumentId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ScanSessionStatus
)
```

```kotlin
data class ScanPage(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val imagePath: String,
    val originalImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val filterType: ScanFilterType,
    val createdAt: Long,
    val updatedAt: Long
)
```

```kotlin
enum class ScanFilterType {
    ORIGINAL,
    CLEAN,
    BLACK_AND_WHITE,
    GRAYSCALE,
    HIGH_CONTRAST
}
```

### Scanner Service

```kotlin
interface DocumentScannerService {
    suspend fun scanDocuments(options: ScanOptions): AppResult<ScanResult>
}
```

```kotlin
data class ScanOptions(
    val allowGalleryImport: Boolean = true,
    val maxPages: Int? = null,
    val returnPdf: Boolean = false
)
```

```kotlin
data class ScanResult(
    val pageImageUris: List<String>,
    val pdfUri: String? = null
)
```

Implementation:

- `MlKitDocumentScannerService` is the default implementation.
- A future `CustomCameraDocumentScannerService` may use CameraX/OpenCV behind the same interface.

## 8. Reader LLD

### Open Resolver

```kotlin
class DocumentOpenResolver(
    private val capabilities: DocumentCapabilityResolver
) {
    fun resolve(document: DoclyDocument): ReaderRoute {
        return when (document.type) {
            DocumentType.PDF -> ReaderRoute.Pdf(document.id)
            DocumentType.TXT -> ReaderRoute.Txt(document.id)
            DocumentType.MARKDOWN -> ReaderRoute.Markdown(document.id)
            DocumentType.HTML -> ReaderRoute.Html(document.id)
            DocumentType.DOCX -> ReaderRoute.Word(document.id)
            DocumentType.XLSX -> ReaderRoute.Excel(document.id)
            else -> ReaderRoute.Unsupported(document.id)
        }
    }
}
```

### Reader Services

```kotlin
interface PdfReaderEngine {
    suspend fun open(fileRef: FileRef): AppResult<PdfDocumentInfo>
    suspend fun renderPage(documentId: String, pageIndex: Int, widthPx: Int): AppResult<RenderedPage>
}
```

```kotlin
interface TextReaderEngine {
    suspend fun read(fileRef: FileRef): AppResult<String>
}
```

```kotlin
interface MarkdownService {
    fun markdownToHtml(markdown: String): String
    fun markdownToPlainText(markdown: String): String
}
```

```kotlin
interface DocxParser {
    suspend fun parse(fileRef: FileRef): AppResult<ExtractedDocument>
}
```

```kotlin
interface XlsxParser {
    suspend fun parse(fileRef: FileRef): AppResult<WorkbookDocument>
}
```

## 9. Creator LLD

### Default Content

```kotlin
class DefaultDocumentContentFactory {
    fun create(type: DocumentType, title: String): String {
        return when (type) {
            DocumentType.TXT -> ""
            DocumentType.MARKDOWN -> "# $title\n\n"
            DocumentType.HTML -> """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>$title</title>
                </head>
                <body>
                </body>
                </html>
            """.trimIndent()
            else -> ""
        }
    }
}
```

### PDF Export Request

```kotlin
data class PdfExportRequest(
    val title: String,
    val sourceType: PdfSourceType,
    val imagePaths: List<String> = emptyList(),
    val htmlContent: String? = null,
    val textContent: String? = null,
    val outputPath: String,
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.BALANCED
)
```

```kotlin
enum class PdfSourceType {
    IMAGES,
    TXT,
    MARKDOWN,
    HTML,
    RICH_DOCUMENT
}
```

## 10. Editor LLD

### Shared Editor State

```kotlin
data class EditorState(
    val documentId: String,
    val title: String,
    val content: String,
    val isDirty: Boolean,
    val isSaving: Boolean,
    val lastSavedAt: Long?,
    val errorMessage: String?
)
```

### Autosave

```kotlin
class AutoSaveController(
    private val saveDelayMs: Long = 1500L,
    private val save: suspend () -> Unit
) {
    private var saveJob: Job? = null

    fun schedule(scope: CoroutineScope) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDelayMs)
            save()
        }
    }

    fun cancel() {
        saveJob?.cancel()
    }
}
```

Editor rules:

- TXT editor writes plain text.
- Markdown editor saves source and derives preview.
- HTML editor saves source and derives preview.
- PDF page management edits scan-source pages and regenerates a new PDF.
- Arbitrary imported PDF text is not edited in MVP.

## 11. Converter LLD

### Conversion Models

```kotlin
data class ConversionPair(
    val input: DocumentType,
    val output: DocumentType
)
```

```kotlin
data class ConversionRequest(
    val inputDocumentId: String?,
    val inputUri: String?,
    val inputType: DocumentType,
    val outputType: DocumentType,
    val outputFileName: String,
    val options: ConversionOptions = ConversionOptions()
)
```

```kotlin
data class ConversionOptions(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val includeImages: Boolean = true,
    val includeFormatting: Boolean = true,
    val quality: PdfQuality = PdfQuality.BALANCED
)
```

```kotlin
data class ConversionResult(
    val outputPath: String,
    val outputDocument: DoclyDocument
)
```

```kotlin
data class ConversionJob(
    val id: String,
    val inputDocumentId: String?,
    val inputType: DocumentType,
    val outputType: DocumentType,
    val status: ConversionStatus,
    val progress: Int,
    val outputDocumentId: String?,
    val errorMessage: String?
)
```

```kotlin
enum class ConversionStatus {
    IDLE,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

### Converter Engine

```kotlin
interface ConverterEngine {
    val supportedPairs: List<ConversionPair>
    suspend fun convert(request: ConversionRequest): AppResult<ConversionResult>
}
```

```kotlin
class ConverterRegistry(
    private val engines: List<ConverterEngine>
) {
    fun getSupportedOutputs(inputType: DocumentType): List<DocumentType> {
        return engines
            .flatMap { it.supportedPairs }
            .filter { it.input == inputType }
            .map { it.output }
            .distinct()
    }

    fun findEngine(input: DocumentType, output: DocumentType): ConverterEngine? {
        return engines.firstOrNull { engine ->
            engine.supportedPairs.any { pair ->
                pair.input == input && pair.output == output
            }
        }
    }
}
```

MVP pairs:

```kotlin
val mvpConversionPairs = listOf(
    ConversionPair(DocumentType.TXT, DocumentType.PDF),
    ConversionPair(DocumentType.TXT, DocumentType.HTML),
    ConversionPair(DocumentType.TXT, DocumentType.MARKDOWN),
    ConversionPair(DocumentType.MARKDOWN, DocumentType.HTML),
    ConversionPair(DocumentType.MARKDOWN, DocumentType.PDF),
    ConversionPair(DocumentType.MARKDOWN, DocumentType.TXT),
    ConversionPair(DocumentType.HTML, DocumentType.PDF),
    ConversionPair(DocumentType.HTML, DocumentType.TXT),
    ConversionPair(DocumentType.IMAGE, DocumentType.PDF)
)
```

## 12. Navigation

Main destinations:

```kotlin
sealed interface MainRoute {
    data object Home : MainRoute
    data object Scan : MainRoute
    data object Documents : MainRoute
    data object Create : MainRoute
    data object Tools : MainRoute
    data object Settings : MainRoute
}
```

App routes:

```text
splash
onboarding
home
documents
document_details/{documentId}
scan
scan_review/{sessionId}
create
reader/pdf/{documentId}
reader/txt/{documentId}
reader/markdown/{documentId}
reader/html/{documentId}
reader/word/{documentId}
reader/excel/{documentId}
editor/txt/{documentId}
editor/markdown/{documentId}
editor/html/{documentId}
editor/rich/{documentId}
editor/table/{documentId}
converter
tools/pdf
ocr
settings
```

Navigation rules:

- Pass IDs through routes, not file paths.
- Resolve files through repositories.
- Use type-safe route objects where practical.

## 13. Screen ViewModel Contracts

Documents:

- Observe documents.
- Search, sort, filter, rename, delete, duplicate, favorite, open, share, import.

Scanner:

- Launch scanner.
- Create scan session.
- Copy result pages.
- Navigate to scan review.

Scan Review:

- Observe scan pages.
- Reorder, rotate, delete, add page.
- Export as PDF or images.

Readers:

- Load document by ID.
- Open the correct reader engine.
- Track page/sheet/mode state.
- Show simplified-mode warnings.

Creator:

- Select type.
- Create file and document row.
- Navigate to editor or scan flow.

Editors:

- Load content.
- Save manually.
- Autosave.
- Preview where supported.
- Export.

Converter:

- Select input.
- Detect type.
- Show supported outputs.
- Run conversion.
- Open/share result.

## 14. File Storage

Internal layout:

```text
files/docly/
|-- documents/pdf/
|-- documents/txt/
|-- documents/markdown/
|-- documents/html/
|-- documents/docx/
|-- documents/xlsx/
|-- documents/csv/
|-- documents/images/
|-- scans/sessions/
|-- scans/pages/
|-- thumbnails/
|-- exports/
|-- temp/
`-- ocr/
```

Storage manager:

```kotlin
interface DoclyStorageManager {
    suspend fun createDocumentFile(name: String, type: DocumentType): AppResult<String>
    suspend fun copyUriToInternalStorage(uri: String, targetName: String, type: DocumentType): AppResult<String>
    suspend fun deleteFile(fileRef: FileRef): AppResult<Unit>
    suspend fun createThumbnail(document: DoclyDocument): AppResult<String>
    suspend fun getFileSize(fileRef: FileRef): AppResult<Long>
}
```

## 15. File Type Detection

Detection priority:

1. MIME type.
2. File extension.
3. File signature where practical.
4. `UNKNOWN`.

```kotlin
class FileTypeResolver {
    fun resolve(fileName: String, mimeType: String?): DocumentType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            mimeType == "application/pdf" || extension == "pdf" -> DocumentType.PDF
            mimeType == "text/plain" || extension == "txt" -> DocumentType.TXT
            extension == "md" || extension == "markdown" -> DocumentType.MARKDOWN
            mimeType == "text/html" || extension in setOf("html", "htm") -> DocumentType.HTML
            extension == "docx" -> DocumentType.DOCX
            extension == "xlsx" -> DocumentType.XLSX
            extension == "csv" -> DocumentType.CSV
            extension in setOf("jpg", "jpeg", "png", "webp") -> DocumentType.IMAGE
            else -> DocumentType.UNKNOWN
        }
    }
}
```

## 16. Error Handling

```kotlin
sealed interface DoclyError {
    data object FileNotFound : DoclyError
    data object UnsupportedFormat : DoclyError
    data object PermissionDenied : DoclyError
    data object ConversionFailed : DoclyError
    data object ScanCancelled : DoclyError
    data object StorageUnavailable : DoclyError
    data object OcrFailed : DoclyError
    data class Unknown(val message: String) : DoclyError
}
```

User-facing messages:

| Failure | Message |
| --- | --- |
| Unsupported format | `Docly cannot open this file type yet.` |
| Simplified DOCX/XLSX | `This document is shown in a simplified view.` |
| Conversion unsupported | `This conversion is not supported yet.` |
| Conversion failed | `The document could not be converted.` |
| Scanner unavailable | `Document scanning is unavailable on this device.` |
| Storage unavailable | `Not enough storage space to save this document.` |
| Permission denied | `Permission is required to continue.` |

## 17. Testing Plan

Unit tests:

- File type resolver.
- Capability resolver.
- Document mappers.
- Scan page ordering.
- Conversion registry.
- Autosave controller.
- Markdown conversion.
- TXT/HTML conversion.

Integration tests:

- Room database.
- SAF import copy.
- File deletion.
- PDF generation.
- Converter output registration.

Instrumentation tests:

- Documents list and actions.
- Scanner result handling.
- Reader screens.
- Editor save flows.
- Converter screen.
- Permission denial states.

Manual QA:

- Physical scanning.
- Large PDFs.
- Low-end device memory.
- DOCX/XLSX simplified rendering.
- Offline behavior.
- Share/export through Android share sheet and SAF.
