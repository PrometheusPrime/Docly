# Implementation Roadmap

## 1. Product Goal

Docly is an Android document scanner for turning camera captures or existing device photos into clean digital document copies. The MVP must work offline and support the full local workflow: capture or import pages, detect document boundaries, correct perspective, enhance readability, review pages, add metadata, export a PDF, and find the saved document in a local library.

The full product extends the MVP with OCR, search over extracted text, optional backend upload, sync, advanced capture automation, and production diagnostics. These later capabilities must not complicate the first local scanning release.

Primary user outcome:

- A user can scan or import all pages of a document and export a readable, ordered PDF with structured metadata.

Engineering outcome:

- The app remains modular, testable, offline-first, performant on low-to-mid range Android devices, and aligned with the existing architecture and LLD docs.

## 2. Current Project Baseline

Current repository facts:

- Android namespace and application ID are `com.docly.app`.
- The project is a single `:app` module.
- The app currently contains the default Compose `Hello Android` shell.
- Compile and target SDK are configured for SDK 36, with min SDK 28.
- Existing docs define MVVM, use cases, repository abstractions, Room plus file storage, CameraX, OpenCV, Compose navigation, Hilt, Coil, and Android PDF generation.
- The Phase 01 audit found no active legacy package namespace references in source, Gradle, or current docs; keep future implementation standardized on `com.docly.app`.

Baseline implementation rule:

- Keep the project in one app module for the MVP, but organize packages as if they can be extracted into modules later.

Canonical package layout:

```text
com.docly.app
+-- app
|   +-- di
|   +-- navigation
+-- core
|   +-- camera
|   +-- common
|   +-- dispatchers
|   +-- file
|   +-- image
|   +-- logging
|   +-- pdf
|   +-- result
|   +-- time
+-- data
|   +-- local
|   +-- mapper
|   +-- repository
|   +-- storage
+-- domain
|   +-- model
|   +-- repository
|   +-- usecase
+-- feature
|   +-- editor
|   +-- export
|   +-- library
|   +-- metadata
|   +-- review
|   +-- scanner
+-- ui
    +-- components
    +-- theme
    +-- util
```

## 3. Architecture Guardrails

Use the architecture from `Technical Architecture.md` and `Low-Level Design (LLD).md` as the source of truth.

Implementation rules:

- Use MVVM with `ViewModel`, immutable UI state, UI events, and one-time UI effects.
- Use use cases for business actions; keep ViewModels thin.
- Use repository interfaces in the domain layer and implementations in the data layer.
- Store metadata in Room and files in app-specific storage. Do not store bitmaps or PDFs in Room.
- Keep image processing behind interfaces so OpenCV details do not leak into UI or domain code.
- Keep PDF generation behind a `PdfGenerator` service interface.
- Keep OCR, backend upload, sync, and auto-capture out of the MVP critical path.
- Every phase must leave the app buildable and runnable.
- Prefer simple, working behavior before advanced automation.

Threading rules:

- Main dispatcher: UI state only.
- IO dispatcher: database, file reads/writes, URI import/export, PDF save.
- Default dispatcher: image analysis and CPU-heavy transforms.

Storage rules:

- Use app-specific storage for MVP scan assets.
- Use a controlled directory structure for raw images, processed images, thumbnails, and PDFs.
- Clean up orphaned temporary files during session abandonment, export completion, and app startup recovery checks.

## 4. API and Dependency Guardrails

Use current Android APIs and avoid deprecated or fragile patterns.

Required modern APIs:

- Camera: CameraX `Preview`, `ImageCapture`, and optional `ImageAnalysis`.
- Permission requests: `rememberLauncherForActivityResult` with `ActivityResultContracts.RequestPermission`.
- Device photo import: Android Photo Picker with `ActivityResultContracts.PickVisualMedia` and `PickMultipleVisualMedia`.
- Navigation: Navigation Compose with type-safe serializable route objects where supported.
- Persistence: Room with Kotlin coroutines, Flow, exported schemas, and KSP annotation processing.
- PDF generation: Android `PdfDocument`, wrapped behind a `PdfGenerator` interface.
- Background sync after MVP: WorkManager with network, retry, and charging constraints as needed.

Avoid:

- Legacy Android Camera APIs.
- `startActivityForResult` and `onActivityResult`.
- Passing raw file paths through navigation route strings.
- Broad storage permissions for importing user images.
- Loading full-resolution bitmaps into Compose lists or thumbnails.
- Heavy OpenCV or PDF work in ViewModels or on the main thread.
- Tight coupling between OCR and PDF export.

Official references for implementers:

- CameraX architecture: <https://developer.android.com/training/camerax/architecture>
- Photo Picker: <https://developer.android.com/training/data-storage/shared/photopicker>
- `ActivityResultContracts.PickVisualMedia`: <https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.PickVisualMedia>
- Type-safe Navigation Compose: <https://developer.android.com/guide/navigation/type-safe-destinations>
- Room: <https://developer.android.com/training/data-storage/room>
- `PdfDocument`: <https://developer.android.com/reference/android/graphics/pdf/PdfDocument>
- WorkManager constraints: <https://developer.android.com/guide/background/persistent/getting-started/define-work>

Initial dependency categories:

- Compose Material 3 and Compose UI.
- Navigation Compose.
- Lifecycle ViewModel Compose.
- Hilt and Hilt Navigation Compose.
- Room runtime, KTX, and KSP compiler.
- CameraX camera2, lifecycle, view, and extensions if needed.
- Coil Compose for image display.
- OpenCV Android integration.
- Kotlin coroutines and Flow.
- WorkManager for post-MVP sync.

## 5. Phase-by-Phase Implementation Plan

### Phase 01 - Project Baseline Audit and Documentation Alignment

Objective:

- Establish the real starting point and remove ambiguity before implementation starts.

Key Tasks:

- Confirm Gradle, Kotlin, AGP, Compose, compile SDK, min SDK, target SDK, and current package namespace.
- Record `com.docly.app` as the canonical namespace.
- Compare `Technical Architecture.md`, `Low-Level Design (LLD).md`, and `Build Roadmap.md` for conflicts.
- Add a short internal note or issue for any legacy package names and outdated references found in docs.

Definition of Done:

- The app still launches with the default shell.
- Implementation notes consistently use `com.docly.app`.
- Any doc conflicts are known before code is created.

Tests / Validation:

- Run the current Gradle build.
- Run existing unit and instrumentation test tasks if available.

### Phase 02 - Gradle Version Catalog, Plugins, and Dependency Setup

Objective:

- Add all foundation dependencies in a controlled, version-catalog-driven way.

Key Tasks:

- Add Hilt, KSP, Room, Navigation Compose, CameraX, Coil Compose, WorkManager, and test dependencies.
- Add OpenCV using the chosen integration method and document it in the build notes.
- Prefer stable AndroidX and Kotlin-compatible versions.
- Keep dependency aliases grouped by subsystem in `libs.versions.toml`.

Definition of Done:

- Dependencies resolve cleanly.
- The app compiles with the new plugin setup.
- No implementation code depends on deprecated APIs.

Tests / Validation:

- Run Gradle sync.
- Run `./gradlew assembleDebug`.
- Run `./gradlew testDebugUnitTest` if configured.

### Phase 03 - Static Quality Setup

Objective:

- Add repeatable quality checks before the codebase grows.

Key Tasks:

- Add detekt, ktlint, or the team's selected Kotlin style tool.
- Add a basic CI-friendly Gradle check command.
- Configure test source sets and Android test dependencies.
- Decide baseline package naming and file naming conventions.

Definition of Done:

- Developers can run one command that compiles and checks the app.
- Style rules are documented and not overly noisy.
- The current project passes the initial quality check.

Tests / Validation:

- Run the quality check command.
- Confirm generated reports are ignored or stored intentionally.

### Phase 04 - Package Architecture Skeleton

Objective:

- Create the app structure that matches the architecture without adding behavior yet.

Key Tasks:

- Create package directories under `com.docly.app` for `app`, `core`, `data`, `domain`, `feature`, and `ui`.
- Add empty or minimal package markers only where useful.
- Keep the single `:app` module, but preserve extraction-friendly boundaries.

Definition of Done:

- Package structure matches this roadmap.
- Default app behavior is unchanged.
- No circular dependencies are introduced.

Tests / Validation:

- Run `assembleDebug`.
- Confirm no unused implementation placeholders are required for compilation.

### Phase 05 - Compose App Shell, Theme, and Navigation Host

Objective:

- Replace the default screen with the Docly app shell and navigable placeholder screens.

Key Tasks:

- Add `AppNavHost` and typed route definitions.
- Add placeholder screens for Scanner, Review, Editor, Metadata, Export, and Library.
- Keep navigation arguments ID-based only.
- Refine Material 3 theme tokens for a document scanning tool.
- Add reusable loading, error, empty, and top bar components.

Definition of Done:

- App opens to the scanner or library start destination.
- Placeholder navigation works without passing file paths in routes.
- UI uses the Docly theme consistently.

Tests / Validation:

- Compose preview builds.
- Basic navigation UI test verifies route transitions.
- Manual launch on emulator confirms no startup crash.

### Phase 06 - Hilt Dependency Injection Foundation

Objective:

- Prepare dependency injection before repositories, use cases, and platform services expand.

Key Tasks:

- Add the Hilt application class and manifest wiring.
- Add DI modules for core providers, database, repositories, image processing, and PDF services.
- Add Hilt ViewModel setup.
- Add test seams for dispatchers, ID generation, time, and file directories.

Definition of Done:

- Hilt initializes on app startup.
- Placeholder ViewModels can be injected.
- Core providers can be overridden in tests.

Tests / Validation:

- Run `assembleDebug`.
- Add a small injection smoke test if practical.

### Phase 07 - Core Utilities

Objective:

- Establish shared primitives used across the app.

Key Tasks:

- Implement `AppResult` with success/error handling helpers.
- Implement `DispatcherProvider`.
- Implement `IdProvider` backed by UUID.
- Implement `TimeProvider`.
- Implement a logging abstraction that can later be backed by Timber or platform logging.
- Define common error categories for camera, permission, processing, storage, PDF, and validation failures.

Definition of Done:

- Domain, data, and feature layers can use the same result and dispatcher types.
- No feature-specific result wrappers exist.
- Error messages can be mapped to user-readable text.

Tests / Validation:

- Unit tests for result helpers if they contain behavior.
- Unit tests for deterministic test providers.

### Phase 08 - Domain Models and Repository Interfaces

Objective:

- Implement the domain contract before data and UI become coupled.

Key Tasks:

- Add domain models: `ScanSession`, `ScannedPage`, `DocumentMetadata`, `SavedDocument`, `PageCorners`, `PointFSerializable`, `ScanMode`, `ScanSessionStatus`, `ProcessedPageResult`, and `ValidationResult`.
- Add repository interfaces for scan sessions, saved documents, image processing, PDF generation, and file storage.
- Add use case class shells for session, page, metadata, export, and library actions.

Definition of Done:

- Domain models contain no Android framework dependencies.
- Use cases depend on repository interfaces, not implementations.
- Package names and model names align with the LLD.

Tests / Validation:

- Compile-only validation.
- Unit tests for pure value behavior where present.

### Phase 09 - Room Database, DAOs, Mappers, and Migrations

Objective:

- Persist sessions, pages, and saved document metadata safely.

Key Tasks:

- Add Room entities for scan sessions, scanned pages, and saved documents.
- Add DAOs with suspend functions and Flow observers where appropriate.
- Add entity/domain mappers outside ViewModels.
- Export Room schemas.
- Add migration policy starting at version 1.
- Ensure pages are ordered by `pageIndex`.

Definition of Done:

- Room initializes through Hilt.
- Sessions, pages, and saved documents can be inserted, read, updated, and deleted.
- Database does not store bitmap or PDF binary data.

Tests / Validation:

- Room instrumentation tests for insert/read/update/delete.
- Mapper unit tests for all fields, including nullable metadata and corners.

### Phase 10 - App-Specific File Storage

Objective:

- Store scan assets with predictable lifecycle management.

Key Tasks:

- Implement file directory creation for raw, processed, thumbnails, and PDFs.
- Implement path creation methods for session images, processed images, thumbnails, and PDF outputs.
- Add deletion helpers for individual files, page assets, sessions, and saved documents.
- Add storage availability checks before capture and export.

Definition of Done:

- File APIs create directories on demand.
- File naming is deterministic enough for debugging and unique enough for real use.
- Deleting a page or document cleans up its associated files.

Tests / Validation:

- Unit or instrumentation tests using temporary directories.
- Manual verification that generated files are under app-specific storage.

### Phase 11 - Device Photo Import

Objective:

- Let users build scans from existing device photos without broad storage permissions.

Key Tasks:

- Use Android Photo Picker through `ActivityResultContracts.PickVisualMedia`.
- Add multi-select import through `PickMultipleVisualMedia`.
- Copy selected URI content into Docly app-specific raw storage.
- Preserve import order where the picker provides it.
- Route imported images into the same review and processing flow as camera captures.

Definition of Done:

- User can select one or more existing images.
- The app does not request broad media storage permission for import.
- Imported images become raw session pages.

Tests / Validation:

- Manual test with camera photos and screenshots.
- Validate permission behavior on supported Android versions.
- Unit test URI copy error handling through fake input streams where practical.

### Phase 12 - Camera Permission Flow and CameraX Preview

Objective:

- Show a stable camera preview with a correct permission experience.

Key Tasks:

- Request camera permission using `rememberLauncherForActivityResult`.
- Add permission denied and permanently denied UI states.
- Bind CameraX `Preview` to lifecycle.
- Keep camera setup in `core.camera` and feature state in `feature.scanner`.
- Add flash mode UI if device support exists.

Definition of Done:

- Camera preview works on a real device.
- Permission denial does not crash the app.
- Camera resources are released when leaving the screen.

Tests / Validation:

- Manual real-device CameraX test.
- Instrumented permission flow test where feasible.
- Verify lifecycle behavior by navigating away and back.

### Phase 13 - Manual CameraX Capture to App Files

Objective:

- Capture a full-resolution raw image reliably before adding processing complexity.

Key Tasks:

- Use CameraX `ImageCapture`.
- Capture into a file path created by the file repository.
- Save capture metadata needed for review.
- Navigate to review using `sessionId` and persisted temporary review state, not raw file paths in route strings.
- Add progress and failure states around capture.

Definition of Done:

- Manual shutter captures an image.
- Captured image exists in app-specific raw storage.
- User reaches the review flow after capture.

Tests / Validation:

- Manual capture test on a real device.
- Verify capture failure path by testing unavailable camera or storage failure if practical.

### Phase 14 - Bitmap Loading, EXIF Orientation, Downsampling, and Thumbnails

Objective:

- Make image loading memory-safe before processing and UI preview expand.

Key Tasks:

- Implement bitmap decoding with bounds checks and downsampling.
- Apply EXIF orientation correction for imported and captured images.
- Generate thumbnails for editor and library use.
- Use Coil for UI image loading, preferring thumbnails in lists.
- Avoid retaining full-size bitmaps in Compose state.

Definition of Done:

- Large images do not crash preview screens.
- Thumbnails are generated and used for list/grid UI.
- Image orientation is correct for common device captures.

Tests / Validation:

- Unit tests for sample-size calculation.
- Manual test with portrait and landscape photos.
- Memory check on a low-memory emulator or device.

### Phase 15 - OpenCV Initialization and Document Boundary Detection

Objective:

- Detect document edges from captured or imported images.

Key Tasks:

- Initialize OpenCV behind `OpenCvInitializer`.
- Implement `DocumentDetector`.
- Normalize input to a working resolution for detection.
- Run grayscale, blur, edge detection, contour detection, quadrilateral selection, and corner ordering.
- Return `PageCorners?` and a confidence or quality hint if available.

Definition of Done:

- Documents under decent lighting usually produce four ordered corners.
- Detection failure returns a recoverable result.
- OpenCV details are isolated from UI code.

Tests / Validation:

- Image-processing tests using a small fixed dataset.
- Manual tests with skewed, bright, dark, and shadowed pages.

### Phase 16 - Perspective Correction and Corner Ordering

Objective:

- Convert detected or manually chosen corners into a flat document image.

Key Tasks:

- Implement `CornerOrderingUtil`.
- Implement `PerspectiveTransformer`.
- Compute target page dimensions from corner distances.
- Warp using OpenCV and write the output file.
- Preserve enough metadata to allow later crop edits.

Definition of Done:

- Skewed captures produce rectangular flattened output.
- Corner ordering is stable regardless of source point order.
- Failed warp returns a clear processing error.

Tests / Validation:

- Unit tests for corner ordering.
- Golden-image or manual comparison tests for perspective outputs.
- Validate output dimensions are reasonable.

### Phase 17 - Manual Crop Adjustment Fallback

Objective:

- Make the scanner usable when automatic detection is wrong or unavailable.

Key Tasks:

- Add a four-corner draggable crop overlay in the review screen.
- Map UI coordinates back to image coordinates accurately.
- Let users reset to detected corners or full image bounds.
- Re-run perspective correction after manual crop confirmation.

Definition of Done:

- User can manually fix bad detection.
- Crop overlay handles rotation and aspect ratio consistently.
- Manual crop works for imported and captured images.

Tests / Validation:

- Compose UI tests for corner movement where practical.
- Manual tests on images with low contrast, shadows, and partial backgrounds.

### Phase 18 - Enhancement Modes

Objective:

- Improve readability without damaging diagrams or color content.

Key Tasks:

- Implement `ImageEnhancer`.
- Add Document mode for text-heavy pages: grayscale, thresholding, denoise, sharpening.
- Add Mixed mode for diagrams and text: moderate contrast, mild denoise, line preservation.
- Add Color mode for color pages: preserve color, mild cleanup, optional white balance.
- Add reprocess support when the selected mode changes.

Definition of Done:

- Output quality changes visibly by mode.
- Document mode improves text readability.
- Mixed and Color modes avoid destroying diagrams and color information.

Tests / Validation:

- Manual QA dataset comparison across all three modes.
- Unit tests for mode routing and output path handling.

### Phase 19 - Page Review Flow

Objective:

- Let users accept, reject, rotate, compare, and rescan each page before it enters a document.

Key Tasks:

- Build `ReviewScreen`, `ReviewViewModel`, state, events, and effects.
- Show original and processed previews.
- Add rotate, rescan, accept, mode change, and manual crop actions.
- Save accepted pages to the current session through use cases.
- Keep heavy processing outside the ViewModel.

Definition of Done:

- Every captured or imported page passes through review.
- User can accept a processed page into the session.
- User can rescan or re-import instead of accepting.

Tests / Validation:

- ViewModel unit tests for review state transitions.
- Manual end-to-end single-page capture/import to accepted page.

### Phase 20 - Multi-Page Session Editor

Objective:

- Support real documents with multiple ordered pages.

Key Tasks:

- Build `EditorScreen`, `EditorViewModel`, state, events, and effects.
- Display thumbnails for all accepted pages.
- Add delete, rotate, add page, and reorder behavior.
- Start with move up/down if drag-and-drop would slow delivery.
- Persist ordering changes through the repository.

Definition of Done:

- A session can contain multiple ordered pages.
- Page order persists after process death or app restart.
- Deleting a page removes DB rows and related files.

Tests / Validation:

- Unit tests for reorder use case.
- Room tests for page order persistence.
- Manual multi-page scan test.

### Phase 21 - Metadata Flow

Objective:

- Attach structured document information before export.

Key Tasks:

- Build `MetadataScreen`, `MetadataViewModel`, and metadata form components.
- Required fields: grade, subject, year, paper type.
- Optional fields: paper number, source, notes, tags if needed later.
- Generate filename previews using normalized naming rules.
- Validate year using a sensible range such as `1980..currentYear + 1`.

Definition of Done:

- Metadata persists to the scan session.
- Export is blocked until required fields are valid.
- Generated filenames are predictable and safe.

Tests / Validation:

- Unit tests for metadata validation.
- Unit tests for filename generation.
- Compose UI test for validation errors.

### Phase 22 - PDF Generation Service

Objective:

- Generate readable PDFs from ordered processed page images.

Key Tasks:

- Implement `PdfGenerator` using Android `PdfDocument`.
- Fit each image to one PDF page while preserving aspect ratio.
- Run PDF generation off the main thread.
- Close documents, streams, and bitmaps safely.
- Keep page rendering and compression policy configurable behind the interface.

Definition of Done:

- Ordered pages generate a PDF under app-specific document storage.
- PDF opens in a standard viewer.
- Page order matches the editor.

Tests / Validation:

- Integration test for generating a small PDF from fixture images.
- Manual test opening exported PDF on device.
- Verify failure handling for missing page image files.

### Phase 23 - Export, Open, and Share Flow

Objective:

- Complete the local document creation workflow.

Key Tasks:

- Build `ExportScreen`, `ExportViewModel`, state, events, and effects.
- Validate session readiness: pages exist, metadata valid, processed files exist.
- Generate PDF and persist `SavedDocument`.
- Mark scan session as exported.
- Add open/share action using safe content URIs and `FileProvider` where needed.
- Use SAF or MediaStore only when the user explicitly chooses an external export destination.

Definition of Done:

- User can export a multi-page document.
- Saved document record is created with metadata and thumbnail.
- User can open or share the exported PDF.

Tests / Validation:

- ViewModel unit tests for export readiness and errors.
- Integration test for PDF plus DB save.
- Manual share/open test on device.

### Phase 24 - Local Library

Objective:

- Provide a persistent archive of exported documents.

Key Tasks:

- Build `LibraryScreen`, `LibraryViewModel`, state, events, and effects.
- Observe saved documents from Room using Flow.
- Show thumbnail, title, metadata, page count, and created date.
- Add search/filter by subject, year, grade, and paper type.
- Add delete and share/open actions.

Definition of Done:

- Exported documents appear in the library.
- Search/filter is responsive for MVP dataset sizes.
- Delete removes DB record and owned files.

Tests / Validation:

- ViewModel unit tests for filtering.
- Compose UI tests for empty, populated, and search states.
- Manual export-to-library workflow test.

### Phase 25 - Session Recovery and Crash-Safe Cleanup

Objective:

- Prevent data loss and uncontrolled file growth.

Key Tasks:

- Restore latest in-progress session on app start or scanner entry.
- Add abandoned session handling.
- Clean orphaned temp files not referenced by DB.
- Check storage before capture, processing, and export.
- Make delete operations idempotent.

Definition of Done:

- In-progress sessions can be recovered after process death.
- Orphan files are cleaned safely.
- Low-storage states produce user-readable errors.

Tests / Validation:

- Repository tests for recovery queries.
- Manual process-kill test during scan and review.
- Manual low-storage simulation where practical.

### Phase 26 - Scan Quality Scoring

Objective:

- Give users useful guidance before and after capture.

Key Tasks:

- Implement blur score, brightness score, overexposure score, and document area score.
- Add preview hints: move closer, improve lighting, hold steady, document not detected.
- Add post-capture warnings with continue/rescan choices.
- Keep quality scoring advisory for MVP unless blocking is clearly needed.

Definition of Done:

- Common bad scans are flagged.
- Warnings do not prevent deliberate user override unless the file is unusable.
- Quality logic is testable outside UI.

Tests / Validation:

- Unit tests for scoring thresholds using fixture images or synthetic data.
- Manual tests across bright, dark, blurry, and skewed pages.

### Phase 27 - Performance Hardening

Objective:

- Keep scanning stable on low-to-mid range devices.

Key Tasks:

- Profile memory during capture, review, editor, and export.
- Downsample detection input and use full resolution only when needed.
- Release OpenCV `Mat` and bitmap resources promptly.
- Use thumbnails in all lists.
- Avoid repeated full-image decodes during recomposition.
- Add library pagination or lazy loading if needed.

Definition of Done:

- Common workflows do not trigger out-of-memory crashes.
- Image processing does not block the UI thread.
- Editor and library remain smooth with realistic document sizes.

Tests / Validation:

- Manual profiling on a real low-to-mid range Android device.
- Stress test with a 20-page document.
- Verify no full-resolution bitmap list rendering.

### Phase 28 - Accessibility, Adaptive Layouts, and UI Polish

Objective:

- Make the app comfortable and reliable across devices and user needs.

Key Tasks:

- Add content descriptions for controls and thumbnails.
- Support large font sizes without clipped controls.
- Add responsive layouts for small phones and larger screens.
- Add loading, empty, error, and confirmation states for every feature.
- Ensure scanning controls are reachable and visually clear.

Definition of Done:

- UI remains usable with large text.
- TalkBack labels identify key actions.
- Empty/error/loading states are present across main screens.

Tests / Validation:

- Manual accessibility pass with TalkBack.
- Compose UI checks for key content descriptions.
- Manual layout checks on small and large screen emulator profiles.

### Phase 29 - Release Hardening

Objective:

- Prepare the local MVP for reliable internal distribution.

Key Tasks:

- Review manifest permissions and remove unused entries.
- Confirm backup rules do not unintentionally back up large scan files if that is not desired.
- Configure release minification and ProGuard/R8 rules for CameraX, Room, Hilt, OpenCV, and Coil as needed.
- Add app versioning discipline.
- Add privacy notes for local-only storage and optional sharing.
- Create a manual release checklist.

Definition of Done:

- Release build compiles.
- Required permissions are minimal and justified.
- Internal testers can install and use the app.

Tests / Validation:

- Run `assembleRelease`.
- Smoke test release build on a real device.
- Verify exported PDFs remain accessible through intended open/share paths.

### Phase 30 - Post-MVP Roadmap

Objective:

- Extend Docly after the local MVP is stable.

Key Tasks:

- Add OCR as a separate pipeline after PDF/page export, not inside the core scan path.
- Add text indexing for local document search.
- Add backend upload behind repository interfaces.
- Add WorkManager sync queue with network and retry constraints.
- Add auto-capture after manual capture and review tools are reliable.
- Investigate anti-glare or multi-frame fusion after core quality is proven.
- Add diagnostics for processing failures, sync failures, and device compatibility.
- Add optional analytics only after privacy requirements are explicit.

Definition of Done:

- Post-MVP features do not break the local offline workflow.
- Sync and OCR can fail independently without losing local documents.
- WorkManager jobs are observable and retry safely.

Tests / Validation:

- OCR accuracy tests against a fixed document dataset.
- Sync integration tests with fake backend responses.
- WorkManager tests for retry and network constraints.
- Regression tests confirming local scan/export still works offline.

## 6. Quality Gates

Every phase must satisfy these baseline gates:

- App builds successfully.
- App launches without crashing.
- New behavior has a clear happy path and failure path.
- Heavy work is off the main thread.
- User-facing errors are readable.
- New code follows the canonical `com.docly.app` package layout.
- No deprecated API patterns are introduced.

MVP acceptance checklist:

- User can scan pages with the camera.
- User can import existing device photos.
- User can review original versus processed pages.
- User can manually fix crop boundaries.
- User can rotate, delete, reorder, and add pages.
- User can enter required metadata.
- User can export a readable, ordered PDF.
- User can open/share the PDF.
- User can find the document later in the local library.
- App works offline for the complete MVP flow.

Document-specific acceptance checks:

- This file exists at `docs/Implementation Roadmap.md`.
- It contains 30 implementation phases.
- It names `com.docly.app` as the canonical namespace.
- It separates MVP work from post-MVP work.
- It explicitly avoids deprecated APIs and fragile patterns.
- It includes tests or validation for every phase.
- It aligns with `Technical Architecture.md`, `Low-Level Design (LLD).md`, and `Build Roadmap.md`.

## 7. Testing Strategy

Unit tests:

- Metadata validation.
- Filename generation.
- Page ordering.
- Entity/domain mappers.
- Quality scoring thresholds.
- Use case orchestration with fake repositories.
- Corner ordering and coordinate mapping.

Room and data integration tests:

- Create, read, update, delete scan sessions.
- Add, reorder, rotate, and delete pages.
- Save and observe exported documents.
- Verify cascade delete behavior.
- Verify migrations when schema version increases.

File and PDF integration tests:

- Create raw, processed, thumbnail, and PDF paths.
- Copy imported URI content into app storage.
- Generate thumbnails.
- Generate a PDF from fixture images.
- Delete files safely when records are removed.

Compose UI tests:

- Navigation between scanner, review, editor, metadata, export, and library.
- Metadata validation and filename preview.
- Editor reorder/delete/rotate interactions.
- Library empty, populated, search, and filter states.
- Error and loading states.

Manual QA dataset:

- Bright page.
- Dark room page.
- Skewed page.
- Page with shadows.
- Page with diagrams.
- Page with graphs.
- Page with tables.
- Page with faint print.
- Color page.
- Multi-page document with at least 20 pages.

Real-device validation:

- CameraX preview and capture must be validated on at least one physical device before scanner phases are complete.
- Memory and performance must be checked on a low-to-mid range device before MVP release.
- Exported PDFs must be opened with an external viewer and shared through the Android share sheet.

## 8. Post-MVP Expansion

OCR:

- Add local OCR or cloud OCR behind an `OcrRepository`.
- Store OCR results separately from scan and PDF records.
- Treat OCR text as searchable support data, not the authoritative document.
- Keep OCR retryable so failure does not block PDF export.

Backend upload and sync:

- Add upload status to saved documents only after backend requirements are defined.
- Use a queue model: local-only, pending upload, uploaded, failed.
- Use WorkManager for retryable background uploads with network constraints.
- Keep local documents available even when sync fails.

Advanced capture:

- Add auto-capture after manual capture is reliable.
- Use CameraX `ImageAnalysis` for live contour, blur, brightness, and stability checks.
- Require stable corners for multiple frames before auto-capture.
- Keep manual shutter available as the fallback.

Advanced processing:

- Investigate glare reduction, shadow removal, and multi-frame fusion only after the MVP pipeline is stable.
- Add advanced PDF options such as metadata embedding, compression tuning, and OCR text layers if required.

Operations:

- Add crash and processing diagnostics only after privacy requirements are clear.
- Add structured logs for failed processing, export, and sync operations.
- Add internal QA scripts and release checklists for repeatable testing.

## 9. Assumptions

- `com.docly.app` is the canonical namespace.
- The first shippable MVP is local and offline-first.
- The app remains a single `:app` module through MVP.
- Package boundaries should still support future module extraction.
- OCR, backend sync, auto-capture, anti-glare processing, and analytics are post-MVP features.
- Camera capture should be manual-first, with strong review and crop correction tools.
- Existing device photos should enter through Android Photo Picker, not broad storage permissions.
- PDF generation starts with Android `PdfDocument`; another PDF library is only considered if MVP requirements exceed `PdfDocument`.
- The user owns all scan files locally unless they explicitly share or export them.
