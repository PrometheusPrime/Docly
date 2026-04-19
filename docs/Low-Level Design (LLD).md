# Low-Level Design (LLD)

## Project: **Docly**

This is the concrete implementation blueprint for the **MVP**. 

---

# 1. Design Goals

The LLD must make the app:

* modular
* easy to extend
* easy to test
* safe on low-memory devices
* offline-first
* suitable for internal/admin past-paper ingestion

---

# 2. Module / Package Layout

Use a **single app module** first, but structure packages as if they were separate modules. That gives you speed now and clean extraction later.

```text
com.docly.app
├── app
│   ├── di
│   ├── navigation
│   └── MainActivity.kt
│
├── core
│   ├── common
│   ├── dispatchers
│   ├── file
│   ├── result
│   ├── image
│   ├── pdf
│   ├── camera
│   ├── logging
│   └── time
│
├── data
│   ├── local
│   │   ├── db
│   │   ├── dao
│   │   ├── entity
│   │   └── mapper
│   ├── repository
│   └── storage
│
├── domain
│   ├── model
│   ├── repository
│   └── usecase
│
├── feature
│   ├── scanner
│   ├── review
│   ├── editor
│   ├── metadata
│   ├── export
│   └── library
│
└── ui
    ├── components
    ├── theme
    └── util
```

---

# 3. Core Architectural Pattern

Use:

* **MVVM**
* **StateFlow**
* **Use Cases**
* **Repository abstraction**
* **Room + file system**
* **Compose navigation**

Each feature gets:

* `Screen`
* `ViewModel`
* `UiState`
* `UiEvent`
* optional `UiEffect`

---

# 4. Domain Models

These are the **source of truth** for business logic.

---

## 4.1 `ScanSession`

```kotlin
data class ScanSession(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ScanSessionStatus,
    val scanMode: ScanMode,
    val pages: List<ScannedPage> = emptyList(),
    val metadata: DocumentMetadata? = null
)
```

---

## 4.2 `ScannedPage`

```kotlin
data class ScannedPage(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val scanMode: ScanMode,
    val width: Int,
    val height: Int,
    val corners: PageCorners? = null,
    val createdAt: Long
)
```

---

## 4.3 `DocumentMetadata`

```kotlin
data class DocumentMetadata(
    val grade: String,
    val subject: String,
    val year: Int,
    val paperType: String,
    val paperNumber: String? = null,
    val source: String? = null,
    val notes: String? = null
)
```

---

## 4.4 `SavedDocument`

```kotlin
data class SavedDocument(
    val id: String,
    val sessionId: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val metadata: DocumentMetadata,
    val pageCount: Int,
    val createdAt: Long
)
```

---

## 4.5 Supporting models

### `PageCorners`

```kotlin
data class PageCorners(
    val topLeft: PointFSerializable,
    val topRight: PointFSerializable,
    val bottomRight: PointFSerializable,
    val bottomLeft: PointFSerializable
)
```

### `PointFSerializable`

```kotlin
data class PointFSerializable(
    val x: Float,
    val y: Float
)
```

### `ScanMode`

```kotlin
enum class ScanMode {
    DOCUMENT,
    MIXED,
    COLOR
}
```

### `ScanSessionStatus`

```kotlin
enum class ScanSessionStatus {
    IN_PROGRESS,
    READY_FOR_EXPORT,
    EXPORTED,
    ABANDONED
}
```

---

# 5. Room Database Design

Do **not** store bitmaps or PDFs inside Room.
Store only metadata and file paths.

---

## 5.1 `ScanSessionEntity`

```kotlin
@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val scanMode: String,
    val grade: String?,
    val subject: String?,
    val year: Int?,
    val paperType: String?,
    val paperNumber: String?,
    val source: String?,
    val notes: String?
)
```

---

## 5.2 `ScannedPageEntity`

```kotlin
@Entity(
    tableName = "scanned_pages",
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
data class ScannedPageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val scanMode: String,
    val width: Int,
    val height: Int,
    val topLeftX: Float?,
    val topLeftY: Float?,
    val topRightX: Float?,
    val topRightY: Float?,
    val bottomRightX: Float?,
    val bottomRightY: Float?,
    val bottomLeftX: Float?,
    val bottomLeftY: Float?,
    val createdAt: Long
)
```

---

## 5.3 `SavedDocumentEntity`

```kotlin
@Entity(tableName = "saved_documents")
data class SavedDocumentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val grade: String,
    val subject: String,
    val year: Int,
    val paperType: String,
    val paperNumber: String?,
    val source: String?,
    val notes: String?,
    val pageCount: Int,
    val createdAt: Long
)
```

---

# 6. DAOs

---

## 6.1 `ScanSessionDao`

```kotlin
@Dao
interface ScanSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestByStatus(status: String): ScanSessionEntity?

    @Update
    suspend fun update(session: ScanSessionEntity)

    @Delete
    suspend fun delete(session: ScanSessionEntity)
}
```

---

## 6.2 `ScannedPageDao`

```kotlin
@Dao
interface ScannedPageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: ScannedPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<ScannedPageEntity>)

    @Query("SELECT * FROM scanned_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getBySessionId(sessionId: String): List<ScannedPageEntity>

    @Query("SELECT * FROM scanned_pages WHERE id = :pageId")
    suspend fun getById(pageId: String): ScannedPageEntity?

    @Update
    suspend fun update(page: ScannedPageEntity)

    @Delete
    suspend fun delete(page: ScannedPageEntity)

    @Query("DELETE FROM scanned_pages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)
}
```

---

## 6.3 `SavedDocumentDao`

```kotlin
@Dao
interface SavedDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: SavedDocumentEntity)

    @Query("SELECT * FROM saved_documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedDocumentEntity>>

    @Query("SELECT * FROM saved_documents WHERE id = :documentId")
    suspend fun getById(documentId: String): SavedDocumentEntity?

    @Delete
    suspend fun delete(document: SavedDocumentEntity)
}
```

---

# 7. Database Class

```kotlin
@Database(
    entities = [
        ScanSessionEntity::class,
        ScannedPageEntity::class,
        SavedDocumentEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scannedPageDao(): ScannedPageDao
    abstract fun savedDocumentDao(): SavedDocumentDao
}
```

---

# 8. Mappers

Each entity needs a mapper to/from domain.

Files:

```text
data/local/mapper/
├── ScanSessionMapper.kt
├── ScannedPageMapper.kt
└── SavedDocumentMapper.kt
```

Keep mapping logic out of ViewModels.

---

# 9. Repository Interfaces

These live in `domain/repository`.

---

## 9.1 `ScanRepository`

```kotlin
interface ScanRepository {
    suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession>
    suspend fun getSession(sessionId: String): AppResult<ScanSession?>
    suspend fun getLatestInProgressSession(): AppResult<ScanSession?>
    suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit>
    suspend fun addPage(page: ScannedPage): AppResult<Unit>
    suspend fun updatePage(page: ScannedPage): AppResult<Unit>
    suspend fun deletePage(pageId: String): AppResult<Unit>
    suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit>
    suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit>
}
```

---

## 9.2 `DocumentRepository`

```kotlin
interface DocumentRepository {
    fun observeSavedDocuments(): Flow<List<SavedDocument>>
    suspend fun saveDocument(document: SavedDocument): AppResult<Unit>
    suspend fun getDocument(documentId: String): AppResult<SavedDocument?>
    suspend fun deleteDocument(documentId: String): AppResult<Unit>
}
```

---

## 9.3 `ImageProcessingRepository`

```kotlin
interface ImageProcessingRepository {
    suspend fun detectDocument(inputPath: String): AppResult<PageCorners?>
    suspend fun processPage(
        inputPath: String,
        outputPath: String,
        scanMode: ScanMode,
        corners: PageCorners?
    ): AppResult<ProcessedPageResult>
    suspend fun generateThumbnail(
        inputPath: String,
        outputPath: String
    ): AppResult<String>
}
```

---

## 9.4 `PdfRepository`

```kotlin
interface PdfRepository {
    suspend fun createPdf(
        pageImagePaths: List<String>,
        outputPdfPath: String
    ): AppResult<String>
}
```

---

## 9.5 `FileRepository`

```kotlin
interface FileRepository {
    fun createSessionImagePath(sessionId: String, suffix: String): String
    fun createProcessedImagePath(sessionId: String, suffix: String): String
    fun createThumbnailPath(sessionId: String, suffix: String): String
    fun createPdfPath(fileName: String): String
    suspend fun deleteFile(path: String): AppResult<Unit>
    suspend fun deleteFiles(paths: List<String>): AppResult<Unit>
}
```

---

# 10. Repository Implementations

These live in `data/repository`.

Files:

```text
data/repository/
├── ScanRepositoryImpl.kt
├── DocumentRepositoryImpl.kt
├── ImageProcessingRepositoryImpl.kt
├── PdfRepositoryImpl.kt
└── FileRepositoryImpl.kt
```

---

# 11. Use Cases

Create small, focused use cases. Avoid giant “manager” classes.

---

## 11.1 Scan session use cases

```text
domain/usecase/session/
├── CreateScanSessionUseCase.kt
├── GetScanSessionUseCase.kt
├── GetLatestInProgressSessionUseCase.kt
├── UpdateScanMetadataUseCase.kt
└── UpdateScanSessionStatusUseCase.kt
```

---

## 11.2 Page use cases

```text
domain/usecase/page/
├── CapturePageUseCase.kt
├── ProcessCapturedPageUseCase.kt
├── AddProcessedPageUseCase.kt
├── UpdatePageUseCase.kt
├── DeletePageUseCase.kt
├── ReorderPagesUseCase.kt
└── RotatePageUseCase.kt
```

---

## 11.3 Export use cases

```text
domain/usecase/export/
├── GenerateDocumentNameUseCase.kt
├── ValidateMetadataUseCase.kt
├── GeneratePdfUseCase.kt
└── SaveDocumentUseCase.kt
```

---

## 11.4 Library use cases

```text
domain/usecase/library/
├── ObserveSavedDocumentsUseCase.kt
├── GetSavedDocumentUseCase.kt
└── DeleteSavedDocumentUseCase.kt
```

---

# 12. Key Use Case Signatures

---

## `CreateScanSessionUseCase`

```kotlin
class CreateScanSessionUseCase(
    private val scanRepository: ScanRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(scanMode: ScanMode): AppResult<ScanSession>
}
```

---

## `ProcessCapturedPageUseCase`

```kotlin
class ProcessCapturedPageUseCase(
    private val imageProcessingRepository: ImageProcessingRepository,
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        rawImagePath: String,
        scanMode: ScanMode,
        manualCorners: PageCorners? = null
    ): AppResult<ProcessedPageResult>
}
```

---

## `GenerateDocumentNameUseCase`

```kotlin
class GenerateDocumentNameUseCase {
    operator fun invoke(metadata: DocumentMetadata): String
}
```

---

## `ValidateMetadataUseCase`

```kotlin
class ValidateMetadataUseCase {
    operator fun invoke(metadata: DocumentMetadata): ValidationResult
}
```

---

## `GeneratePdfUseCase`

```kotlin
class GeneratePdfUseCase(
    private val pdfRepository: PdfRepository,
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        fileName: String,
        pages: List<ScannedPage>
    ): AppResult<String>
}
```

---

# 13. Supporting Service Models

---

## `ProcessedPageResult`

```kotlin
data class ProcessedPageResult(
    val processedImagePath: String,
    val thumbnailPath: String,
    val detectedCorners: PageCorners?,
    val width: Int,
    val height: Int
)
```

---

## `ValidationResult`

```kotlin
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
```

---

# 14. Feature-by-Feature LLD

---

## 14.1 Scanner Feature

### Purpose

Live camera preview and raw capture.

### Files

```text
feature/scanner/
├── ScannerScreen.kt
├── ScannerViewModel.kt
├── ScannerUiState.kt
├── ScannerUiEvent.kt
├── ScannerUiEffect.kt
├── CameraPreviewView.kt
└── DocumentOverlay.kt
```

---

### `ScannerUiState`

```kotlin
data class ScannerUiState(
    val isCameraPermissionGranted: Boolean = false,
    val isCameraReady: Boolean = false,
    val isCapturing: Boolean = false,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val detectedCorners: PageCorners? = null,
    val qualityHint: String? = null,
    val sessionId: String? = null,
    val errorMessage: String? = null
)
```

---

### `ScannerUiEvent`

```kotlin
sealed interface ScannerUiEvent {
    data object OnStart : ScannerUiEvent
    data class OnPermissionResult(val granted: Boolean) : ScannerUiEvent
    data object OnCaptureClicked : ScannerUiEvent
    data class OnScanModeChanged(val scanMode: ScanMode) : ScannerUiEvent
    data class OnCornersDetected(val corners: PageCorners?) : ScannerUiEvent
}
```

---

### `ScannerUiEffect`

```kotlin
sealed interface ScannerUiEffect {
    data class NavigateToReview(
        val sessionId: String,
        val rawImagePath: String,
        val scanMode: ScanMode
    ) : ScannerUiEffect

    data class ShowToast(val message: String) : ScannerUiEffect
}
```

---

### `ScannerViewModel` responsibilities

* create or restore scan session
* hold selected scan mode
* react to capture button
* save raw image path
* navigate to review screen

It should **not** perform heavy OpenCV work directly.

---

## 14.2 Review Feature

### Purpose

Preview processed output, allow manual correction, accept/rescan.

### Files

```text
feature/review/
├── ReviewScreen.kt
├── ReviewViewModel.kt
├── ReviewUiState.kt
├── ReviewUiEvent.kt
├── ReviewUiEffect.kt
├── CropAdjustmentOverlay.kt
└── ReviewNavArgs.kt
```

---

### `ReviewUiState`

```kotlin
data class ReviewUiState(
    val sessionId: String = "",
    val rawImagePath: String = "",
    val processedImagePath: String? = null,
    val selectedScanMode: ScanMode = ScanMode.DOCUMENT,
    val detectedCorners: PageCorners? = null,
    val editableCorners: PageCorners? = null,
    val isProcessing: Boolean = true,
    val isSaving: Boolean = false,
    val showOriginal: Boolean = false,
    val rotationDegrees: Int = 0,
    val errorMessage: String? = null
)
```

---

### `ReviewUiEvent`

```kotlin
sealed interface ReviewUiEvent {
    data object OnLoad : ReviewUiEvent
    data class OnScanModeChanged(val scanMode: ScanMode) : ReviewUiEvent
    data class OnCornersChanged(val corners: PageCorners) : ReviewUiEvent
    data object OnReprocessClicked : ReviewUiEvent
    data object OnRotateClicked : ReviewUiEvent
    data object OnAcceptClicked : ReviewUiEvent
    data object OnRescanClicked : ReviewUiEvent
    data object OnToggleOriginalClicked : ReviewUiEvent
}
```

---

### `ReviewUiEffect`

```kotlin
sealed interface ReviewUiEffect {
    data class NavigateBackToScanner(val sessionId: String) : ReviewUiEffect
    data class NavigateToEditor(val sessionId: String) : ReviewUiEffect
    data class ShowToast(val message: String) : ReviewUiEffect
}
```

---

### `ReviewViewModel` responsibilities

* process raw image on load
* allow reprocessing with different mode
* allow manual corners override
* rotate final page
* save accepted page into session

---

## 14.3 Editor Feature

### Purpose

Manage accepted session pages.

### Files

```text
feature/editor/
├── EditorScreen.kt
├── EditorViewModel.kt
├── EditorUiState.kt
├── EditorUiEvent.kt
├── EditorUiEffect.kt
└── PageThumbnailItem.kt
```

---

### `EditorUiState`

```kotlin
data class EditorUiState(
    val sessionId: String = "",
    val pages: List<ScannedPage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

---

### `EditorUiEvent`

```kotlin
sealed interface EditorUiEvent {
    data object OnLoad : EditorUiEvent
    data object OnAddPageClicked : EditorUiEvent
    data class OnDeletePageClicked(val pageId: String) : EditorUiEvent
    data class OnRotatePageClicked(val pageId: String) : EditorUiEvent
    data class OnMovePageUp(val pageId: String) : EditorUiEvent
    data class OnMovePageDown(val pageId: String) : EditorUiEvent
    data object OnContinueClicked : EditorUiEvent
}
```

---

### `EditorUiEffect`

```kotlin
sealed interface EditorUiEffect {
    data class NavigateToScanner(val sessionId: String) : EditorUiEffect
    data class NavigateToMetadata(val sessionId: String) : EditorUiEffect
    data class ShowToast(val message: String) : EditorUiEffect
}
```

---

### `EditorViewModel` responsibilities

* load pages by session
* delete page
* rotate page
* reorder pages
* move forward when at least one page exists

---

## 14.4 Metadata Feature

### Purpose

Attach structured past-paper information.

### Files

```text
feature/metadata/
├── MetadataScreen.kt
├── MetadataViewModel.kt
├── MetadataUiState.kt
├── MetadataUiEvent.kt
├── MetadataUiEffect.kt
└── MetadataForm.kt
```

---

### `MetadataUiState`

```kotlin
data class MetadataUiState(
    val sessionId: String = "",
    val grade: String = "",
    val subject: String = "",
    val year: String = "",
    val paperType: String = "",
    val paperNumber: String = "",
    val source: String = "",
    val notes: String = "",
    val generatedFileName: String = "",
    val validationErrors: List<String> = emptyList(),
    val isSaving: Boolean = false
)
```

---

### `MetadataUiEvent`

```kotlin
sealed interface MetadataUiEvent {
    data object OnLoad : MetadataUiEvent
    data class OnGradeChanged(val value: String) : MetadataUiEvent
    data class OnSubjectChanged(val value: String) : MetadataUiEvent
    data class OnYearChanged(val value: String) : MetadataUiEvent
    data class OnPaperTypeChanged(val value: String) : MetadataUiEvent
    data class OnPaperNumberChanged(val value: String) : MetadataUiEvent
    data class OnSourceChanged(val value: String) : MetadataUiEvent
    data class OnNotesChanged(val value: String) : MetadataUiEvent
    data object OnContinueClicked : MetadataUiEvent
}
```

---

### `MetadataUiEffect`

```kotlin
sealed interface MetadataUiEffect {
    data class NavigateToExport(val sessionId: String) : MetadataUiEffect
    data class ShowToast(val message: String) : MetadataUiEffect
}
```

---

### `MetadataViewModel` responsibilities

* load existing metadata if any
* keep generated filename updated live
* validate required fields
* persist metadata into session

---

## 14.5 Export Feature

### Purpose

Generate final PDF and save document.

### Files

```text
feature/export/
├── ExportScreen.kt
├── ExportViewModel.kt
├── ExportUiState.kt
├── ExportUiEvent.kt
├── ExportUiEffect.kt
└── ExportSummaryCard.kt
```

---

### `ExportUiState`

```kotlin
data class ExportUiState(
    val sessionId: String = "",
    val fileName: String = "",
    val pageCount: Int = 0,
    val isExporting: Boolean = false,
    val exportSuccessPath: String? = null,
    val errorMessage: String? = null
)
```

---

### `ExportUiEvent`

```kotlin
sealed interface ExportUiEvent {
    data object OnLoad : ExportUiEvent
    data object OnExportClicked : ExportUiEvent
    data object OnOpenLibraryClicked : ExportUiEvent
}
```

---

### `ExportUiEffect`

```kotlin
sealed interface ExportUiEffect {
    data class ShowToast(val message: String) : ExportUiEffect
    data object NavigateToLibrary : ExportUiEffect
}
```

---

### `ExportViewModel` responsibilities

* load session, pages, metadata
* validate export readiness
* generate PDF
* create `SavedDocument`
* mark session exported

---

## 14.6 Library Feature

### Purpose

Show all exported local documents.

### Files

```text
feature/library/
├── LibraryScreen.kt
├── LibraryViewModel.kt
├── LibraryUiState.kt
├── LibraryUiEvent.kt
├── LibraryUiEffect.kt
└── LibraryDocumentItem.kt
```

---

### `LibraryUiState`

```kotlin
data class LibraryUiState(
    val documents: List<SavedDocument> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
```

---

### `LibraryUiEvent`

```kotlin
sealed interface LibraryUiEvent {
    data object OnLoad : LibraryUiEvent
    data class OnSearchQueryChanged(val query: String) : LibraryUiEvent
    data class OnDeleteDocumentClicked(val documentId: String) : LibraryUiEvent
}
```

---

### `LibraryUiEffect`

```kotlin
sealed interface LibraryUiEffect {
    data class ShowToast(val message: String) : LibraryUiEffect
}
```

---

# 15. Navigation Design

Use `Navigation Compose`.

---

## 15.1 Routes

```kotlin
sealed class AppRoute(val route: String) {
    data object Scanner : AppRoute("scanner")
    data object Review : AppRoute("review/{sessionId}/{rawImagePath}/{scanMode}")
    data object Editor : AppRoute("editor/{sessionId}")
    data object Metadata : AppRoute("metadata/{sessionId}")
    data object Export : AppRoute("export/{sessionId}")
    data object Library : AppRoute("library")
}
```

Because raw file paths can be unsafe in route strings, a cleaner option is:

* store temporary review state in `SavedStateHandle`
* navigate with only `sessionId`

That is the better production choice.

---

## Recommended final route set

```kotlin
sealed class AppRoute(val route: String) {
    data object Scanner : AppRoute("scanner")
    data object Review : AppRoute("review/{sessionId}")
    data object Editor : AppRoute("editor/{sessionId}")
    data object Metadata : AppRoute("metadata/{sessionId}")
    data object Export : AppRoute("export/{sessionId}")
    data object Library : AppRoute("library")
}
```

---

# 16. File Storage Design

Use app-specific storage.

---

## 16.1 Directories

```text
files/
├── scans/
│   ├── raw/
│   ├── processed/
│   └── thumbnails/
└── documents/
    └── pdf/
```

---

## 16.2 Naming convention

### Raw image

```text
raw_{sessionId}_{timestamp}.jpg
```

### Processed image

```text
processed_{sessionId}_{pageIndex}.jpg
```

### Thumbnail

```text
thumb_{sessionId}_{pageIndex}.jpg
```

---

# 17. Core Utilities

Create these utility interfaces:

---

## `IdProvider`

```kotlin
interface IdProvider {
    fun generateId(): String
}
```

Implementation: UUID.

---

## `TimeProvider`

```kotlin
interface TimeProvider {
    fun now(): Long
}
```

---

## `DispatcherProvider`

```kotlin
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
```

---

# 18. Result Wrapper

Use a single wrapper across the app.

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : AppResult<Nothing>()
}
```

Add helpers:

```kotlin
inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T>
inline fun <T> AppResult<T>.onError(block: (String) -> Unit): AppResult<T>
```

---

# 19. Image Processing LLD

This is the most technical part.

---

## 19.1 Interfaces in `core/image`

```text
core/image/
├── DocumentDetector.kt
├── PerspectiveTransformer.kt
├── ImageEnhancer.kt
├── ThumbnailGenerator.kt
├── BitmapLoader.kt
└── OpenCvInitializer.kt
```

---

## 19.2 `DocumentDetector`

```kotlin
interface DocumentDetector {
    suspend fun detect(imagePath: String): AppResult<PageCorners?>
}
```

Implementation steps:

* decode bitmap at workable resolution
* convert to `Mat`
* grayscale
* Gaussian blur
* Canny edge detection
* find contours
* choose best quadrilateral
* return ordered corners

---

## 19.3 `PerspectiveTransformer`

```kotlin
interface PerspectiveTransformer {
    suspend fun warp(
        imagePath: String,
        corners: PageCorners,
        outputPath: String
    ): AppResult<WarpResult>
}
```

### `WarpResult`

```kotlin
data class WarpResult(
    val outputPath: String,
    val width: Int,
    val height: Int
)
```

---

## 19.4 `ImageEnhancer`

```kotlin
interface ImageEnhancer {
    suspend fun enhance(
        inputPath: String,
        outputPath: String,
        scanMode: ScanMode
    ): AppResult<String>
}
```

---

## 19.5 Processing flow

For a captured page:

1. detect corners
2. warp page if corners found
3. if no corners, use original image
4. enhance using selected mode
5. generate thumbnail
6. return processed result

---

# 20. PDF Service LLD

In `core/pdf/`

```text
core/pdf/
├── PdfGenerator.kt
└── AndroidPdfGenerator.kt
```

---

## `PdfGenerator`

```kotlin
interface PdfGenerator {
    suspend fun generate(
        pageImagePaths: List<String>,
        outputPdfPath: String
    ): AppResult<String>
}
```

### Implementation notes

* use `PdfDocument`
* one page per image
* fit image inside PDF page bounds
* preserve aspect ratio
* use background dispatcher
* close all resources safely

---

# 21. Scanner Camera LLD

In `core/camera/`

```text
core/camera/
├── CameraController.kt
├── CameraCaptureManager.kt
├── CameraFrameAnalyzer.kt
└── QualityAnalyzer.kt
```

For MVP, keep live document detection **optional**.
Do not overcomplicate phase 1.

---

## `CameraCaptureManager`

```kotlin
interface CameraCaptureManager {
    suspend fun captureToFile(outputPath: String): AppResult<String>
}
```

---

# 22. Dependency Injection

Use Hilt.

---

## `DatabaseModule`

Provides:

* `AppDatabase`
* `ScanSessionDao`
* `ScannedPageDao`
* `SavedDocumentDao`

---

## `RepositoryModule`

Provides:

* `ScanRepository`
* `DocumentRepository`
* `ImageProcessingRepository`
* `PdfRepository`
* `FileRepository`

---

## `CoreModule`

Provides:

* `IdProvider`
* `TimeProvider`
* `DispatcherProvider`
* `PdfGenerator`
* `DocumentDetector`
* `PerspectiveTransformer`
* `ImageEnhancer`

---

# 23. Screen-to-UseCase Map

This is important for implementation discipline.

| Screen   | Main use cases                                                                           |
| -------- | ---------------------------------------------------------------------------------------- |
| Scanner  | `CreateScanSessionUseCase`                                                               |
| Review   | `ProcessCapturedPageUseCase`, `AddProcessedPageUseCase`                                  |
| Editor   | `GetScanSessionUseCase`, `DeletePageUseCase`, `ReorderPagesUseCase`, `RotatePageUseCase` |
| Metadata | `UpdateScanMetadataUseCase`, `GenerateDocumentNameUseCase`, `ValidateMetadataUseCase`    |
| Export   | `GeneratePdfUseCase`, `SaveDocumentUseCase`, `UpdateScanSessionStatusUseCase`            |
| Library  | `ObserveSavedDocumentsUseCase`, `DeleteSavedDocumentUseCase`                             |

---

# 24. Suggested File List for First Implementation Pass

Here is the **minimum file set** to start coding.

```text
app/
  MainActivity.kt
  di/
    DatabaseModule.kt
    RepositoryModule.kt
    CoreModule.kt
  navigation/
    AppNavHost.kt
    AppRoute.kt

core/
  result/AppResult.kt
  time/TimeProvider.kt
  common/IdProvider.kt
  dispatchers/DispatcherProvider.kt
  file/FileManager.kt
  image/DocumentDetector.kt
  image/PerspectiveTransformer.kt
  image/ImageEnhancer.kt
  pdf/PdfGenerator.kt

data/local/
  db/AppDatabase.kt
  dao/ScanSessionDao.kt
  dao/ScannedPageDao.kt
  dao/SavedDocumentDao.kt
  entity/ScanSessionEntity.kt
  entity/ScannedPageEntity.kt
  entity/SavedDocumentEntity.kt
  mapper/ScanSessionMapper.kt
  mapper/ScannedPageMapper.kt
  mapper/SavedDocumentMapper.kt

domain/model/
  ScanSession.kt
  ScannedPage.kt
  DocumentMetadata.kt
  SavedDocument.kt
  PageCorners.kt
  ScanMode.kt
  ScanSessionStatus.kt
  ValidationResult.kt
  ProcessedPageResult.kt

domain/repository/
  ScanRepository.kt
  DocumentRepository.kt
  ImageProcessingRepository.kt
  PdfRepository.kt
  FileRepository.kt

domain/usecase/
  session/CreateScanSessionUseCase.kt
  session/GetScanSessionUseCase.kt
  page/ProcessCapturedPageUseCase.kt
  page/AddProcessedPageUseCase.kt
  page/DeletePageUseCase.kt
  page/ReorderPagesUseCase.kt
  export/GenerateDocumentNameUseCase.kt
  export/ValidateMetadataUseCase.kt
  export/GeneratePdfUseCase.kt
  export/SaveDocumentUseCase.kt
  library/ObserveSavedDocumentsUseCase.kt

feature/scanner/
  ScannerScreen.kt
  ScannerViewModel.kt
  ScannerUiState.kt

feature/review/
  ReviewScreen.kt
  ReviewViewModel.kt
  ReviewUiState.kt

feature/editor/
  EditorScreen.kt
  EditorViewModel.kt
  EditorUiState.kt

feature/metadata/
  MetadataScreen.kt
  MetadataViewModel.kt
  MetadataUiState.kt

feature/export/
  ExportScreen.kt
  ExportViewModel.kt
  ExportUiState.kt

feature/library/
  LibraryScreen.kt
  LibraryViewModel.kt
  LibraryUiState.kt
```

---

# 25. First Three Implementation Units

This is the correct engineering sequence.

---

## Unit 1: Data foundation

Build:

* models
* Room entities
* DAOs
* mappers
* repositories
* file manager

### Goal

The app can create sessions and persist fake pages.

---

## Unit 2: Manual scan flow

Build:

* scanner screen
* CameraX preview
* raw image capture
* review navigation

### Goal

The app can take a picture and enter review.

---

## Unit 3: Processing pipeline

Build:

* document detection
* perspective transform
* enhancement
* accept page into session

### Goal

The app can turn a raw photo into a usable scanned page.

---

# 26. Validation Rules

---

## Metadata validation

Required:

* grade not blank
* subject not blank
* year is numeric and reasonable
* paper type not blank

Optional:

* paper number
* source
* notes

### Year rule

Accept only a sensible range, for example:

```kotlin
year in 1980..currentYear + 1
```

---

# 27. Error Handling Rules

Map technical failures to clean user messages.

| Failure                  | User message                                        |
| ------------------------ | --------------------------------------------------- |
| Camera permission denied | Camera permission is required to scan pages.        |
| Capture failed           | Could not capture image. Please try again.          |
| No page detected         | Page edges were not detected. Adjust crop manually. |
| Processing failed        | Could not process this page. Please rescan.         |
| PDF failed               | Could not create PDF. Please try again.             |
| Storage full             | Not enough storage space to save this document.     |

---

# 28. Performance Constraints in Code

To avoid memory issues:

* do not load full bitmaps into Compose image lists
* generate and use thumbnails for list/grid UI
* process images off the main thread
* scale input during detection phase
* only use full resolution where necessary
* recycle or release native resources promptly

---

# 29. Testing Plan at LLD Level

---

## Unit tests

Test:

* filename generation
* metadata validation
* page reordering
* mapper correctness

---

## Instrumentation tests

Test:

* DB insert/read
* navigation flow
* export flow

---

## Manual QA dataset

Prepare 10–20 real pages:

* bright paper
* dark room
* skewed capture
* paper with diagrams
* paper with graphs
* paper with tables
* paper with faint print

This dataset is essential.

---

