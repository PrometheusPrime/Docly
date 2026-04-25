# Implementation Notes

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
