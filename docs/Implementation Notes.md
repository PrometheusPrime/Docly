# Implementation Notes

Current roadmap note:

- Entries dated before 2026-05-11 are historical implementation records from the earlier scanner-focused plan.
- The active product direction is the 2026-05-11 documentation reset: Docly is a local-first document utility for scanning, reading, creating, editing, converting, managing, and sharing documents.
- Use `Implementation Roadmap.md`, `Technical Architecture.md`, `Low-Level Design (LLD).md`, and `Build Roadmap.md` as the current planning source of truth.

## 2026-04-18 - Phase 01 Baseline Audit

Scope:

- Documentation-only baseline audit for Phase 01 of `Implementation Roadmap.md`.
- No app code, Gradle configuration, dependencies, manifest entries, Kotlin packages, or package skeletons were changed.
- Because this workspace is not inside a Git repository, this Markdown note is the internal implementation note for Phase 01.

Verified project baseline:

- Project shape: one Android application module, `:app`, declared from `settings.gradle.kts`.
- Gradle wrapper: `9.3.1`, from `gradle/wrapper/gradle-wrapper.properties`.
- Version catalog: AGP `9.1.1`, Kotlin Compose plugin `2.2.10`, Compose BOM `2024.09.00`.
- Android config: namespace `com.docly.app`, application ID `com.docly.app`, compile SDK `36.1`, target SDK `36`, min SDK `28`.
- Current UI shell: `MainActivity` still renders the default Compose `Hello Android` screen.
- Current package root: source and tests use `com.docly.app`.
- Current dependencies are still the generated Compose baseline dependencies only; Hilt, Room, Navigation, CameraX, Coil, WorkManager, and OpenCV remain for later phases.

Documentation alignment decisions:

- `com.docly.app` is the canonical namespace for all implementation work.
- `Implementation Roadmap.md` controls phase sequencing.
- `Low-Level Design (LLD).md` controls the MVP package layout.
- `Technical Architecture.md` remains high-level and includes older package-shape examples; use it for architectural intent, not exact MVP package placement.
- `Build Roadmap.md` is an older/high-level roadmap. Its broad "Phase 0" maps across several newer phases in `Implementation Roadmap.md`.
- Repo search found no active legacy package namespace references in source, Gradle files, or current docs. If a legacy package name appears later, treat it as stale documentation unless source/config proves otherwise.

Validation:

- `2026-04-18 04:13:26 UTC`: `./gradlew :app:assembleDebug :app:testDebugUnitTest` completed with `BUILD SUCCESSFUL in 31s`; 41 actionable tasks were up to date.
- `adb devices` returned an empty device list.
- `./gradlew :app:connectedDebugAndroidTest` was not run because no device or emulator was connected.
- App launch verification was unavailable in this environment for the same reason.

## 2026-04-18 - Phase 02 Gradle Catalog, Plugins, and Dependency Setup

Scope:

- Gradle-only foundation pass for Phase 02 of `Implementation Roadmap.md`.
- Refreshed the version catalog around grouped aliases for build plugins, AndroidX core/lifecycle/activity, Compose, Navigation, Hilt, Room, CameraX, Coil, WorkManager, KotlinX, OpenCV, and test dependencies.
- Applied KSP, Hilt, Room, and Kotlin Serialization plugins in the app module.
- No app source files, manifest entries, package structure, UI behavior, or runtime feature code were changed.

Dependency and plugin decisions:

- Kept AGP `9.1.1`, Kotlin `2.2.10`, compile SDK `36.1`, target SDK `36`, and min SDK `28`.
- Updated Compose BOM from `2024.09.00` to `2026.03.01`.
- Added KSP `2.2.10-2.0.2`, Hilt `2.59.2`, Room `2.8.4`, Navigation Compose `2.9.7`, CameraX `1.6.0`, Coil Compose `3.4.0`, WorkManager KTX `2.11.2`, KotlinX Serialization JSON `1.11.0`, and coroutines `1.10.2`.
- Added Room schema export with `room { schemaDirectory("$projectDir/schemas") }`.
- Added Hilt aggregation with `hilt { enableAggregatingTask = true }`.
- OpenCV integration is `org.opencv:opencv:4.13.0` from Maven Central. The project is not importing the OpenCV Android SDK as a local Gradle module.
- Added `android.disallowKotlinSourceSets=false` because KSP generated-source registration triggers AGP 9 built-in Kotlin's source-set guard otherwise.
- Added `org.gradle.workers.max=2` because the refreshed dependency set, especially dexing/transforms for OpenCV and AndroidX native artifacts, over-parallelized on this low-memory workstation.

Reference sources used:

- AndroidX versions: <https://developer.android.com/jetpack/androidx/versions>
- Hilt Gradle setup: <https://dagger.dev/hilt/gradle-setup>
- OpenCV Android Maven guidance: <https://docs.opencv.org/4.x/d5/df8/tutorial_dev_with_OCV_on_Android.html>
- Maven metadata for OpenCV, Coil, KSP, coroutines, and Compose BOM.

Validation:

- By `2026-04-18 05:42:47 UTC`, `./gradlew assembleDebug` had completed with `BUILD SUCCESSFUL in 19m 13s`; 38 actionable tasks, 26 executed and 12 up to date.
- `assembleDebug` emitted native strip warnings for packaged native libraries, including `libopencv_java4.so`; Gradle packaged them as-is and the build succeeded.
- By `2026-04-18 05:42:47 UTC`, `./gradlew testDebugUnitTest` had completed with `BUILD SUCCESSFUL in 7m 10s`; 30 actionable tasks, 9 executed and 21 up to date.
- A first `assembleDebug` attempt hit a transient Maven Central TLS handshake failure while downloading `com.squareup:kotlinpoet-jvm:2.0.0`; a direct artifact check succeeded and the Gradle retry resolved the dependency.
- `adb devices` returned an empty device list.
- `./gradlew connectedDebugAndroidTest` was not run because no device or emulator was connected.

## 2026-04-18 - Phase 03 Static Quality Setup

Scope:

- Added ktlint as the initial Kotlin style gate for Phase 03.
- Kept the existing AGP 9 built-in Kotlin setup; `org.jetbrains.kotlin.android` was not added.
- Added a root `qualityCheck` task for CI-friendly local verification.
- Cleaned the generated starter Kotlin files so the initial ktlint gate passes without a baseline.

Quality configuration:

- Added `org.jlleitschuh.gradle.ktlint` `14.2.0` through the version catalog.
- Pinned the ktlint engine to `1.8.0` in the app plugin configuration.
- Configured ktlint for Android style, console output, non-ignored failures, and reports under `app/build/reports/ktlint`.
- Wired `:app:check` to depend on `:app:ktlintCheck`.
- Added root `.editorconfig` with UTF-8, LF endings, final newlines, trimmed trailing whitespace, 4-space Kotlin/Kotlin DSL indentation, `max_line_length = 120`, and `ktlint_code_style = android_studio`.
- Added `ktlint_function_naming_ignore_when_annotated_with = Composable` so Compose UI entry points can keep standard PascalCase names while regular Kotlin functions remain checked.
- Added `/.kotlin/` to the root `.gitignore`; ktlint, lint, and unit-test reports remain under ignored `build/` directories.

Developer commands:

- Full local gate: `./gradlew qualityCheck`.
- Kotlin style gate only: `./gradlew :app:ktlintCheck`.
- Kotlin formatter: `./gradlew :app:ktlintFormat`.

Naming and source layout conventions:

- Packages stay all lowercase under `com.docly.app`, following the roadmap layers: `app`, `core`, `data`, `domain`, `feature`, and `ui`.
- Primary Kotlin files use PascalCase names matching their main declaration.
- Feature and architecture suffixes should stay consistent: `*Screen`, `*ViewModel`, `*UiState`, `*UiEvent`, `*UiEffect`, `*UseCase`, `*Repository`, `*Dao`, `*Entity`, and `*Test`.
- Unit tests live in `app/src/test/java`.
- Instrumentation and Compose UI tests live in `app/src/androidTest/java`.

Validation:

- `./gradlew :app:tasks --all --console=plain` completed with `BUILD SUCCESSFUL` and confirmed ktlint source-set tasks for app `main`, `test`, and `androidTest` sources, so the JavaExec fallback was not needed.
- `./gradlew :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 51s`; 9 actionable tasks, 5 executed and 4 up to date.
- `2026-04-18 11:24:36 UTC`: `./gradlew qualityCheck --console=plain` completed with `BUILD SUCCESSFUL in 2h 13s`; 96 actionable tasks, 87 executed and 9 up to date.
- `qualityCheck` assembled the debug app and debug Android test APK, ran ktlint, `testDebugUnitTest`, Android lint, and `:app:check`.
- During `qualityCheck`, Kotlin daemon connection failed once and Gradle automatically used in-process fallback compilation; the build still completed successfully.
- Android packaging repeated the known native strip warning for `libandroidx.graphics.path.so`, `libc++_shared.so`, `libimage_processing_util_jni.so`, `libopencv_java4.so`, and `libsurface_util_jni.so`; Gradle packaged them as-is and the build succeeded.
- Generated reports were confirmed under ignored paths: `app/build/reports/ktlint`, `app/build/reports/lint-results-debug.*`, and `app/build/reports/tests/testDebugUnitTest`.
- `connectedDebugAndroidTest` was not run because Phase 03 only requires Android test compilation and no device or emulator was connected.

## 2026-04-18 - Phase 04 Package Architecture Skeleton

Scope:

- Created the single-module MVP package skeleton under `com.docly.app` for `app`, `core`, `data`, `domain`, `feature`, and `ui`.
- Moved `MainActivity` into `com.docly.app.app` so the activity location matches the LLD package layout.
- Updated the launcher activity manifest entry from `.MainActivity` to `.app.MainActivity`.
- Added non-compiled `.gitkeep` markers for empty leaf packages instead of Kotlin placeholder declarations.
- Left the default Compose `Hello Android` app behavior unchanged.

Package skeleton:

- `app.di`, `app.navigation`
- `core.camera`, `core.common`, `core.dispatchers`, `core.file`, `core.image`, `core.logging`, `core.pdf`, `core.result`, `core.time`
- `data.local.db`, `data.local.dao`, `data.local.entity`, `data.local.mapper`, `data.repository`, `data.storage`
- `domain.model`, `domain.repository`, `domain.usecase`
- `feature.scanner`, `feature.review`, `feature.editor`, `feature.metadata`, `feature.export`, `feature.library`
- `ui.components`, `ui.theme`, `ui.util`

Validation:

- `2026-04-18 11:39:08 UTC`: `./gradlew :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 6m 36s`; 38 actionable tasks, 12 executed and 26 up to date.
- `2026-04-18 11:39:08 UTC`: `./gradlew :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 12s`; 9 actionable tasks, 2 executed and 7 up to date.

## 2026-04-18 - Phase 05 Compose App Shell, Theme, and Navigation Host

Scope:

- Replaced the generated `Greeting` activity content with a `DoclyApp` root composable.
- Added `AppNavHost` with typed Navigation Compose routes for Scanner, Review, Editor, Metadata, Export, and Library.
- Kept Scanner as the start destination.
- Kept all Phase 05 screens stateless and callback-driven; no Hilt ViewModels, repositories, CameraX, Room, PDF generation, or file I/O were introduced.
- Added shared loading, error, empty, scaffold, and top bar Compose components.
- Added stable Compose UI test tags and a Phase 05 navigation instrumentation test.
- Replaced the generated purple Compose palette with Docly paper, ink, teal/green scan accents, slate secondary tones, and light/dark schemes. Dynamic color now defaults to disabled for consistent branding.

Navigation decisions:

- Routes are Kotlin serialization types under `com.docly.app.app.navigation`.
- Navigation arguments remain ID-only. The placeholder flow uses `PLACEHOLDER_SESSION_ID = "placeholder-session"`.
- Placeholder flow: Scanner -> Review -> Editor -> Metadata -> Export -> Library.
- Library can navigate back to Scanner, and Scanner can open Library from the top bar.

Dependency change:

- Added `androidx.compose.material:material-icons-core` through the version catalog for top bar icons.

Validation:

- `2026-04-18 12:07:20 UTC`: `./gradlew :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 1m 53s` after replacing the deprecated back icon with `Icons.AutoMirrored.Filled.ArrowBack`.
- A combined validation command was interrupted after the Gradle client stalled during dexing; the stale Gradle/Kotlin daemons were stopped and the same checks were rerun as isolated `--no-daemon` commands.
- `./gradlew --no-daemon :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 59s` to apply the configured Android Studio ktlint wrapping style.
- `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 18s`; 9 actionable tasks, 3 executed and 6 up to date.
- `./gradlew --no-daemon :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 2m 58s`; 7 actionable tasks, 2 executed and 5 up to date.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 18s`; 30 actionable tasks, 9 executed and 21 up to date.
- `./gradlew --no-daemon :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 5m 43s`; 38 actionable tasks, 12 executed and 26 up to date.
- `assembleDebug` repeated the known native strip warning for `libandroidx.graphics.path.so`, `libc++_shared.so`, `libimage_processing_util_jni.so`, `libopencv_java4.so`, and `libsurface_util_jni.so`; Gradle packaged them as-is and the build succeeded.
- `./gradlew --no-daemon :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 14s`; 54 actionable tasks, 25 executed and 29 up to date.
- `adb devices` returned an empty device list at `2026-04-18 13:40:17 UTC`.
- `./gradlew :app:connectedDebugAndroidTest` was not run because no device or emulator was connected.

## 2026-04-18 - Phase 06 Hilt Dependency Injection Foundation

Scope:

- Added the real Hilt application boundary with `DoclyApplication`, manifest `android:name`, and `@AndroidEntryPoint` on `MainActivity`.
- Added Hilt modules for core providers, Room database/DAOs, repository bindings, image-processing service placeholders, and PDF service placeholders.
- Advanced the planned core/data/domain contracts needed for meaningful DI bindings: `AppResult`, error categories, dispatcher/ID/time/logging/file seams, domain models, repository interfaces, use case shells, Room entities, DAOs, mappers, and repository implementations.
- Added `@HiltViewModel` placeholder ViewModels plus UI state/event/effect contracts for Scanner, Review, Editor, Metadata, Export, and Library.
- Kept the Phase 05 screens stateless and callback-driven. `AppNavHost` instantiates the Hilt ViewModels, but no screen starts real camera, OpenCV, thumbnail, or PDF work yet.
- Added a custom Hilt instrumentation runner backed by `HiltTestApplication`.

Persistence and DI decisions:

- Room database name is `docly.db`; schema version starts at `1`.
- Room schema JSON was generated under `app/schemas/com.docly.app.data.local.db.AppDatabase/1.json`.
- Room stores metadata and file paths only, not bitmap or PDF binary data.
- `ScanRepositoryImpl` creates sessions using `IdProvider` and `TimeProvider`, reads pages ordered by `pageIndex`, updates metadata/status, and reorders pages transactionally.
- `DocumentRepositoryImpl` owns saved document rows only; file cleanup remains a later workflow concern.
- `FileRepositoryImpl` creates app-specific paths under `files/scans/raw`, `files/scans/processed`, `files/scans/thumbnails`, and `files/documents/pdf`.
- Image processing, thumbnail generation, perspective correction, camera capture, and PDF generation return categorized not-implemented errors until their dedicated roadmap phases.

Dependency change:

- Added explicit AndroidX test `core` and `runner` aliases for Hilt/Room instrumentation test compilation.

Tests:

- Added JVM tests for `AppResult` helpers, metadata validation, filename generation, and entity/domain mappers.
- Added Android test compilation coverage for Hilt core-provider overrides and Room-backed scan repository page ordering/reordering.
- Updated the Phase 05 navigation instrumentation test to run under the Hilt test runner.

Validation:

- `./gradlew --no-daemon :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 3m 38s`.
- `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 21s`; 9 actionable tasks, 2 executed and 7 up to date.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 1m 47s`; 32 actionable tasks, 4 executed and 28 up to date.
- `./gradlew --no-daemon :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 16m 44s`; 40 actionable tasks, 21 executed and 19 up to date.
- `assembleDebug` repeated the known native strip warning for `libandroidx.graphics.path.so`, `libc++_shared.so`, `libimage_processing_util_jni.so`, `libopencv_java4.so`, and `libsurface_util_jni.so`; Gradle packaged them as-is and the build succeeded.
- `./gradlew --no-daemon :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 2m 36s`; 59 actionable tasks, 8 executed and 51 up to date.
- `adb devices` returned an empty device list.
- `./gradlew :app:connectedDebugAndroidTest` was not run because no device or emulator was connected.

## 2026-04-18 - Phase 08 Domain Contracts Completion

Scope:

- Reconciled the Phase 08 domain contract requirements against the current source and LLD after the domain models, repository interfaces, and use case shells were introduced early during Phase 06.
- Preserved the existing public domain API shape: plain Kotlin domain models, `AppResult` repository contracts, `Flow` saved-document observation, and current session/page/export/library use case grouping.
- Kept `GenerateDocumentNameUseCase` and `ValidateMetadataUseCase` under `domain.usecase.export` because that matches the current LLD inventory and avoids a test-only package churn.
- Did not add real camera capture, OpenCV processing, file cleanup lifecycle, Room migration, or PDF rendering behavior; those remain owned by later roadmap phases.

Tests:

- Added focused JVM coverage for `RotatePageUseCase` rotation wrapping, `GeneratePdfUseCase` empty-page validation, processed-image path preference during PDF generation, and repository-pass-through behavior for session, page, export, and library use cases.
- Confirmed the production domain package still has no Android framework dependencies.

Validation:

- `2026-04-18 16:29:59 UTC`: `./gradlew --no-daemon :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 2m 37s`; 7 actionable tasks up to date.
- `2026-04-18 16:37:19 UTC`: `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 6m 15s`; 32 actionable tasks, 4 executed and 28 up to date.
- `2026-04-18 16:29:59 UTC`: `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 2m 44s`; 9 actionable tasks, 2 executed and 7 up to date.
- `2026-04-18 16:29:59 UTC`: `rg -n "androidx?|android\\.|Context|Bitmap|Uri" app/src/main/java/com/docly/app/domain || true` returned no matches.

## 2026-04-18 - Phase 09 Room Persistence Completion

Scope:

- Completed the Room persistence phase against the existing Phase 06 data layer instead of replacing the schema.
- Kept `AppDatabase` at schema version `1` with exported schemas enabled.
- Added `RoomMigrations.ALL` as the centralized migration policy; it is intentionally empty for version `1`.
- Wired the Hilt database provider with `.addMigrations(*RoomMigrations.ALL)` and did not enable destructive migration fallback.
- Added explicit saved-document DAO update support while preserving `DocumentRepository.saveDocument()` as the public upsert path.
- Confirmed Room continues to store scalar metadata and file paths only, not bitmap or PDF binary data.

Tests:

- Expanded mapper JVM coverage for complete session metadata, null session metadata, ordered supplied pages, full/partial page corners, and saved-document nullable metadata fields.
- Expanded Room instrumentation coverage for scan-session DAO CRUD, scanned-page ordering/update/delete/delete-by-session/cascade behavior, and saved-document DAO observe/read/update/delete behavior.
- Expanded repository-backed instrumentation coverage for scan metadata/status/page update/delete flows and saved-document save/observe/get/update/delete flows.
- Added a `MigrationTestHelper` schema validation test that opens the exported version `1` schema and validates it with the current migration policy.

Validation:

- `./gradlew --no-daemon :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 2m 20s`.
- `./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 6m 39s`.
- `2026-04-18 20:22:23 UTC`: `./gradlew --no-daemon :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 5m 3s`; 77 actionable tasks, 15 executed and 62 up to date.
- `adb devices` returned an empty device list.
- `./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain` was not run because no device or emulator was connected.

## 2026-04-18 - Phase 10 App-Specific File Storage

Scope:

- Hardened the app-specific file storage layer for raw scan images, processed images, thumbnails, and PDFs.
- Expanded `FileRepository` with explicit storage availability checks and asset cleanup helpers for pages, sessions, and saved documents.
- Kept all file paths under the existing app-specific directories: `files/scans/raw`, `files/scans/processed`, `files/scans/thumbnails`, and `files/documents/pdf`.
- Did not add Photo Picker import, CameraX capture, image processing, thumbnail rendering, PDF rendering, storage permissions, or navigation path arguments.

Storage and cleanup decisions:

- Storage reserve constants live at the domain repository boundary: `CAPTURE_BYTES = 25 MB` and `EXPORT_BYTES = 50 MB`.
- `GeneratePdfUseCase` now checks `EXPORT_BYTES` before creating the PDF output path or invoking the PDF repository.
- File path generation keeps readable prefixes (`raw_`, `processed_`, `thumb_`), sanitizes names consistently, and creates the target directory on demand.
- Deleting missing files is treated as success; deleting a directory through the file cleanup API is treated as a storage failure.
- Page/document deletion uses the selected DB-first policy: delete the Room row first, then remove the explicit file paths stored on the deleted page or saved document.
- If post-DB cleanup fails, the repository returns a `STORAGE` error while leaving the database deletion durable.

Tests:

- Added JVM tests for `FileRepositoryImpl` covering directory/path creation, name sanitization, PDF extension handling, idempotent file deletion, page/session/document asset cleanup, and low-storage error handling.
- Expanded `GeneratePdfUseCase` tests for the export storage reserve check and early stop behavior when storage is unavailable.
- Expanded Room-backed instrumentation coverage so page and saved-document deletion verify DB-first cleanup calls and cleanup-failure behavior.

Validation:

- `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 37s`; 9 actionable tasks, 3 executed and 6 up to date.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 6m 58s`; 32 actionable tasks, 12 executed and 20 up to date.
- `./gradlew --no-daemon :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 3m 38s`; 40 actionable tasks, 3 executed and 37 up to date.
- `./gradlew --no-daemon :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 57s`; 59 actionable tasks, 8 executed and 51 up to date.
- `adb devices` reported one attached device: `1268015548000502`.
- Extra validation attempt: `./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain` reached the device-side `:app:connectedDebugAndroidTest` task but produced no progress output for several minutes, so it was terminated at `2026-04-18 21:13:54 UTC` with exit code `143`. The requested compile/build validation tasks above passed.

## 2026-04-19 - Phase 11 Device Photo Import

Scope:

- Added Android Photo Picker entry points on the scanner screen for single-image and multi-image import.
- Added `DevicePhotoRepository`, `ImportedRawImage`, `ImportDevicePhotosResult`, and `ImportDevicePhotosUseCase`.
- Implemented URI import by copying selected image content into app-specific raw scan storage, reading image bounds without full bitmap loading, and persisting imported images as raw `ScannedPage` rows.
- Kept imported images in the same session/page model used by future camera captures, with navigation to review by `sessionId` only.
- Did not add broad storage/media permissions, image processing, thumbnail generation, EXIF correction, or accept/reject review behavior; those remain later phases.

Import decisions:

- Domain APIs keep Android framework types out of the domain layer by accepting picker selections as URI strings.
- `ImportDevicePhotosUseCase` reuses an existing in-progress scanner session when available, otherwise creates one.
- Picker order is preserved by importing URIs sequentially and assigning page indices after the current session's max `pageIndex`.
- Imported pages are persisted immediately with raw image paths, dimensions, `processedImagePath = null`, `thumbnailPath = null`, `corners = null`, and `rotationDegrees = 0`.
- Raw file path creation now supports sanitized custom image extensions while preserving the existing JPEG helper used by future camera capture.
- Import cleanup is best-effort but batch-aware: failed imports delete partial files, and failures after one or more page inserts roll back pages created by the current batch.

Tests:

- Added JVM coverage for `ImportDevicePhotosUseCase`: empty selections, new-session creation, latest-session reuse, import order/page indices, batch rollback, and add-page cleanup.
- Added JVM coverage for the raw URI-copy helper with fake input streams: successful copy, missing stream, read failure cleanup, invalid image bounds cleanup, and MIME-extension fallback.
- Added Scanner ViewModel tests for successful import navigation and import failure UI state.
- Expanded file repository tests for custom raw image extensions.
- Expanded the navigation instrumentation test source to assert the scanner import controls render.

Validation:

- `./gradlew --no-daemon :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 4m 48s`.
- `./gradlew --no-daemon :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 2m 3s`.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` initially failed because a public JUnit rule exposed a private nested test rule type; the test rule visibility was fixed.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 31s`; 32 actionable tasks, 5 executed and 27 up to date.
- `./gradlew --no-daemon :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 2m 29s`; 40 actionable tasks, 4 executed and 36 up to date.
- `./gradlew --no-daemon :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 59s`; 59 actionable tasks, 8 executed and 51 up to date.
- Final `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 43s`; 9 actionable tasks, 2 executed and 7 up to date.
- `rg -n "READ_EXTERNAL|READ_MEDIA|MANAGE_EXTERNAL|WRITE_EXTERNAL" app/src/main/AndroidManifest.xml app/src/main || true` returned no matches.
- `adb devices` reported one attached device: `1268015548000502`.
- Manual Photo Picker validation with real gallery photos was not performed in this terminal session.

## 2026-04-19 - Phase 16 Perspective Correction and Corner Ordering

Scope:

- Replaced the perspective-correction placeholder with an OpenCV-backed `OpenCvPerspectiveTransformer`.
- Hardened `CornerOrderingUtil` so invalid corner counts, duplicate points, and non-finite coordinates are rejected before ordering.
- Wired Hilt image-processing bindings to the real perspective transformer.
- Added JVM tests for corner-order validation and Android instrumentation coverage for generated skewed-document warping.
- Kept process-page orchestration, manual crop UI, enhancement modes, and review actions out of this phase.

Perspective decisions:

- The transformer decodes through `BitmapLoader`, so EXIF orientation and downsampling behavior stay centralized.
- Supplied corners are scaled from original oriented image dimensions into the decoded bitmap coordinate space before warping.
- Target output dimensions are computed from opposing edge distances after corner ordering.
- OpenCV homography and `warpPerspective` run on the default dispatcher; JPEG file writing runs on the IO dispatcher.
- Failed warp attempts delete partial output and return a categorized `PROCESSING` error.

Validation:

- `./gradlew --no-daemon :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 5m 55s`.
- `./gradlew --no-daemon :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 2m 56s`.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 13m 38s`.
- `./gradlew --no-daemon :app:assembleDebug --console=plain` completed with `BUILD SUCCESSFUL in 6m 2s`.
- `./gradlew --no-daemon :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 10m 49s`.
- `adb devices` reported one attached device: `1268015548000502`.
- `./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 5m 29s`; 31 tests passed on `TECNO KL5 - 14`.

## 2026-04-25 - Phase 17 Manual Crop Adjustment Fallback

Scope:

- Added a review-screen crop editor for the latest captured or imported page.
- Added a four-corner draggable crop overlay with `ContentScale.Fit` coordinate mapping between viewport pixels and oriented raw-image coordinates.
- Added reset actions for detected corners and full-image bounds.
- Added crop application through `ApplyPageCropUseCase`, which generates unique processed/thumbnail paths, runs perspective correction, updates the existing page row, and cleans up failed or superseded output files.
- Implemented the Phase 17 `ImageProcessingRepository.processPage` path as perspective warp plus thumbnail generation; enhancement modes remain deferred to Phase 18.
- Wired review UI events/effects through `ReviewViewModel` and `AppNavHost`.

Implementation decisions:

- `ScannedPage.width` and `height` continue to represent oriented raw-image dimensions so later crop edits remain in stable source-image coordinates.
- Crop reset falls back to full-image bounds when automatic detection did not produce corners.
- Repeated crop applications use a suffix based on `page.id` plus a new generated ID to avoid overwriting the currently referenced processed image or thumbnail.
- Existing processed assets are deleted only after the page update succeeds.

Tests:

- Added JVM coverage for crop coordinate mapping, reverse mapping, drag delta mapping, bounds clamping, and full-image fallback corners.
- Added JVM coverage for `ApplyPageCropUseCase`: successful update, unique output paths, processing failure cleanup, update failure cleanup, old asset cleanup, and storage failure.
- Added JVM coverage for `ReviewViewModel`: detected-corner load, full-bounds fallback, corner changes, reset actions, crop success, processing failure, and update failure.
- Added Compose UI coverage for crop overlay rendering, handle rendering, action enabled states, and drag dispatch.

Validation:

- `./gradlew ktlintCheck` completed with `BUILD SUCCESSFUL in 39s`.
- `./gradlew testDebugUnitTest` completed with `BUILD SUCCESSFUL in 3m 40s`.
- `./gradlew assembleDebug` completed with `BUILD SUCCESSFUL in 2m 5s`.
- `./gradlew assembleDebugAndroidTest` completed with `BUILD SUCCESSFUL in 1m 32s`.
- `adb devices` reported no attached devices, so connected/manual device validation was not run in this session.

## 2026-04-25 - Phase 18 Enhancement Modes

Scope:

- Replaced the placeholder `ImageEnhancer` with an OpenCV-backed `OpenCvImageEnhancer`.
- Wired page processing as perspective warp to a deterministic temporary image, enhancement into the final processed image, and thumbnail generation from that enhanced output.
- Added Document, Mixed, and Color enhancement modes:
  - Document mode uses grayscale conversion, median denoise, adaptive thresholding, and light sharpening.
  - Mixed mode uses bilateral denoise, LAB luminance contrast enhancement, and mild sharpening.
  - Color mode uses simple gray-world white balance, mild bilateral denoise, and light sharpening.
- Added a shared segmented scan-mode selector to Scanner and Review.
- Kept Review reprocessing explicit: mode changes update the selected mode, and the existing Apply action persists the new mode through processing.

Implementation decisions:

- Processed images remain JPEGs to match the existing `FileRepository.createProcessedImagePath()` contract.
- `ReviewUiState` now tracks selected and applied scan modes separately so the UI can represent pending mode changes without auto-processing.
- Failed warp, enhancement, or thumbnail generation deletes temporary and newly generated files before returning the processing error.
- The temporary warp path is derived from the final processed path using the `_warp_tmp` suffix.
- No curated manual QA image dataset was available in this terminal session; generated bitmap fixtures were used for automated on-device comparison across modes.

Tests:

- Added JVM coverage for `ImageProcessingRepositoryImpl`: warp/enhance/thumbnail ordering, selected mode routing, and cleanup for warp, enhancer, and thumbnail failures.
- Expanded `ReviewViewModel` JVM coverage for explicit scan-mode selection, apply-time persistence, and failure behavior that preserves the prior processed state.
- Expanded Scanner and Review Compose tests for mode selector rendering, click dispatch, and disabled states while processing/importing.
- Added Android instrumentation coverage for `OpenCvImageEnhancer` using generated bitmap fixtures to verify Document mode produces high-contrast grayscale output and Mixed/Color preserve more chroma than Document.

Validation:

- `./gradlew --no-daemon :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 10m 57s`.
- `./gradlew --no-daemon :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 5m 3s`.
- `./gradlew --no-daemon :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 2m 3s`.
- `./gradlew --no-daemon :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 25s`.
- `./gradlew --no-daemon :app:ktlintCheck :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 6m 34s`.
- `adb devices` reported one attached device: `1268015548000502`.
- `./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 58s`; 40 tests passed on `TECNO KL5 - 14`.
- Final combined validation after cleanup hardening: `./gradlew --no-daemon :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 8m 1s`; 40 connected tests passed on `TECNO KL5 - 14`.

## 2026-04-25 - Phase 19 Page Review Flow

Scope:

- Added explicit `PageReviewStatus` gating so captured/imported pages enter Room as `PENDING` and only accepted pages continue into the document editor.
- Added Room schema version `2`, exported schema JSON, and migration `1 -> 2` with `reviewStatus TEXT NOT NULL DEFAULT 'ACCEPTED'` so pre-Phase-19 pages remain visible.
- Added `AcceptReviewedPageUseCase` to validate processed output and persist reviewed pages as `ACCEPTED`.
- Expanded `ReviewViewModel` to select the first pending page, auto-process unprocessed pending pages, apply crop/mode changes, rotate, compare original/processed previews, accept pages, and discard pending pages for rescan.
- Updated `ReviewScreen` with explicit compare, crop, rotate, rescan, and accept controls.
- Updated `EditorViewModel` to show only accepted pages.

Implementation decisions:

- Reject/rescan deletes only pending pages; accepted fallback pages are left intact and navigate back to scanner.
- Rotation remains metadata-only through `rotationDegrees`; image rewriting remains deferred to export/PDF behavior if needed.
- Auto-processing uses existing page processing use cases and keeps OpenCV/file work out of the ViewModel.
- Review accept is disabled while processing/saving, while processed output is missing, or while crop/mode changes are unapplied.

Tests:

- Added JVM coverage for `AcceptReviewedPageUseCase`, `EditorViewModel` accepted-page filtering, capture/import pending status, and expanded review state transitions.
- Expanded mapper and Room instrumentation coverage for `reviewStatus` round-tripping, migration defaulting, and repository persistence.
- Expanded Review Compose tests for compare/crop/rotate/rescan/accept controls and accept disabled states.

Validation:

- `./gradlew --no-daemon :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 1m 31s`.
- `adb devices` reported one attached device: `1268015548000502`.
- `./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 2m 11s`; 44 tests passed on `TECNO KL5 - 14`.
- An earlier full connected run hit a transient Hilt test-application process-state crash while entering non-Hilt Room tests; `RoomRepositoryTest` passed in isolation, then the full connected suite passed after force-stopping the app/test processes.

## 2026-04-27 - Phase 20 Multi-Page Session Editor

Scope:

- Wired `EditorViewModel` with events and effects for add page, delete, rotate, move up/down, reload, and continue.
- Updated the editor UI to show session actions, per-page move/rotate/delete controls, rotated thumbnails, pending-review blocking, and inline errors without hiding editable pages.
- Changed `ScannerRoute` to carry an optional `sessionId` and initialized `ScannerViewModel` from `SavedStateHandle` so editor add-page navigation appends to the existing session.
- Updated app navigation to collect editor effects and route add-page to scanner and continue to metadata.

Implementation decisions:

- Reorder uses move up/down controls; drag-and-drop remains out of scope for this phase.
- The editor displays only accepted pages, but the ViewModel keeps the full ordered session internally so repository reorder calls include pending hidden pages.
- Delete remains immediate. The editor reloads after delete attempts because the repository can remove the DB row even when asset cleanup returns a storage error.
- Continue is blocked until at least one accepted page exists and all pending pages are reviewed.

Tests:

- Expanded `EditorViewModel` JVM coverage for accepted-page filtering, pending counts, full-session reorder IDs, visible boundary no-ops, rotate, delete reload after cleanup errors, and navigation effects.
- Added Scanner ViewModel coverage for route-provided session IDs.
- Added Editor Compose coverage for action rendering, enabled/disabled boundary states, pending-page blocking, and click dispatch.
- Extended Room repository instrumentation coverage for invalid reorder ID lists.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 49m 59s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 14m 52s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 5m 6s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 16m 52s`.
- `adb devices` reported no attached devices, so connected/manual device validation was not run in this session.
- The shell `JAVA_HOME` initially pointed to missing `/usr/lib/jvm/java-25-openjdk`; validation was run with the installed `/usr/lib/jvm/java-26-openjdk`.

## 2026-04-27 - Phase 21 Metadata Flow

Scope:

- Replaced the placeholder metadata route with a state/event-driven `MetadataScreen` backed by `MetadataViewModel`.
- Added metadata loading, live safe filename preview, required-field validation, non-integer year validation, trimmed persistence, nullable optional fields, save error handling, and navigation-to-export effects after successful persistence.
- Wired `AppNavHost` to collect metadata effects and show toast messages, keeping export navigation behind ViewModel validation and save success.
- Added Material 3 form fields for grade, subject, year, paper type, paper number, source, and notes.
- Kept tags, Room schema changes, domain API changes, and `READY_FOR_EXPORT` status updates out of Phase 21.

Implementation decisions:

- Filename previews are shown only once required text fields are present and the year can be parsed as an integer.
- Validation errors remain inline on the metadata screen; repository save failures also emit a toast.
- Grade, subject, and paper type remain free-form text fields.

Tests:

- Added `MetadataViewModelTest` coverage for existing metadata load, blank session IDs, live preview updates, required-field blocking, non-integer and out-of-range years, trimmed successful saves, nullable optional fields, navigation effects, and save failures.
- Added metadata use-case coverage for future-year rejection and blank optional paper-number filename generation.
- Added `MetadataScreenStateTest` coverage for field rendering and dispatch, filename previews, validation errors, continue dispatch, and loading/saving disabled states.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 2m 56s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 2m 3s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 10m 28s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 8m 11s`.
- `adb devices` reported one attached device: `1268015548000502`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.metadata.MetadataScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 13s`; 5 metadata connected tests passed on `TECNO KL5 - 14`.
- Full `:app:connectedDebugAndroidTest` was attempted twice after the metadata test fix. One run stalled at 5/52 tests and a later bounded run stalled at 18/52 tests; both were stopped and the app/test processes were force-stopped. The targeted Phase 21 connected coverage passed, but the full connected suite was not recorded as passing in this session.

## 2026-04-27 - Phase 22 PDF Generation Service

Scope:

- Replaced the placeholder PDF generator with `AndroidPdfGenerator` backed by Android `PdfDocument`.
- Added `PdfGenerationOptions` with an A4-fit page policy and configurable render quality while keeping the existing repository/use-case call path stable.
- Rendered one source image per PDF page, choosing A4 portrait or landscape from the decoded image orientation and center-fitting the bitmap on a white page while preserving aspect ratio.
- Ran generation work through `DispatcherProvider`, recycled bitmaps after each page, closed `PdfDocument` safely, and removed partial output files on failures.
- Updated Hilt to bind the real PDF generator.

Implementation decisions:

- The default export policy is A4 fit using 595 x 842 PDF points.
- `GeneratePdfUseCase` still chooses processed image paths before original image paths and delegates ordered paths to the PDF repository.
- Missing/unreadable images, blank output paths, and write/render errors return `AppErrorCategory.PDF`.
- Export UI, saved-document persistence, session export status changes, open/share actions, and external destinations remain Phase 23.

Tests:

- Added `AndroidPdfGeneratorTest` instrumentation coverage for generating a non-empty two-page PDF from fixture images, verifying page count with `PdfRenderer`, checking page order by rendered center colors, checking A4 portrait/landscape sizing, and ensuring missing input images return PDF errors without leaving output files.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 2m 14s` after the first `ktlintCheck` reported formatting issues in the new generator.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 2m 46s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 41m 22s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 37m 6s`.
- `adb devices` reported no attached devices, so the targeted `AndroidPdfGeneratorTest` connected test and manual PDF viewer validation were not run in this session.

## 2026-04-27 - Phase 23 Export, Open, and Share Flow

Scope:

- Replaced the placeholder export route with a state/effect-driven `ExportScreen` backed by `ExportViewModel`.
- Added export readiness and orchestration use cases that load the scan session, validate metadata, require reviewed accepted pages, require processed image files to exist, generate the PDF, save a `SavedDocument`, and mark the scan session `EXPORTED`.
- Added rollback for generated PDFs when saved-document persistence or session status update fails; status-update rollback also attempts to delete the saved-document row.
- Added Android `FileProvider` support and `PdfIntentFactory` for safe app-owned PDF open/share content URIs.
- Wired export effects in navigation so open/share use external Android activities and show a toast if no handler is available.

Implementation decisions:

- Export remains app-storage-first under `files/documents/pdf`; no SAF or MediaStore destination picker was added.
- Saved document titles use the generated PDF filename without the `.pdf` suffix.
- The first exported page thumbnail becomes the saved document thumbnail.
- Export blocks pending pages and pages without an existing processed image rather than falling back to raw images.

Tests:

- Added JVM coverage for export readiness failures, PDF failure, save/status rollback, successful `SavedDocument` creation, session export marking, and export ViewModel open/share effects.
- Added Compose UI coverage for loading, not-ready, ready, exporting, and exported export-screen states.
- Added Android integration coverage that creates fixture processed images, generates a PDF, saves the document in Room, and verifies the session is marked exported.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat --console=plain` completed with `BUILD SUCCESSFUL in 3m 17s`.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 39s`.
- First `:app:testDebugUnitTest` attempt failed because `ExportViewModelTest` exposed a private JUnit rule type; the rule visibility was fixed.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 7m 46s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 9m 34s`.
- `adb devices` reported no attached devices, so targeted connected export/open/share tests and manual viewer/share-sheet validation were not run in this session.

## 2026-04-29 - Phase 24 Local Library

Scope:

- Completed the local library as a searchable archive of exported `SavedDocument` records observed from Room.
- Added `LibraryViewModel` state/effect handling for full-list observation, immediate search filtering, open/share effects, delete selection, delete confirmation, and delete success/error toasts.
- Expanded `LibraryScreen` with a search field, empty-library versus no-results states, document thumbnail/title/metadata/page/date display, open/share/delete actions, and a delete confirmation dialog.
- Wired library open/share effects through navigation using the existing `PdfIntentFactory`.

Implementation decisions:

- Phase 24 uses one search field instead of separate filter controls; matching covers title, grade, subject, year, paper type, and paper number.
- Filtering stays in the ViewModel over the observed Room list, which is sufficient for MVP dataset sizes.
- Delete requires confirmation and delegates to the existing repository path that removes the DB row and owned PDF/thumbnail files.
- Delete failures keep the list visible and surface an inline error plus toast.

Tests:

- Added `LibraryViewModelTest` coverage for observed documents, search matching, empty search results, open/share effects, missing-document toasts, delete selection/dismissal, delete success, and delete failure.
- Added `LibraryScreenStateTest` coverage for empty, populated, search, no-results, and delete-dialog states.
- Updated existing thumbnail UI coverage for the new `LibraryScreen` event callback.

Validation:

- First targeted `LibraryViewModelTest` compile failed because the JUnit rule exposed a private nested type; the rule visibility was fixed.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --tests com.docly.app.feature.library.LibraryViewModelTest --console=plain` completed with `BUILD SUCCESSFUL in 5m 29s`.
- First `:app:ktlintCheck` attempt failed on constructor formatting in `LibraryViewModelTest`; the formatting was fixed.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 20s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 12s`.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 2m 16s`.
- `adb devices` reported one attached device: `1268015548000502`.
- Targeted connected library UI coverage first failed in offline mode because `com.android.tools.utp:android-test-plugin-host-additional-test-output:32.2.0` was not cached; rerunning without `--offline` fetched/used the dependency.
- The first online targeted connected library UI run exposed a test-only issue from calling Compose `setContent` twice in one test; the test was split into two single-content tests.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.library.LibraryScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 2m 19s`; 5 library connected tests passed on `TECNO KL5 - 14`.

## 2026-05-03 - Phase 25 Session Recovery and Crash-Safe Cleanup

Scope:

- Added recoverable-session lookup for the latest `IN_PROGRESS` session with at least one page, plus destination selection for review, editor, or export.
- Added explicit session abandonment that marks the session `ABANDONED`, removes page rows, and then cleans page assets.
- Added startup scanner recovery UI with Resume and Discard actions; capture, import, and scan-mode changes are blocked while the prompt is visible.
- Added orphan-file cleanup for app-managed raw, processed, thumbnail, and PDF directories using Room references from scanned pages and saved documents.
- Made missing page and saved-document delete requests idempotent.
- Added a specific low-storage user message while keeping other storage failures mapped to the generic safe storage message.

Implementation decisions:

- Recovery is prompt-based on scanner entry and skipped when the scanner route already has an explicit session ID for adding pages.
- Orphan cleanup runs from scanner `OnStart` and does not block recovery if cleanup itself fails.
- Cleanup only deletes files inside the app-managed directories and preserves saved-document thumbnails as durable library references.
- Discarding a recovered scan clears the prompt even if post-DB file cleanup reports a storage error, matching the existing DB-first cleanup pattern.

Tests:

- Added JVM coverage for scanner recovery prompt loading, resume destinations, discard, cleanup-failure tolerance, capture/import blocking, and low-storage capture/import messages.
- Added JVM coverage for low-storage review processing and export messages.
- Added connected Compose coverage for recovery prompt display, Resume/Discard events, and blocked capture/import controls.
- Added Room-backed connected coverage for recoverable-session query, abandonment, idempotent missing deletes, and orphan cleanup preserving referenced files.
- Added core result coverage for the low-storage user message passthrough.

Validation:

- Targeted unit pass: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --tests com.docly.app.core.AppResultTest --tests com.docly.app.feature.scanner.ScannerViewModelTest --tests com.docly.app.feature.review.ReviewViewModelTest --tests com.docly.app.feature.export.ExportViewModelTest --console=plain` completed with `BUILD SUCCESSFUL in 8m 6s`.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 50s`.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 58s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 51s`.
- `adb devices` reported one attached device: `1268015548000502`.
- A combined targeted connected run for `RoomRepositoryTest` and `ScannerScreenStateTest` first failed because the new cleanup test returned `Boolean`; after fixing the test signature, a second combined run stalled at 7/26 tests for several minutes and was stopped.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.scanner.ScannerScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 3s`; 10 scanner connected tests passed on `TECNO KL5 - 14`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.data.local.RoomRepositoryTest#scanRepositoryLoadsLatestRecoverableInProgressSessionWithPages,com.docly.app.data.local.RoomRepositoryTest#scanRepositoryAbandonsInProgressSessionAndDeletesPageRowsBeforeAssetCleanup,com.docly.app.data.local.RoomRepositoryTest#scanRepositoryDeletePageIsIdempotentWhenPageIsMissing,com.docly.app.data.local.RoomRepositoryTest#documentRepositoryDeleteDocumentIsIdempotentWhenDocumentIsMissing,com.docly.app.data.local.RoomRepositoryTest#cleanupRepositoryDeletesOnlyUnreferencedManagedFiles --console=plain` completed with `BUILD SUCCESSFUL in 2m 34s`; 5 Phase 25 Room connected tests passed on `TECNO KL5 - 14`.

## 2026-05-04 - Phase 26 Scan Quality Scoring

Scope:

- Added recomputed, non-persisted scan quality scoring for blur, brightness, overexposure, document area, and missing document detection.
- Added CameraX preview analysis that emits both document boundary and quality assessment from the existing analysis frame.
- Added scanner preview hints for document not detected, move closer, improve lighting, and hold steady.
- Added post-capture review warnings with Continue and Rescan choices; Accept is blocked until the warning is deliberately continued.
- Wired raw-image review quality evaluation through `ImageProcessingRepository` and `EvaluateScanQualityUseCase`.

Implementation decisions:

- Quality results are advisory and not stored in Room, avoiding a migration for MVP.
- Review quality warnings are limited to pending pages; accepted pages and quality-evaluation failures do not block review.
- The blur score uses signed Laplacian variance over a bounded luminance buffer so it remains testable outside Android UI.
- Preview hint priority is document missing, document too small, lighting, then blur.

Tests:

- Added JVM scorer coverage for good, blurry, dark, bright, overexposed, missing-document, and small-document synthetic luminance cases.
- Added repository and use-case coverage for quality evaluation delegation and error propagation.
- Expanded scanner ViewModel tests for preview quality hint priority and clearing.
- Expanded review ViewModel tests for warning display, Continue override, Rescan flow, passing quality, evaluation failure, and crop-change recomputation.
- Added connected Compose state coverage for scanner quality hint rendering and review warning Continue/Rescan controls.

Validation:

- First targeted Phase 26 unit run failed because the blur score used absolute Laplacian values; switching to signed Laplacian variance fixed the scorer.
- Targeted Phase 26 unit pass: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --tests com.docly.app.core.image.DefaultScanQualityScorerTest --tests com.docly.app.data.repository.ImageProcessingRepositoryImplTest --tests com.docly.app.domain.EvaluateScanQualityUseCaseTest --tests com.docly.app.feature.scanner.ScannerViewModelTest --tests com.docly.app.feature.review.ReviewViewModelTest --console=plain` completed with `BUILD SUCCESSFUL in 5m 52s`.
- First `:app:ktlintCheck` runs exposed formatting issues in new main and test code; the formatting was fixed.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck --console=plain` completed with `BUILD SUCCESSFUL in 1m 17s`.
- Final `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL in 7m 10s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 8m 16s`.
- `adb devices` reported no attached devices, so targeted connected scanner/review quality UI tests were compiled but not run in this session.

## 2026-05-04 - Phase 27 Performance Hardening

Scope:

- Added a strict post-orientation bitmap fit so `BitmapLoader.decode(path, maxWidth, maxHeight)` never returns a bitmap larger than the requested bounds after EXIF rotation or mirroring.
- Shortened decoded bitmap lifetimes in thumbnail generation, perspective correction, and enhancement by recycling source bitmaps once derived output bitmaps are available.
- Bounded CameraX preview analysis with a `720 x 960` target resolution selector, `KEEP_ONLY_LATEST` backpressure, and a 720px max long-edge analysis frame.
- Reworked preview analysis to use the first luminance plane as a compact grayscale OpenCV `Mat` instead of allocating RGBA frame buffers.
- Migrated editor and library rendering to keyed `LazyColumn` content through a shared lazy scaffold.
- Limited editor list images to saved thumbnails only; missing thumbnails now render the existing placeholder instead of falling back to processed or raw full-page paths.

Implementation decisions:

- Full-quality `ImageCapture` remains unchanged; only preview analysis and decode/render paths are downsampled.
- Review still renders raw or processed paths because it shows one page at a time and needs full-page inspection.
- No Room schema, domain repository contract, pagination API, or benchmark module was added for Phase 27.
- The full non-interactive 20-page real scan/import workflow was not run because it requires camera capture or Android Photo Picker media selection. Connected UI tests now cover 20-row editor and library list stress, and device profiling covered startup plus scanner idle with camera permission granted.

Tests:

- Expanded `BitmapLoaderTest` for invalid bounds and fitted-dimension calculations.
- Expanded editor connected UI coverage for a 20-page lazy list and missing-thumbnail placeholder behavior.
- Expanded library connected UI coverage for a 20-document lazy list and missing-thumbnail placeholder behavior.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL in 5m 58s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 6m 2s`.
- Requested build gate: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 6m 52s`.
- `adb devices` reported attached device `1268015548000502`; device properties identified it as `TECNO KL5`, Android `14`.
- Targeted connected UI coverage: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.editor.EditorScreenStateTest,com.docly.app.feature.library.LibraryScreenStateTest,com.docly.app.feature.scanner.ScannerScreenStateTest,com.docly.app.feature.review.ReviewScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 31s`; 33 tests passed on `TECNO KL5 - 14`.
- Profiling artifacts were stored in `/tmp/codex-android-perf.3ozSQC`.
- Startup/debug scanner snapshot after app install: `meminfo-startup-installed.txt` reported `TOTAL PSS 149320 KB`, `Java Heap 18088 KB`, `Native Heap 10628 KB`, `Graphics 36384 KB`, 1 activity, and 7 views. The matching `gfxinfo-startup-installed.txt` had only one cold-start frame, so it was not used as scrolling evidence.
- Scanner idle snapshot with camera permission granted and `gfxinfo` reset after launch: `meminfo-camera-idle.txt` reported `TOTAL PSS 197123 KB`, `Java Heap 33548 KB`, `Native Heap 13096 KB`, `Graphics 48356 KB`, 1 activity, and 12 views. `gfxinfo-camera-idle.txt` reported 79 frames, 8 janky frames, 50th percentile 12ms, 90th percentile 31ms, and no slow bitmap uploads.

## 2026-05-04 - Phase 28 Accessibility, Adaptive Layouts, and UI Polish

Scope:

- Added shared adaptive Compose layout helpers for compact-width and large-font action stacking, minimum touch targets, reduced compact padding, and tablet max-width content.
- Added polite live-region semantics and content descriptions to shared loading, error, empty, image thumbnail, camera preview, scanner/library actions, dialogs, and crop handles.
- Added destructive confirmations for recovered-session discard, review rescan, and editor page delete while preserving existing ViewModel events and leaving export/open/share/navigation actions direct.
- Improved large-text resilience across scanner controls, recovery and quality prompts, review/editor/library action groups, exported actions, and editor/library rows.
- Added blocking error state handling for unavailable metadata sessions and unavailable export previews.

Implementation decisions:

- No Room schema, navigation route, domain model, repository contract, or persisted UI state change was added for Phase 28.
- Destructive confirmations are local Compose state and dispatch existing events only after confirmation.
- The adaptive layout uses conservative width/font-scale thresholds and keeps the existing single-pane navigation architecture.
- Crop handles were increased to 48dp so manual crop controls meet minimum touch-target expectations.

Tests:

- Expanded connected Compose coverage for key content descriptions, destructive confirmation dialogs, large-font compact-width rendering, shared state semantics, metadata/export blocking error states, and adaptive action reachability.
- Existing editor/library 20-row lazy-list coverage continues to validate larger content sets after the adaptive layout changes.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintFormat :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain` initially exposed one ktlint constant-naming issue and one stale `assertExists` import; both were fixed. The narrowed rerun of `:app:compileDebugAndroidTestKotlin` completed with `BUILD SUCCESSFUL in 3m 56s`.
- Requested local gate: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 5m 27s`.
- `adb devices` reported attached device `1268015548000502` (`TECNO KL5 - 14`).
- First targeted connected UI run failed one scanner assertion because the Phase 28 progress text changed from the button label to explicit loading content; the test was updated to assert `Capturing page...` and scroll to the disabled capture action.
- A later combined targeted connected run stalled before test progress after ADB restarted. Logcat showed device idleness blocked by background `com.instagram.android`; after force-stopping that app and splitting the suite, connected validation passed.
- Scanner connected coverage: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.scanner.ScannerScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 24s`; 13 tests passed on `TECNO KL5 - 14`.
- Remaining Phase 28 connected UI coverage: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.docly.app.feature.review.ReviewScreenStateTest,com.docly.app.feature.editor.EditorScreenStateTest,com.docly.app.feature.metadata.MetadataScreenStateTest,com.docly.app.feature.export.ExportScreenStateTest,com.docly.app.feature.library.LibraryScreenStateTest,com.docly.app.ui.components.ThumbnailScreenStateTest --console=plain` completed with `BUILD SUCCESSFUL in 4m 2s`; 41 tests passed on `TECNO KL5 - 14`.

## 2026-05-04 - Phase 29 Release Hardening

Scope:

- Added release version discipline through `docly.versionCode=29` and `docly.versionName=0.29.0` in Gradle properties.
- Added optional release signing from `DOCLY_RELEASE_STORE_FILE`, `DOCLY_RELEASE_STORE_PASSWORD`, `DOCLY_RELEASE_KEY_ALIAS`, and `DOCLY_RELEASE_KEY_PASSWORD`, accepted as Gradle properties or environment variables.
- Enabled release minification and resource shrinking with R8 while keeping `proguard-android-optimize.txt`.
- Removed the unused WorkManager runtime dependency and version-catalog entry.
- Disabled Android backup and replaced generated sample backup XMLs with explicit private-data exclusions for legacy backup, cloud backup, and device transfer.
- Added privacy notes and an internal release checklist, and linked both from the README.

Implementation decisions:

- No Kotlin/domain/UI API, Room schema, navigation route, repository contract, or user-facing workflow change was added for Phase 29.
- The app-declared runtime permission remains `android.permission.CAMERA`; a transitive `androidx.media3` `ACCESS_NETWORK_STATE` permission was explicitly removed through the manifest merger.
- The merged release manifest still contains AndroidX Core's signature-only `com.docly.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; it is an app-local protection permission, not an Android runtime or network/storage permission.
- `FileProvider` remains limited to `documents/pdf/` and keeps `exported=false` with temporary URI grants for explicit open/share actions.
- `org.gradle.jvmargs` now includes `-XX:TieredStopAtLevel=1` because the first minified release run stayed in R8 for an impractically long time while the JVM compiled R8 optimizer code; with the tiering cap persisted, the requested release gate completes.
- No broad CameraX, Room, Hilt, OpenCV, or Coil keep/dontwarn rules were added because R8 produced no missing-class or reflection failure.

Validation:

- Final release gate: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleRelease --console=plain` completed with `BUILD SUCCESSFUL in 10m 40s`; 92 actionable tasks, 15 executed and 77 up to date.
- The minified unsigned release artifact was generated at `app/build/outputs/apk/release/app-release-unsigned.apk`; `output-metadata.json` reports `versionCode` 29 and `versionName` `0.29.0`.
- The merged release manifest reports `android:allowBackup="false"`, `android.permission.CAMERA`, optional `android.hardware.camera.any`, and the existing PDF `FileProvider`.
- The merged release manifest does not contain `INTERNET`, broad storage/media permissions, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, or `FOREGROUND_SERVICE`.
- No `DOCLY_RELEASE_*` signing values were present in this shell, so no signed `app-release.apk` was produced.
- `adb devices` reported no attached devices after starting ADB, so the signed release install and real-device smoke checklist were documented but not run in this session.

## 2026-05-06 - Phase 30 Post-MVP Foundations

Scope:

- Added bundled ML Kit OCR for Latin, Chinese, Devanagari, Japanese, and Korean text recognition, with OCR scheduled after successful PDF export and kept non-blocking for the export flow.
- Added a separate OCR persistence path with document OCR status/result rows plus an FTS-backed text index used by library search.
- Replaced library in-memory filtering with repository-backed metadata and OCR search while preserving the existing empty and no-results states.
- Added explicit manual document upload from the library, including upload status display, retry action, WorkManager scheduling with network constraints, exponential backoff, and local state transitions.
- Added the unauthenticated signed-upload client contract: create upload intent, PUT PDF and optional thumbnail to signed URLs, then call the completion endpoint.
- Added local structured diagnostics for OCR, upload, processing, camera compatibility, and auto-capture suppression; diagnostics remain local only.
- Added scanner auto-capture controls and stability gating while keeping manual capture available.
- Updated privacy/release documentation for explicit manual upload, local diagnostics, network permissions, and the blank-by-default `DOCLY_BACKEND_BASE_URL` behavior.

Implementation decisions:

- Room was bumped from schema version `2` to `3`; `MIGRATION_2_3` adds OCR, FTS, diagnostics, and upload-state storage, and schema `3.json` was generated.
- OCR text is stored separately from scan/PDF records. OCR failures update OCR status and diagnostics but do not alter the saved PDF or saved document row.
- `DoclyApplication` now provides WorkManager configuration through injected `HiltWorkerFactory`, and WorkManager's default initializer is removed from the manifest.
- Upload is never automatic after export. A blank `BuildConfig.DOCLY_BACKEND_BASE_URL` makes upload scheduling fail with a clear not-configured error.
- Upload workers retry transient HTTP failures (`408`, `429`, and `5xx`) and treat other backend errors as permanent failures for this phase.
- No analytics SDK, analytics events, remote diagnostics upload, or auth header was added.
- Release minification and shrinking remain enabled, but `-dontoptimize` was added to R8 after the bundled OCR release build stayed in optimization for more than an hour.

Tests:

- Added OCR use-case and merge/deduplication coverage with fake OCR engines.
- Added upload repository coverage for signed-intent parsing, file PUT/completion behavior, missing backend configuration, and transient/permanent failure handling.
- Added library ViewModel coverage for repository-backed OCR search and manual upload queueing.
- Added scanner ViewModel coverage for auto-capture stability and manual fallback behavior.
- Added diagnostics repository coverage through Room insertion paths.
- Added Room migration and OCR FTS search instrumentation coverage.
- Added Compose state coverage for library OCR/upload status/actions and scanner auto-capture toggle/hint behavior.

Validation:

- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:compileDebugUnitTestKotlin --console=plain` completed with `BUILD SUCCESSFUL`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:testDebugUnitTest --console=plain` completed with `BUILD SUCCESSFUL`.
- Requested local offline gate: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 41m 1s`.
- Final narrow rerun after connected-test scroll fixes: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:ktlintCheck :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 2m 57s`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --offline --max-workers=1 :app:assembleRelease --console=plain` first stayed in R8 for over an hour and was stopped; after adding `-dontoptimize`, the same release command completed with `BUILD SUCCESSFUL in 16m 7s`.
- The unsigned release artifact was generated at `app/build/outputs/apk/release/app-release-unsigned.apk`; `output-metadata.json` reports `versionCode` 30 and `versionName` `0.30.0`. The release APK is about `192M`.
- The merged release manifest intentionally includes `android.permission.CAMERA`, `android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`, AndroidX WorkManager scheduler permissions (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`), the app-local dynamic receiver permission, `allowBackup=false`, and the existing PDF `FileProvider`.
- Initial targeted connected Phase 30 coverage on `TECNO KL5 - 14` installed and ran 25 tests, then exposed small-screen reachability assertions in scanner/library Compose tests; those assertions were updated to scroll before visibility checks.
- The final targeted connected rerun was blocked before test execution by device storage: installing `app-debug.apk` failed with `INSTALL_FAILED_INSUFFICIENT_STORAGE`, zero tests ran, and Android cache trimming only raised available `/data` space to about `1.3G`.

## 2026-05-11 - Documentation Roadmap Reset For Document Utility Product

Scope:

- Documentation-only reset for the broader Docly product direction defined in `/home/prometheus/Desktop/Docly.txt`.
- Replaced the active scanner-only planning docs with a local-first document utility roadmap.
- Updated `Implementation Roadmap.md`, `Technical Architecture.md`, `Low-Level Design (LLD).md`, `Build Roadmap.md`, `Privacy Notes.md`, and `Release Checklist.md`.
- No app code, Gradle configuration, manifest entries, schemas, tests, or generated files were changed as part of this documentation reset.

Product decisions recorded:

- Docly is now documented as a scanner, reader, creator, editor, converter, and local document manager.
- The build order is scanner, reader, creator, editor, then converter, with a unified document foundation before those product stages.
- ML Kit Document Scanner is the preferred first scanner engine, behind a replaceable `DocumentScannerService` abstraction.
- TXT, Markdown, and HTML are the directly editable MVP formats.
- PDF editing is documented as page management and later annotation, not arbitrary original text/layout editing.
- DOCX and XLSX support starts with simplified readers and later limited export; direct full Office editing is explicitly out of MVP scope.
- MVP remains local-first with no account, cloud backup, automatic upload, server conversion, analytics, or remote diagnostics requirement.

Validation:

- Documentation was searched for stale scanner-only positioning and old MVP cloud/upload assumptions after the rewrite.
- Gradle tests were not run because this change only edits Markdown documentation.

## 2026-05-11 - Phase 1-2 Unified Local Document Foundation

Scope:

- Implemented Phase 1 as the active product baseline and Phase 2 as a fresh local Room reset instead of a scanner-era data migration.
- Replaced scanner-only library persistence with the unified `DoclyDocument` model and document types for PDF, TXT, Markdown, HTML, image, DOCX, XLSX, CSV, and unsupported `UNKNOWN`.
- Added file-type resolution, document capability resolution, internal document storage under `files/docly/documents/<type>/`, managed FileProvider open/share intents, and app-owned file deletion.
- Bumped Room to schema version `4`; `MIGRATION_3_4` drops legacy document/upload/OCR tables and creates `documents`, `folders`, `recent_documents`, `conversion_jobs`, `scan_sessions`, and `scan_pages`.
- Removed the active manual upload/network path, backend build config, OkHttp/mock upload dependencies, WorkManager upload/OCR workers, ML Kit OCR text-recognition behavior, and network permissions from the MVP path.
- Built Home-first top-level navigation for Home, Documents, Search, Create, Tools, and Settings. Documents now owns SAF import, search, sort, filter, list/grid view, favorite, rename, delete, share, and system-open effects.

Implementation decisions:

- Existing `saved_documents` rows are intentionally not migrated into the new document library.
- OCR remains a document metadata status only; OCR workers and text-recognition indexing are deferred to the later OCR phase.
- Rename is metadata-only in Phase 2 and does not rename the internal physical file.
- Scanner/export compatibility remains compiling, and successful scan PDF export registers a `DoclyDocument` with `DocumentSource.SCANNED`.
- `UNKNOWN` files fail before repository insertion.

Validation:

- Stale-scope search was run across active docs, source, manifest, Gradle files, and README for upload/backend/network/OCR-worker/scanner-only remnants. Remaining upload/cloud references are historical implementation notes or explicit future/privacy gates; active source/build upload wording was removed.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:compileDebugKotlin --console=plain` completed with `BUILD SUCCESSFUL`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:compileDebugUnitTestKotlin --console=plain` completed with `BUILD SUCCESSFUL`.
- `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:compileDebugAndroidTestKotlin --console=plain` completed with `BUILD SUCCESSFUL in 8m 42s`.
- Requested local gate: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` completed with `BUILD SUCCESSFUL in 3m 41s`.
- The regenerated debug merged, packaged, and androidTest packaged manifests do not contain `INTERNET` or `ACCESS_NETWORK_STATE`.
