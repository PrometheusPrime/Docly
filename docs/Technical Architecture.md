# Technical Architecture

## Project: **Docly**

This is the **technical architecture** for a mobile-first scanning system that digitizes papers documents into clean, readable, structured PDFs. It is optimized for **Android**, **offline-first operation**, and **low-to-mid range devices**.

---

# 1. Architecture Goals

The architecture must support:

* high-quality page capture
* perspective correction
* document enhancement
* multi-page document assembly
* PDF export
* metadata tagging
* local persistence
* future backend upload
* future OCR support

It must also remain:

* modular
* testable
* scalable
* performant on older Android devices

---

# 2. Architecture Style

Use a **layered modular architecture** with **MVVM + Use Cases**.

## Recommended structure

```text
presentation/
domain/
data/
core/
feature_scanner/
feature_document_editor/
feature_library/
feature_metadata/
feature_export/
```

This gives you separation between:

* UI logic
* business rules
* storage
* camera/image processing
* PDF generation
* future network sync

---

# 3. High-Level System Architecture

```text
┌──────────────────────────────┐
│        Presentation Layer    │
│ Compose UI + ViewModels      │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│         Domain Layer         │
│ Use Cases + Entities         │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│          Data Layer          │
│ Repositories                 │
│ Local DB / File Storage      │
│ PDF Service / OCR Service    │
└──────────────┬───────────────┘
               │
┌──────────────▼───────────────┐
│          Core Layer          │
│ CameraX / OpenCV / Utils     │
│ Image Processing / Logging   │
└──────────────────────────────┘
```

---

# 4. Module Breakdown

## 4.1 `core-camera`

Handles camera preview, capture, focus, exposure, and frame analysis.

### Responsibilities

* CameraX setup
* preview stream
* image capture
* analysis pipeline
* auto-capture readiness checks

### Main components

* `CameraController`
* `CameraFrameAnalyzer`
* `AutoCaptureEvaluator`
* `CameraPermissionManager`

---

## 4.2 `core-image-processing`

Handles all page detection and enhancement logic.

### Responsibilities

* edge detection
* contour finding
* quadrilateral extraction
* perspective correction
* denoise
* thresholding
* contrast enhancement
* sharpening
* optional glare scoring

### Main components

* `DocumentDetector`
* `CornerOrderingUtil`
* `PerspectiveTransformer`
* `ImageEnhancer`
* `ScanModeProcessor`
* `ImageCompressionService`

### Likely implementation

* OpenCV
* some custom bitmap/matrix utilities
* JNI only if performance later demands it

---

## 4.3 `feature-scanner`

Owns the scanning workflow.

### Responsibilities

* start scan session
* show live overlay
* capture pages
* preview result
* accept/rescan page

### Main classes

* `ScannerViewModel`
* `ScannerScreen`
* `CapturePageUseCase`
* `ProcessCapturedPageUseCase`

---

## 4.4 `feature-document-editor`

Manages scanned pages after capture.

### Responsibilities

* reorder pages
* rotate pages
* delete pages
* crop adjust pages
* apply enhancement mode again

### Main classes

* `DocumentEditorViewModel`
* `DocumentEditorScreen`
* `ReorderPagesUseCase`
* `RotatePageUseCase`
* `DeletePageUseCase`

---

## 4.5 `feature-metadata`

Handles classification and naming.

### Responsibilities

* assign grade
* assign subject
* assign year
* assign paper type
* generate file names
* validate required fields

### Main classes

* `MetadataViewModel`
* `GenerateDocumentNameUseCase`
* `ValidateMetadataUseCase`

---

## 4.6 `feature-export`

Handles final output generation.

### Responsibilities

* PDF generation
* image export
* quality/compression selection
* save to file storage
* share/export intent

### Main classes

* `ExportViewModel`
* `GeneratePdfUseCase`
* `SaveDocumentUseCase`
* `ShareDocumentUseCase`

---

## 4.7 `feature-library`

Displays previously saved scans.

### Responsibilities

* list saved scans
* open scan details
* filter/search by metadata
* delete or re-export

### Main classes

* `LibraryViewModel`
* `GetSavedDocumentsUseCase`
* `DeleteDocumentUseCase`

---

## 4.8 `data-local`

Responsible for Room database and file persistence.

### Responsibilities

* store scan session metadata
* store page metadata
* store exported file references
* recover incomplete scan sessions

### Main components

* `AppDatabase`
* `DocumentDao`
* `PageDao`
* `ScanSessionDao`

---

# 5. Core Data Flow

## 5.1 Scan Flow

```text
Camera Preview
   ↓
Frame Analysis
   ↓
Document Boundary Detection
   ↓
Auto-Capture Readiness Check
   ↓
Image Capture
   ↓
Perspective Correction
   ↓
Enhancement Pipeline
   ↓
Temporary Page Save
   ↓
Review / Accept / Rescan
   ↓
Add to Scan Session
```

---

## 5.2 Export Flow

```text
Scan Session
   ↓
Metadata Validation
   ↓
Ordered Page Retrieval
   ↓
Render Pages to PDF
   ↓
Save PDF to App Storage
   ↓
Persist Document Record in DB
   ↓
Return URI / Share / Upload Later
```

---

# 6. Domain Model

Use plain Kotlin domain models, independent of Android framework.

## 6.1 Main entities

### `ScanSession`

```kotlin
data class ScanSession(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ScanSessionStatus,
    val pages: List<ScannedPage>
)
```

### `ScannedPage`

```kotlin
data class ScannedPage(
    val id: String,
    val sessionId: String,
    val originalImagePath: String,
    val processedImagePath: String,
    val pageIndex: Int,
    val rotationDegrees: Int,
    val scanMode: ScanMode,
    val width: Int,
    val height: Int
)
```

### `DocumentMetadata`

```kotlin
data class DocumentMetadata(
    val grade: String,
    val subject: String,
    val year: Int,
    val paperType: String,
    val paperNumber: String?,
    val source: String?
)
```

### `SavedDocument`

```kotlin
data class SavedDocument(
    val id: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val metadata: DocumentMetadata,
    val pageCount: Int,
    val createdAt: Long
)
```

---

# 7. Presentation Layer Architecture

Use **Jetpack Compose + ViewModel + StateFlow**.

## 7.1 Pattern

Each feature should expose:

* `UiState`
* `UiEvent`
* `UiEffect` for one-time actions

### Example

```kotlin
data class ScannerUiState(
    val isCameraReady: Boolean = false,
    val detectedCorners: List<PointF> = emptyList(),
    val isAutoCaptureReady: Boolean = false,
    val lastCapturedPage: String? = null,
    val errorMessage: String? = null
)
```

---

## 7.2 Recommended screens

### Scanner

* camera preview
* page guide overlay
* capture button
* mode switch
* flash toggle
* stability hints

### Page Review

* captured page preview
* crop adjustment
* rotate
* accept
* rescan

### Document Editor

* page thumbnails
* drag reorder
* delete page
* add page
* continue

### Metadata Screen

* grade
* subject
* year
* paper type
* file name preview

### Export Screen

* export as PDF
* quality setting
* save
* share

### Library Screen

* list of saved scans
* search/filter
* open/export/delete

---

# 8. Data Layer Architecture

Repositories should abstract away Room, files, and services.

## 8.1 Repository interfaces

### `ScanRepository`

```kotlin
interface ScanRepository {
    suspend fun createSession(): ScanSession
    suspend fun addPage(sessionId: String, page: ScannedPage)
    suspend fun getSession(sessionId: String): ScanSession?
    suspend fun updatePageOrder(sessionId: String, orderedPageIds: List<String>)
    suspend fun deletePage(pageId: String)
    suspend fun finalizeSession(sessionId: String)
}
```

### `DocumentRepository`

```kotlin
interface DocumentRepository {
    suspend fun saveDocument(document: SavedDocument)
    suspend fun getDocuments(): List<SavedDocument>
    suspend fun getDocumentById(id: String): SavedDocument?
    suspend fun deleteDocument(id: String)
}
```

### `PdfRepository`

```kotlin
interface PdfRepository {
    suspend fun generatePdf(
        pages: List<ScannedPage>,
        outputName: String
    ): String
}
```

---

# 9. Storage Architecture

Use two storage types:

## 9.1 Room Database

Store structured metadata only.

### Tables

* `scan_sessions`
* `scanned_pages`
* `saved_documents`

Do **not** store large images in the database.

---

## 9.2 File Storage

Store actual image and PDF files in app-specific storage.

### Recommended directories

```text
/files/scans/raw/
/files/scans/processed/
/files/scans/thumbnails/
/files/documents/pdf/
```

### Why

* easier cleanup
* lower DB bloat
* simpler file lifecycle control

---

# 10. Image Processing Pipeline

This is the heart of the system.

## 10.1 Pipeline stages

### Stage 1: Input normalization

* decode image efficiently
* rotate based on EXIF if needed
* scale to working resolution

### Stage 2: Document detection

* grayscale conversion
* blur
* edge detection
* contour detection
* identify best quadrilateral
* fallback to manual crop if detection fails

### Stage 3: Perspective transform

* order corners
* compute homography
* warp into flat document rectangle

### Stage 4: Enhancement

Depends on selected mode.

#### Document mode

* grayscale
* adaptive threshold
* denoise
* sharpen text

#### Mixed mode

* moderate contrast boost
* mild denoise
* line preservation
* preserve diagrams

#### Color mode

* white balance adjustment
* mild sharpening
* color preservation

### Stage 5: Compression and save

* save processed page as JPEG or PNG depending on mode
* generate thumbnail
* persist metadata

---

# 11. Scan Quality Strategy

You need a quality gate before capture and before export.

## 11.1 Pre-capture checks

* document visible
* corners stable for N frames
* brightness acceptable
* blur below threshold
* device motion low

## 11.2 Post-capture checks

* page not too dark
* page not too blurry
* page not overexposed
* usable contrast

If a capture fails quality threshold:

* show warning
* allow immediate retake

---

# 12. Camera Architecture

Use **CameraX**.

## 12.1 Use cases

* `Preview`
* `ImageCapture`
* `ImageAnalysis`

## 12.2 Analyzer

The analyzer runs on preview frames and computes:

* page contour
* stability score
* brightness score
* blur score
* auto-capture readiness

## 12.3 Auto-capture logic

Auto-capture only when:

* contour exists
* area large enough
* stability maintained for a short duration
* blur score acceptable
* no rapid movement

---

# 13. PDF Generation Architecture

PDF generation should be isolated behind a service.

## 13.1 Inputs

* ordered list of processed pages
* output title
* page size preference
* compression setting

## 13.2 Outputs

* PDF file path
* thumbnail preview
* metadata record

## 13.3 Implementation options

For MVP:

* Android `PdfDocument`

Later, if needed:

* a more advanced PDF library for:

  * metadata embedding
  * better compression
  * OCR text layer insertion

---

# 14. Metadata Architecture

Metadata should be independent from the raw scan session.

## 14.1 Required fields

* grade
* subject
* year
* paper type

## 14.2 Optional fields

* paper number
* source
* notes
* tags

## 14.3 Filename generation

Use normalized naming:

```text
G12_Mathematics_2023_Internal_Paper1.pdf
```

Rules:

* replace spaces with underscores
* strip special characters
* consistent casing

---

# 15. Offline-First Architecture

This should be an offline-first tool.

## Core principle

Everything needed for scanning and export must work without internet.

### Local-only MVP features

* scan pages
* process pages
* save PDF
* view library
* edit metadata

### Future online features

* upload to backend
* OCR cloud processing
* deduplication
* admin sync

---

# 16. Future Sync Architecture

When backend arrives, use a sync queue model.

## 16.1 Upload queue

Each saved document can have:

* local-only
* pending upload
* uploaded
* failed upload

## 16.2 Sync worker

Use `WorkManager` for:

* background retry
* network constraint handling
* charging/Wi-Fi preferences if needed

---

# 17. OCR Architecture (Future)

Do not tightly couple OCR to scan generation.

OCR should be a separate pipeline.

## 17.1 OCR modes

### Local OCR

* faster to start
* privacy-friendly
* weaker for complex text

### Cloud OCR

* stronger results
* more expensive
* requires backend

## 17.2 Recommended OCR architecture

```text
Saved PDF / Processed Pages
    ↓
OCR Service
    ↓
Extracted Text Blocks
    ↓
Indexed Search Data
```

For mathematics, treat OCR as:

* supportive
* not authoritative

---

# 18. Error Handling Architecture

Use a consistent result wrapper.

### Example

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()
}
```

## Error categories

* camera unavailable
* permission denied
* document not detected
* processing failed
* storage full
* PDF generation failed

Each feature should map technical errors to user-readable messages.

---

# 19. Performance Architecture

Because your target includes lower-end devices, performance matters.

## Strategies

* process preview frames at reduced resolution
* process full-resolution only on actual capture
* use background dispatchers for image processing
* reuse buffers where possible
* avoid large bitmap retention in memory
* cache thumbnails, not full images
* paginate library results if needed

## Threading suggestion

* `Main`: UI only
* `Default`: image transforms
* `IO`: file reads/writes, DB, PDF save

---

# 20. Security and Privacy Architecture

For MVP:

* store files in app-specific private storage
* no mandatory cloud upload
* no user data collection required for scanning

Future:

* encrypt sensitive metadata if needed
* signed upload requests
* server-side validation

---

# 21. Testing Architecture

You need tests at multiple levels.

## 21.1 Unit tests

Test:

* filename generation
* metadata validation
* page ordering
* export orchestration
* quality evaluation logic

## 21.2 Integration tests

Test:

* repository + Room
* file save + DB record creation
* PDF output generation

## 21.3 Instrumentation tests

Test:

* camera permission flow
* scan UI flow
* multi-page editor flow

## 21.4 Image-processing test set

Create a fixed dataset of:

* bright pages
* dark pages
* skewed pages
* pages with diagrams
* pages with shadows

Use it to validate:

* detection accuracy
* readability
* consistency

This is very important.

---

# 22. Suggested Package Structure

```text
com.docly.app
├── core
│   ├── camera
│   ├── image
│   ├── pdf
│   ├── util
│   └── logging
├── data
│   ├── db
│   ├── repository
│   ├── storage
│   └── mapper
├── domain
│   ├── model
│   ├── repository
│   └── usecase
├── feature
│   ├── scanner
│   ├── review
│   ├── editor
│   ├── metadata
│   ├── export
│   └── library
└── app
    ├── navigation
    ├── di
    └── ui
```

---

# 23. Recommended Tech Stack

## Core stack

* **Kotlin**
* **Jetpack Compose**
* **CameraX**
* **Room**
* **WorkManager**
* **Coroutines / Flow**
* **OpenCV**

## Nice additions later

* **ML Kit Text Recognition**
* **Hilt** for dependency injection
* **Timber** or structured logging abstraction
* **Coil** for image loading in page thumbnails

---

# 24. MVP Technical Cut

To keep the build realistic, the MVP should include only this:

## Included

* manual capture
* edge detection
* manual corner adjustment
* perspective correction
* 3 enhancement modes
* multi-page session
* reorder/delete/rotate pages
* metadata entry
* PDF export
* local library

## Excluded from MVP

* OCR
* anti-glare multi-frame fusion
* backend upload
* auto subject/year detection
* public contribution workflows

That cut is aggressive enough to be useful, but still buildable.

---

# 25. Recommended Build Sequence

## Phase 1: Foundation

* project setup
* navigation
* Room
* file storage
* base domain/data structure

## Phase 2: Scanner

* CameraX preview
* manual capture
* page detection overlay

## Phase 3: Processing

* perspective correction
* enhancement modes
* page save pipeline

## Phase 4: Editor

* page review
* reorder/delete/rotate
* rescan support

## Phase 5: Export

* metadata form
* PDF generator
* document library

## Phase 6: Hardening

* quality checks
* performance optimization
* test dataset
* crash reduction

---

# 26. Architecture Decision Summary

## Chosen decisions

* **Android-first**
* **offline-first**
* **MVVM + use cases**
* **Room + file storage**
* **CameraX for capture**
* **OpenCV for processing**
* **PDF generation as isolated service**
* **OCR deferred**
* **internal/admin ingestion first**
