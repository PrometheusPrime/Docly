# Build Roadmap

## 1. Goal

Build Docly as a local-first Android document utility in this order:

1. Scanner
2. Reader
3. Creator
4. Editor
5. Converter

Every implementation block must leave the app buildable, runnable, and honest about supported capabilities.

## 2. Delivery Milestones

### Milestone A - Document Foundation

Goal:

- A unified document library exists before feature-specific workflows expand.

Includes:

- `DoclyDocument` model.
- Document metadata Room tables.
- Internal file storage.
- SAF import.
- File type detection.
- Capability resolver.
- Documents screen.
- Open/share/rename/delete/sort/filter.

Acceptance:

- A user can import PDF, TXT, Markdown, HTML, images, DOCX, and XLSX.
- The library identifies each type and shows only supported actions.

### Milestone B - Scanner MVP

Goal:

- A user can scan paper documents and save high-quality PDF/images.

Includes:

- ML Kit Document Scanner integration.
- Scan session/page persistence.
- Scan review screen.
- Reorder, rotate, delete, add page.
- PDF/image export.
- Output registration in Documents.

Acceptance:

- A multi-page scan becomes a readable PDF in the document library.
- Camera permission is requested only when scanning.

### Milestone C - Reader MVP

Goal:

- A user can open all required document formats with appropriate fidelity messaging.

Includes:

- PDF reader.
- TXT reader.
- Markdown reader.
- HTML reader.
- Simplified DOCX reader.
- Simplified XLSX table reader.

Acceptance:

- PDF, TXT, Markdown, HTML, DOCX, and XLSX files open from Documents.
- DOCX/XLSX readers clearly state simplified rendering limitations.

### Milestone D - Creator MVP

Goal:

- A user can create useful documents without leaving Docly.

Includes:

- Create screen.
- TXT creation.
- Markdown creation.
- HTML creation.
- PDF creation from scans/images.
- PDF creation from TXT, Markdown, and HTML.

Acceptance:

- Created documents appear in Documents and open in the right editor or reader.

### Milestone E - Editor MVP

Goal:

- A user can safely edit the formats Docly owns.

Includes:

- TXT editor.
- Markdown editor with preview.
- HTML source editor with preview.
- Autosave.
- Manual save.
- Scan-source PDF page management.

Acceptance:

- Edits survive close/reopen.
- PDF page changes are implemented by regenerating Docly-created scan PDFs, not by pretending to edit arbitrary PDF text.

### Milestone F - Converter MVP

Goal:

- A user can export practical supported formats through a clear converter flow.

Includes:

- Converter screen.
- Type detection.
- Supported output filtering.
- TXT, Markdown, HTML, and image conversion engines.
- Conversion job status.
- Output registration, open, and share.

Acceptance:

- Unsupported conversion pairs are not offered.
- Successful outputs appear in Documents.

### Milestone G - Product Hardening

Goal:

- The MVP is reliable enough for internal and public testing.

Includes:

- Settings.
- Accessibility.
- Large-file handling.
- Low-storage handling.
- Release manifest review.
- Privacy checklist.
- Device smoke testing.

Acceptance:

- Release build passes the documented gate and smoke test.

## 3. Implementation Blocks

### Block 01 - Product Reset And Architecture Alignment

Tasks:

- Update active docs.
- Align roadmap, architecture, LLD, privacy, and release checklist.
- Preserve implementation notes as history.

Done when:

- No active `/docs` file describes Docly as scanner-only.

Tests:

- Documentation search for stale scope and false feature promises.

### Block 02 - Unified Document Data Layer

Tasks:

- Add document, folder, recent, scan, OCR, and conversion job entities.
- Add DAOs, mappers, and migrations.
- Add file storage manager.
- Add file type resolver.
- Add capability resolver.

Done when:

- Imported files become `DoclyDocument` records.

Tests:

- Room, mapper, file resolver, and capability unit tests.

### Block 03 - Documents And Navigation Shell

Tasks:

- Build Home, Documents, Create, Tools, Settings navigation.
- Add Documents list/grid, search, sort, filter, action menu.
- Add import, open, share, rename, delete.

Done when:

- Users can manage imported files from one library.

Tests:

- Compose tests for empty, populated, search, filter, and destructive actions.

### Block 04 - Scanner

Tasks:

- Add `DocumentScannerService`.
- Implement ML Kit scanner service.
- Persist scan sessions and pages.
- Build scan review.
- Export scan to PDF/images.

Done when:

- A scanned PDF appears in Documents and opens.

Tests:

- Service fake tests, scan page operation tests, PDF export integration tests, real-device scan smoke test.

### Block 05 - Readers

Tasks:

- Build open resolver.
- Implement PDF, TXT, Markdown, HTML readers.
- Add simplified DOCX and XLSX readers.
- Add simplified-mode banners.

Done when:

- Required formats open without crashing and large PDFs render lazily.

Tests:

- Reader resolver tests, parser tests, screen state tests, manual fixture files.

### Block 06 - Creators

Tasks:

- Build Create screen.
- Add default content factory.
- Create TXT, Markdown, and HTML files.
- Create PDFs from scans/images/text/Markdown/HTML.

Done when:

- Created documents are registered and immediately usable.

Tests:

- Create use-case tests and PDF generation integration tests.

### Block 07 - Editors

Tasks:

- Add shared editor state and autosave controller.
- Implement TXT editor.
- Implement Markdown editor and preview.
- Implement HTML source editor and preview.
- Add scan-source PDF page management.

Done when:

- Supported edits persist safely and exported results match the latest saved content.

Tests:

- Autosave unit tests, editor ViewModel tests, Compose editor flow tests.

### Block 08 - Converter

Tasks:

- Add conversion model and registry.
- Add conversion job persistence.
- Build Converter screen.
- Implement MVP conversion engines.

Done when:

- Only supported conversions are shown and outputs become library documents.

Tests:

- Converter registry tests, per-engine tests, conversion job integration tests.

### Block 09 - Settings And Defaults

Tasks:

- Add DataStore settings.
- Add appearance, scanner, reader, export, and storage settings.
- Apply default settings through use cases.

Done when:

- Settings persist and affect new workflows predictably.

Tests:

- Settings repository tests and UI state tests.

### Block 10 - Hardening And Release

Tasks:

- Add accessibility labels and large-text support.
- Improve performance for large documents.
- Review permissions and backup rules.
- Run release checklist.

Done when:

- Release build and smoke tests pass.

Tests:

- Local Gradle gate, connected UI tests, physical device smoke test.

## 4. First Three Engineering Tasks

Task 1:

- Implement the unified document model, Room tables, storage manager, file type resolver, and capability resolver.

Task 2:

- Build the Documents screen and SAF import path so imported files appear in the library.

Task 3:

- Replace scanner-only entry with the ML Kit scanner service flow that saves scan outputs as library documents.

## 5. Technical Risks

PDF rendering risk:

- Large PDFs may exhaust memory.
- Mitigation: lazy render visible pages, limit bitmap cache, and test with large fixtures.

DOCX/XLSX fidelity risk:

- Office files can contain complex layout and formulas.
- Mitigation: simplified reader first, explicit messaging, no early direct editing.

Conversion quality risk:

- Some conversions cannot preserve formatting perfectly.
- Mitigation: supported-pair matrix, simplified wording, fixture-based tests.

Scanner dependency risk:

- ML Kit scanner relies on Google Play services.
- Mitigation: keep scanner interface replaceable and retain future CameraX/OpenCV fallback option.

Large-file risk:

- Images, PDFs, DOCX, and XLSX can be large.
- Mitigation: streaming, thumbnails, background work, paging, and low-storage checks.

## 6. Completion Criteria For MVP

The MVP is complete when a user can:

1. Import and manage documents in a local library.
2. Scan multi-page paper documents.
3. Save scans as PDF or images.
4. Open PDF, TXT, Markdown, HTML, simplified DOCX, and simplified XLSX.
5. Create TXT, Markdown, HTML, and PDFs from scans/images/text content.
6. Edit TXT, Markdown, and HTML.
7. Convert supported TXT, Markdown, HTML, and image inputs.
8. Share or export output documents.
9. Use the full MVP offline.
