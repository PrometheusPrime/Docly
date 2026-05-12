# Implementation Roadmap

## 1. Product Goal

Docly is a local-first Android document utility app. It helps users scan, read, create, edit, convert, manage, and share everyday documents from one phone.

The product is built in this order:

1. Document scanner
2. Document reader
3. Document creator
4. Document editor
5. Document converter

The roadmap intentionally starts with a strong scanner because scan-to-PDF is the highest-value workflow and becomes input for later reading, editing, and conversion features.

Primary user promise:

- A user can turn paper or existing files into useful local documents, open them reliably, make practical edits, export them to supported formats, and share them without needing an account or server conversion.

Engineering promise:

- The app uses a capability-based document architecture, so unsupported actions are not shown and new formats can be added without rewriting the app.

## 2. Product Scope And Guardrails

MVP positioning:

- Docly is a mobile document scanner and lightweight document workspace.
- It supports high-quality scanning, local document management, PDF reading, text-based editing, PDF creation, and practical conversion.
- It does not claim to be a full PDF, Word, or Excel replacement.

MVP included:

- Local document library with search, sort, filter, rename, delete, share, and import.
- Document scanner using a scanner engine abstraction, with ML Kit Document Scanner as the first preferred engine.
- Scan review with page reorder, rotate, delete, add page, and save as PDF or images.
- PDF reader.
- TXT, Markdown, and HTML readers, creators, and editors.
- PDF creation from scans, imported images, TXT, Markdown, and HTML.
- Converter screen with supported-pair rules for TXT, Markdown, HTML, and images.
- Settings for appearance and core document defaults.

MVP excluded:

- Full PDF text editing.
- PDF to Word or PDF to Excel conversion.
- Perfect Word or Excel rendering.
- Full DOCX or XLSX editing.
- Advanced Excel formulas, macros, track changes, collaborative editing, account sync, cloud backup, and server-side conversion.

Version 1.5 focus:

- Simplified DOCX reader.
- Simplified XLSX table reader.
- DOCX to TXT.
- XLSX to CSV and TXT.
- PDF page thumbnails and remember-last-page.
- Folders, templates, scanner filters, reader settings, export settings, and storage settings.

Version 2.0 focus:

- OCR tool and OCR result editor.
- Search scanned documents using OCR text.
- PDF annotation and page tools: merge, split, extract, rotate, delete, compress.
- Rich document editor.
- Simple table creator.
- Simple DOCX export and simple XLSX export from Docly-created documents.

Version 3.0 focus:

- Searchable PDFs.
- Advanced OCR layout reconstruction.
- Batch conversion.
- Cloud-assisted conversion only with explicit consent and privacy review.
- Premium subscription or feature gating if the product strategy requires it.

## 3. Core Product Decisions

Document model:

- Use one `DoclyDocument` model for all library items.
- Use `DocumentType` and `DocumentCapabilities` to decide which actions are available.
- Imported files are copied into app-managed storage by default for stable access.
- External URI references may exist, but must be treated as fragile because permissions can change.

Scanner engine:

- Use ML Kit Document Scanner first because it provides a maintained scanner flow and returns JPEG/PDF outputs.
- Keep `DocumentScannerService` behind an interface so a custom CameraX/OpenCV PhotoScan-style scanner can be added later for advanced control.
- Existing custom CameraX/OpenCV code may be reused where it helps, but the architecture source of truth is the new document workspace model.

Editing strategy:

- TXT, Markdown, and HTML are directly editable in MVP.
- PDF editing means page management and later annotation, not editing original PDF text/layout.
- DOCX and XLSX are not directly edited early. Future support imports them into simplified Docly models and exports new copies.

Conversion strategy:

- Use a converter registry with explicit supported input/output pairs.
- Show only supported output formats.
- Label DOCX/XLSX conversions as simplified when layout fidelity is limited.
- Do not add cloud conversion to MVP.

Platform strategy:

- Kotlin, Jetpack Compose, Material 3, MVVM, use cases, repositories, Room, DataStore, Coroutines, Flow, Hilt, SAF, and app-specific storage.
- Target Android API 26+ in the product docs. The current Gradle min SDK remains an implementation detail until a validated SDK change is made.
- Avoid broad storage permissions. Use SAF for files and Photo Picker or scanner output URIs for images.

## 4. Supported Format Matrix

### MVP Matrix

| Format | View | Create | Edit | Convert/export |
| --- | --- | --- | --- | --- |
| PDF | Yes | From scans, images, TXT, Markdown, HTML | Page tools for Docly scan source only | Share/export PDF |
| TXT | Yes | Yes | Yes | TXT, PDF, HTML, Markdown |
| Markdown | Yes | Yes | Yes | Markdown, HTML, PDF, TXT |
| HTML | Yes | Yes | Source edit | HTML, PDF, TXT |
| Images | Yes | From scan/import | Scan review crop/rotate/filter | PDF/images |
| DOCX | Reader epic after core readers, simplified | No | No | No in MVP |
| XLSX | Reader epic after core readers, simplified table | No | No | No in MVP |
| CSV | Later or converter support when XLSX arrives | Later | Later table editor | Later |

### Finished Product Matrix

| Format | View | Create | Edit | Convert/export |
| --- | --- | --- | --- | --- |
| PDF | Yes | Yes | Annotation/page tools | PDF, images, OCR text, limited future formats |
| TXT | Yes | Yes | Yes | TXT, PDF, HTML, Markdown |
| Markdown | Yes | Yes | Yes | Markdown, HTML, PDF, TXT |
| HTML | Yes | Yes | Source/simple editor | HTML, PDF, TXT |
| DOCX | Simplified | Simple export from Docly documents | Import to rich editor later | TXT, simple HTML, simple DOCX |
| XLSX | Simplified table | Simple table export | Simple table editor | CSV, TXT, simple XLSX |
| CSV | Yes | Yes | Simple table editor | CSV, TXT, XLSX |
| Images | Yes | Camera/import | Crop/filter | PDF, images, OCR text |

## 5. Phase-By-Phase Roadmap

### Phase 01 - Documentation And Product Reset

Objective:

- Reset the active docs from scanner-only planning to the broader document utility product.

Key tasks:

- Rewrite the active roadmap, architecture, LLD, build roadmap, privacy notes, and release checklist.
- Keep historical implementation notes intact and append a reset note.
- Record capability boundaries for PDF, DOCX, and XLSX.

Definition of done:

- `/docs` consistently describes Docly as a local-first document utility.
- No active doc promises full PDF, DOCX, or XLSX editing for MVP.
- Roadmaps build in the requested scanner, reader, creator, editor, converter order.

Validation:

- Search docs for stale scanner-only positioning and old cloud/upload assumptions.
- Review the supported format matrices for consistency.

### Phase 02 - Unified Document Foundation

Objective:

- Build the product substrate that every later feature uses.

Key tasks:

- Introduce `DoclyDocument`, `DocumentType`, `FileRef`, `DocumentSource`, `DocumentCapabilities`, `OcrStatus`, `SortMode`, and `ViewMode`.
- Replace scan-only library records with a unified `documents` table.
- Add folders, recent documents, conversion jobs, and scanner session/page tables.
- Create `DocumentRepository`, `FileTypeResolver`, `DocumentCapabilityResolver`, and `DoclyStorageManager`.
- Define internal directory structure for PDF, TXT, Markdown, HTML, DOCX, XLSX, CSV, images, scans, thumbnails, exports, temp, and OCR.
- Build Documents, Home, Search, Create, Tools, and Settings navigation placeholders.

Definition of done:

- The app can import supported files through SAF, copy them into internal storage, identify document type, and show them in the Documents screen.
- Rename, delete, share, sort, filter, favorite, and name search work from the document library.
- Unsupported actions are hidden or disabled based on document capabilities.

Validation:

- Unit tests for file type detection, capability resolution, mappers, and repository behavior.
- Room migration tests for new document tables.
- Manual import tests for PDF, TXT, MD, HTML, images, DOCX, and XLSX.

### Phase 03 - Document Scanner

Objective:

- Deliver the scan-to-document workflow first.

Key tasks:

- Add `DocumentScannerService` with an ML Kit implementation and a fake implementation for tests.
- Launch scanner from Home and Scan.
- Copy returned JPEG pages into scan storage.
- Create `ScanSession` and `ScanPage` records.
- Build Scan Review with thumbnails, reorder, rotate, delete, add page, and save actions.
- Export scanned pages as PDF through the image-to-PDF exporter.
- Export scanned pages as images when requested.
- Register PDF/image outputs as `DoclyDocument` records.

Definition of done:

- A user can scan one or more pages, review the pages, save a readable PDF, save images, and find the outputs in Documents.
- Camera permission is requested only when scanning starts.
- Scan cancellation, scanner unavailable, low storage, and file copy failures show clear errors.

Validation:

- Unit tests for scan session ordering, page operations, and export orchestration.
- Integration tests for image-to-PDF generation and document registration.
- Manual tests on a physical Android device with bright, dark, skewed, shadowed, and multi-page documents.

### Phase 04 - Document Reader

Objective:

- Let users open and read supported documents from the unified library.

Key tasks:

- Add `DocumentOpenResolver` that maps document types to reader routes.
- Build PDF reader using `PdfRenderer` or a selected viewer dependency, with lazy page rendering, page count, zoom, and navigation.
- Build TXT reader with text size and theme support.
- Build Markdown reader using a Markdown parser and sanitized HTML/Compose rendering.
- Build HTML reader using WebView with JavaScript disabled by default for local documents.
- Add simplified DOCX reader after core readers, extracting paragraphs, headings, lists, and simple tables.
- Add simplified XLSX table reader after core readers, supporting sheet tabs and lazy table display.
- Add clear simplified-mode messaging for DOCX and XLSX.

Definition of done:

- PDF, TXT, Markdown, HTML, DOCX, and XLSX can be opened from Documents.
- Core readers do not load large files fully into Compose state when avoidable.
- DOCX/XLSX readers make fidelity limits explicit.

Validation:

- Unit tests for reader route resolution and text extraction.
- Instrumentation tests for reader screen loading/error states.
- Manual tests with large PDFs, long text files, Markdown with tables/code, local HTML, basic DOCX, and basic XLSX.

### Phase 05 - Document Creator

Objective:

- Let users create documents in supported formats from the Create tab and scanner flows.

Key tasks:

- Build Create screen with document type selection for TXT, Markdown, HTML, PDF from scan/images, and future rich/table documents.
- Add `CreateDocumentUseCase` and default content factory for TXT, Markdown, and HTML.
- Create files in internal storage and register them in the `documents` table.
- Open the correct editor after creation.
- Add PDF creation from images/scans through `ImageToPdfExporter`.
- Add PDF creation from TXT, Markdown, and HTML through HTML rendering and WebView print/PDF pipeline.

Definition of done:

- A user can create TXT, Markdown, and HTML documents and immediately edit them.
- A user can create a PDF from scanned pages, imported images, TXT, Markdown, or HTML.
- Created documents appear in Documents and can be opened, renamed, shared, and deleted.

Validation:

- Unit tests for default content, create use case, and document registration.
- Integration tests for text/Markdown/HTML-to-PDF export.
- Manual tests for all Create entry points.

### Phase 06 - Document Editor

Objective:

- Add practical editing for formats Docly can safely own.

Key tasks:

- Build TXT editor with autosave, manual save, unsaved-state handling, and text search.
- Build Markdown editor with edit/preview modes and export actions.
- Build HTML source editor with source/preview modes and WebView preview.
- Add shared autosave controller, save status, conflict-safe file writes, and user-readable errors.
- Add PDF page management for Docly-created scan PDFs by retaining scan source pages and regenerating PDFs.
- Add future boundaries for rich document editor, table editor, and PDF annotation.

Definition of done:

- TXT, Markdown, and HTML documents can be created, edited, autosaved, reopened, and exported.
- PDF page management works for Docly-created scan PDFs without pretending to edit arbitrary PDF text.
- The app does not overwrite imported originals unless the user explicitly exports or replaces a file.

Validation:

- Unit tests for autosave scheduling, file writes, editor state, and export triggers.
- Instrumentation tests for editor text entry, preview toggles, and recovery after navigation.
- Manual tests for process death or app restart during editing.

### Phase 07 - Document Converter

Objective:

- Provide reliable conversion for supported pairs and clear messaging for unsupported pairs.

Key tasks:

- Add `ConversionPair`, `ConversionRequest`, `ConversionOptions`, `ConversionResult`, `ConversionJob`, and `ConverterEngine`.
- Add `ConverterRegistry` and `ConverterRepository`.
- Build Converter screen with input selection, type detection, supported output list, options, progress, result, open, share, and save actions.
- Implement MVP engines:
  - TXT to PDF, HTML, Markdown.
  - Markdown to HTML, PDF, TXT.
  - HTML to PDF, TXT.
  - Images to PDF.
- Add later engines:
  - DOCX to TXT.
  - DOCX to simple HTML.
  - XLSX to CSV.
  - XLSX to TXT.

Definition of done:

- The converter shows only supported output formats for the selected input.
- Conversion outputs are saved as `DoclyDocument` records.
- Failed conversions leave the input unchanged and show a clear error.

Validation:

- Unit tests for registry lookup, supported outputs, and each converter.
- Integration tests for conversion job persistence and output file registration.
- Manual tests for successful and failed conversion flows.

### Phase 08 - Settings, Polish, Accessibility, And Performance

Objective:

- Bring the app to product-quality behavior across common devices.

Key tasks:

- Add appearance settings, scanner defaults, reader defaults, export defaults, storage view, and cache clearing.
- Add empty/loading/error states across every screen.
- Add accessible labels, large-font support, small-phone layouts, and tablet-ready constraints.
- Generate thumbnails asynchronously and avoid full-size image rendering in lists.
- Add memory and performance checks for large PDFs, images, and long documents.

Definition of done:

- The app remains usable with large font sizes and TalkBack.
- Library scrolling is smooth with at least 100 documents.
- Large files fail gracefully or render lazily instead of crashing.

Validation:

- Compose UI tests for key states and actions.
- Manual accessibility pass.
- Manual performance pass on a low-to-mid range Android device.

### Phase 09 - Release Hardening

Objective:

- Prepare the local-first MVP for internal and public testing.

Key tasks:

- Review manifest permissions.
- Validate backup behavior and private file handling.
- Configure release signing, minification, and versioning.
- Build a release checklist covering scanner, readers, creators, editors, converters, SAF import/export, share, and permissions.
- Add privacy review gates before any cloud, analytics, crash reporting, or upload feature.

Definition of done:

- Release build compiles.
- Manifest contains only justified permissions.
- Smoke tests pass on at least one physical device.

Validation:

- Run local build/test gate.
- Install release APK on device and execute the release checklist.

## 6. Testing Strategy

Unit tests:

- File type resolver.
- Capability resolver.
- Conversion registry.
- Document repository and mappers.
- Scanner page ordering and PDF export orchestration.
- Text/Markdown/HTML editor autosave.
- Converter engines.
- Error mapping.

Integration tests:

- Room migrations and DAO behavior.
- SAF import copy and app-storage deletion.
- Image-to-PDF generation.
- Text/Markdown/HTML-to-PDF generation.
- Conversion job creation and output registration.

Instrumentation and Compose tests:

- Documents import/open/rename/delete/share flows.
- Scanner launch, review, and save states.
- Reader loading/error states.
- Editor save and preview flows.
- Converter selection and result flows.

Manual QA:

- Physical camera scanning.
- Large PDFs.
- Long TXT/Markdown/HTML files.
- Basic DOCX and XLSX files.
- Low storage.
- Permission denial.
- Share/export.
- Offline behavior.

## 7. Acceptance Criteria

Documentation acceptance:

- Active docs agree on product scope, MVP boundaries, architecture, and build order.
- No active doc describes Docly as only a scanner app.
- No active doc promises full PDF, DOCX, or XLSX editing for MVP.

Product acceptance for MVP:

- Scanner creates high-quality multi-page PDF/images.
- Documents library manages imported, scanned, created, and converted documents.
- Readers open PDF, TXT, Markdown, HTML, and simplified DOCX/XLSX.
- Creators/editors support TXT, Markdown, HTML, and PDF generation.
- Converter shows only supported output formats and saves outputs to the library.
- The full MVP works offline.

## 8. References

- Google ML Kit Document Scanner: <https://developers.google.com/ml-kit/vision/doc-scanner/android>
- Android `PdfRenderer`: <https://developer.android.com/reference/android/graphics/pdf/PdfRenderer>
- Android WebView printing: <https://developer.android.com/training/printing/html-docs>
- Android Storage Access Framework: <https://developer.android.com/training/data-storage/shared/documents-files>

## 9. Assumptions

- `com.docly.app` remains the canonical namespace.
- The app can remain a single `:app` module while package boundaries are prepared for future extraction.
- Existing scanner/OCR/upload work is useful implementation history but does not define the new product roadmap.
- MVP is local-first and does not require account, cloud sync, upload, or server conversion.
- Any future cloud feature requires explicit consent, privacy review, and separate release checklist updates.
