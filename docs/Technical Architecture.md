# Technical Architecture

## 1. Architecture Goal

Docly is a local-first Android document workspace. The architecture must support this product loop:

```text
Scan -> Store -> Read -> Create/Edit -> Convert/Export -> Share
```

The central rule is capability-based behavior:

```text
Every document type declares what it can view, create, edit, annotate, and convert.
The UI only exposes actions that are technically supported.
```

This avoids misleading users with full PDF, DOCX, or XLSX editing promises before those systems exist.

## 2. High-Level Architecture

```text
Presentation
  Compose screens, ViewModels, UI state, navigation
        |
Domain
  Use cases, domain models, repository interfaces, capability rules
        |
Data
  Room, repository implementations, storage, parsers, converters
        |
Platform
  ML Kit Document Scanner, SAF, WebView, PdfRenderer, PdfDocument, WorkManager
```

Dependency direction:

- Presentation depends on Domain.
- Data depends on Domain.
- Domain has no Android framework dependencies.
- Platform integrations are hidden behind interfaces.

## 3. Recommended Stack

Core:

- Kotlin.
- Jetpack Compose.
- Material 3.
- MVVM with immutable state, events, and one-time effects.
- Use cases for business operations.
- Repository interfaces in domain and implementations in data.
- Coroutines and Flow.
- Room for metadata.
- DataStore for settings.
- Hilt for dependency injection.
- SAF for file import/export.
- App-specific storage for managed documents.

Document systems:

- ML Kit Document Scanner as the first scanner engine.
- Android `PdfRenderer` or a selected viewer dependency for PDF reading.
- Android `PdfDocument` for image-to-PDF generation.
- WebView print adapter for TXT/Markdown/HTML-to-PDF.
- WebView for HTML preview/reader with JavaScript disabled by default.
- Markdown parser for Markdown rendering and conversion.
- Apache POI or a commercial SDK later for simplified DOCX/XLSX parsing/export.
- ML Kit Text Recognition later for OCR.

## 4. Package Architecture

The MVP may remain a single Android app module, but packages should preserve extraction boundaries.

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
|-- scanner
|-- documents
|-- reader
|-- creator
|-- editor
|-- converter
|-- tools
|-- ocr
|-- settings
`-- ui
```

Future module split:

```text
:app
:core
:domain
:data
:feature-documents
:feature-scanner
:feature-reader
:feature-creator
:feature-editor
:feature-converter
:feature-tools
:feature-settings
:feature-ocr
```

## 5. Domain Model Strategy

The unified document model is the center of the app.

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

```kotlin
sealed class FileRef {
    data class InternalFile(val path: String) : FileRef()
    data class ExternalUri(val uri: String) : FileRef()
}
```

```kotlin
data class DoclyDocument(
    val id: String,
    val name: String,
    val type: DocumentType,
    val mimeType: String?,
    val fileRef: FileRef,
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

Capability profile:

```kotlin
data class DocumentCapabilities(
    val canView: Boolean,
    val canCreate: Boolean,
    val canEdit: Boolean,
    val canAnnotate: Boolean,
    val canConvert: Boolean,
    val isSimplifiedView: Boolean
)
```

Capability rules:

- PDF: view and create in MVP; page tools for Docly-created scan PDFs; annotation later.
- TXT, Markdown, HTML: view, create, edit, and convert in MVP.
- DOCX: simplified reader after core readers; no direct editing early.
- XLSX: simplified table reader after core readers; no full spreadsheet editing early.
- Images: view/import/scan and convert to PDF.

## 6. Storage Architecture

Room stores metadata only. Files store document content.

Room tables:

```text
documents
folders
scan_sessions
scan_pages
conversion_jobs
recent_documents
document_tags
ocr_results
settings
```

Internal file storage:

```text
files/docly/
|-- documents/
|   |-- pdf/
|   |-- txt/
|   |-- markdown/
|   |-- html/
|   |-- docx/
|   |-- xlsx/
|   |-- csv/
|   `-- images/
|-- scans/
|   |-- sessions/
|   `-- pages/
|-- thumbnails/
|-- exports/
|-- temp/
`-- ocr/
```

Storage rules:

- Imported files are copied into internal storage unless the user explicitly chooses URI-only behavior.
- Original external files are not overwritten unless the user explicitly exports over them through SAF.
- Thumbnails are generated for library and reader previews.
- Large binary content is never stored in Room.

## 7. Scanner Architecture

Scanner flow:

```text
User taps Scan
  -> DocumentScannerService launches scanner
  -> scanner returns image/PDF result URIs
  -> Docly copies pages into internal scan storage
  -> ScanSession and ScanPage records are created
  -> user reviews pages
  -> user saves PDF/images
  -> outputs become DoclyDocument records
```

Primary engine:

- `MlKitDocumentScannerService` wraps ML Kit Document Scanner.
- It returns scan result URIs and hides SDK details from ViewModels.

Engine abstraction:

```kotlin
interface DocumentScannerService {
    suspend fun scanDocuments(options: ScanOptions): AppResult<ScanResult>
}
```

Future engine:

- A custom CameraX/OpenCV scanner may be added later for more control, PhotoScan-style capture guidance, live overlays, advanced filters, and fallback support.
- The custom engine must implement the same service contract.

## 8. Reader Architecture

Reader resolution:

```text
DoclyDocument
  -> DocumentOpenResolver
  -> PDF/TXT/Markdown/HTML/DOCX/XLSX route
```

Reader engines:

- `PdfReaderEngine` renders PDF pages lazily and caches visible page bitmaps.
- `TextReaderEngine` streams or reads text safely based on file size.
- `MarkdownReaderEngine` parses Markdown to a preview model or sanitized HTML.
- `HtmlReaderEngine` loads local HTML into WebView.
- `DocxReaderEngine` extracts simplified blocks.
- `XlsxReaderEngine` extracts workbook sheets and cell values.

Large-file rules:

- Do not render all PDF pages at once.
- Do not keep full-resolution page bitmaps in Compose state.
- Show clear unsupported or simplified rendering messages when needed.

## 9. Creator And Editor Architecture

Creator:

```text
Create screen
  -> select type
  -> create internal file
  -> insert DocumentEntity
  -> open matching editor
```

Editable MVP formats:

- TXT uses direct text editing.
- Markdown uses source editing plus preview.
- HTML uses source editing plus WebView preview.

PDF creation:

- Images/scans to PDF use `PdfDocument`.
- TXT/Markdown/HTML to PDF use HTML rendering and WebView print.

Future internal model:

```kotlin
data class RichDocument(
    val id: String,
    val title: String,
    val blocks: List<DocumentBlock>,
    val createdAt: Long,
    val updatedAt: Long
)
```

This model supports later rich editing and export to PDF, HTML, Markdown, TXT, DOCX, and table-focused XLSX.

## 10. Converter Architecture

Conversion is registry-based.

```kotlin
data class ConversionPair(
    val input: DocumentType,
    val output: DocumentType
)
```

```kotlin
interface ConverterEngine {
    val supportedPairs: List<ConversionPair>
    suspend fun convert(request: ConversionRequest): AppResult<ConversionResult>
}
```

```text
Converter screen
  -> input selection
  -> type detection
  -> supported output lookup
  -> conversion job
  -> output file
  -> DoclyDocument registration
```

MVP conversion engines:

- TXT to PDF, HTML, Markdown.
- Markdown to HTML, PDF, TXT.
- HTML to PDF, TXT.
- Images to PDF.

Later conversion engines:

- DOCX to TXT.
- DOCX to simple HTML.
- XLSX to CSV.
- XLSX to TXT.
- PDF to images or OCR text.

## 11. OCR And Background Work

OCR is not part of the critical MVP path.

OCR architecture:

```text
Scanned image or scanned PDF
  -> OCR service
  -> page-level text result
  -> OCR result editor
  -> local search index
```

Use WorkManager for:

- OCR.
- Batch conversion.
- Thumbnail generation.
- PDF compression.
- Future cloud backup only after privacy review.

OCR must fail independently:

- Failed OCR must not corrupt the source document.
- OCR text is support data, not the authoritative document.

## 12. Privacy And Security Architecture

MVP privacy posture:

- Local-first.
- No account required.
- No server conversion.
- No automatic upload.
- No document content analytics.
- Camera permission requested only when scanning.
- SAF used for import/export.
- FileProvider used only for explicit share/open actions.

Future cloud posture:

- Cloud features require explicit user action, documented consent, privacy review, and release checklist updates.
- Temporary uploads must be encrypted and deleted according to a defined retention policy.

## 13. Testing Architecture

Unit tests:

- Capability resolver.
- File type resolver.
- Conversion registry.
- Use cases.
- Mappers.
- Error mapping.
- Autosave.

Integration tests:

- Room migrations.
- Document import and file copy.
- PDF generation.
- WebView print/PDF output where practical.
- Converter output registration.

Instrumentation tests:

- Documents screen.
- Scanner result handling.
- Reader screens.
- Editor save flows.
- Converter flow.
- Permission denial states.

Manual QA:

- Physical scanner flow.
- Large PDFs.
- Long text files.
- Basic DOCX/XLSX.
- Low storage.
- Offline use.
- Share/export with Android share sheet and SAF.

## 14. References

- Google ML Kit Document Scanner: <https://developers.google.com/ml-kit/vision/doc-scanner/android>
- Android `PdfRenderer`: <https://developer.android.com/reference/android/graphics/pdf/PdfRenderer>
- Android WebView printing: <https://developer.android.com/training/printing/html-docs>
- Android Storage Access Framework: <https://developer.android.com/training/data-storage/shared/documents-files>
