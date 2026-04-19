# Build Roadmap

## Docly

This is the **implementation roadmap** for building the app.

---

# 1. MVP Goal

Build an **internal scanning tool** that can:

* capture pages
* detect document edges
* correct perspective
* enhance readability
* manage multiple pages
* export to PDF
* save locally with metadata

This roadmap is optimized for **incremental delivery**. Each phase should leave the app in a runnable state.

---

# 2. Target MVP Deliverables

By the end of the MVP, you should have:

* Scanner screen
* Page review screen
* Document editor screen
* Metadata screen
* Export-to-PDF flow
* Local scan library
* Offline local storage

---

# 3. Recommended Stack

## Core

* **Kotlin**
* **Jetpack Compose**
* **CameraX**
* **Room**
* **Coroutines + Flow**
* **WorkManager** later
* **OpenCV Android**

## Helpful libraries

* **Hilt** for DI
* **Coil** for image previews
* `androidx.documentfile` if export targets shared storage later

---

# 4. Development Phases

---

## Phase 0 — Project Foundation

### Objective

Set up the project skeleton and core architecture.

### Tasks

* Create Android project
* Set min SDK
* Set up Compose
* Add module/package structure
* Add Hilt
* Add Room
* Add Coil
* Add CameraX dependencies
* Add OpenCV integration
* Set up navigation
* Create theme and design tokens
* Set up basic logging
* Create base `Result` wrapper
* Create folder/file manager utility

### Output

A clean app shell with navigation and architecture ready.

### Definition of done

* App launches
* Navigation works between placeholder screens
* DI works
* Room initializes
* OpenCV loads successfully

---

## Phase 1 — Domain and Data Foundations

### Objective

Build the core models and persistence layer before the scanner UI becomes complex.

### Tasks

Create domain models:

* `ScanSession`
* `ScannedPage`
* `DocumentMetadata`
* `SavedDocument`

Create Room entities:

* `ScanSessionEntity`
* `ScannedPageEntity`
* `SavedDocumentEntity`

Create DAOs:

* `ScanSessionDao`
* `ScannedPageDao`
* `SavedDocumentDao`

Create repositories:

* `ScanRepository`
* `DocumentRepository`
* `PdfRepository`

Create file storage service:

* save raw image
* save processed image
* save thumbnail
* save PDF
* delete session files

### Output

Working persistence and file-management layer.

### Definition of done

* Can create a scan session
* Can save mock pages to DB/files
* Can read them back
* Can delete them safely

---

## Phase 2 — Scanner Screen v1 (Manual Capture Only)

### Objective

Get a live camera preview and manual page capture working first.

### Tasks

Build:

* `ScannerScreen`
* `ScannerViewModel`
* camera permission flow
* CameraX preview
* manual shutter button
* flash toggle
* mode selector UI placeholder
* capture image to temp file

Do **not** add edge detection yet if it slows progress.
First get reliable capture working.

### Output

User can open scanner and capture a page manually.

### Definition of done

* Camera preview is stable
* Manual capture saves image file
* Captured file can be previewed
* No crash on permission denial path

---

## Phase 3 — Document Detection and Perspective Correction

### Objective

Turn raw photos into document-like scans.

### Tasks

Implement image-processing pipeline:

* decode bitmap efficiently
* convert to OpenCV `Mat`
* grayscale
* blur
* Canny edge detection
* contour detection
* largest quadrilateral detection
* corner ordering
* perspective warp

Create:

* `DocumentDetector`
* `PerspectiveTransformer`
* `CornerOrderingUtil`

Add overlay on preview:

* draw detected document contour
* indicate success/failure

Fallback:

* if no document detected, still allow manual crop later

### Output

Captured images can be auto-cropped and flattened.

### Definition of done

* Most pages under decent lighting detect correctly
* Warped output looks like a flat page
* Failures fall back gracefully

---

## Phase 4 — Page Review Screen

### Objective

Let the user confirm or reject each scanned page.

### Tasks

Build:

* `PageReviewScreen`
* processed image preview
* original vs processed toggle
* rotate page
* rescan page
* accept page
* manual crop adjustment UI

Manual crop adjustment can start simple:

* draggable 4-corner overlay
* re-run perspective correction on confirm

### Output

Every captured page goes through a review step before entering the document.

### Definition of done

* User can accept processed page
* User can rotate it
* User can manually fix crop
* User can rescan

---

## Phase 5 — Enhancement Modes

### Objective

Improve readability and support different page types.

### Tasks

Implement 3 processing modes:

### Document mode

* grayscale
* adaptive threshold
* denoise
* sharpen

### Mixed mode

* mild contrast
* line preservation
* lighter denoise

### Color mode

* preserve original colors
* mild cleanup

Create:

* `ImageEnhancer`
* `ScanModeProcessor`

Add UI:

* mode selector
* live mode choice before processing
* reprocess page after mode change

### Output

Pages can be processed appropriately for text, diagrams, or colored content.

### Definition of done

* Output quality changes visibly by mode
* Diagrams are preserved better in mixed mode
* Text-heavy pages look clean in document mode

---

## Phase 6 — Multi-Page Session Management

### Objective

Support real documents, not just single-page capture.

### Tasks

Build:

* session page list
* thumbnail strip/grid
* add another page
* reorder pages
* delete page
* rotate saved page
* re-open page review

Create:

* `DocumentEditorScreen`
* `ReorderPagesUseCase`
* `DeletePageUseCase`
* `RotatePageUseCase`

For reorder:

* use drag-and-drop if practical
* otherwise provide move up/down for first version

### Output

A scan session can contain multiple ordered pages.

### Definition of done

* User can build a multi-page paper
* Page order persists
* Pages can be deleted/reordered reliably

---

## Phase 7 — Metadata Screen

### Objective

Attach educational structure to the scanned paper.

### Tasks

Build:

* `MetadataScreen`
* grade dropdown
* subject dropdown
* year input
* paper type dropdown
* optional paper number
* source/notes optional
* filename preview

Validation:

* required fields must be completed before export

Create:

* `ValidateMetadataUseCase`
* `GenerateDocumentNameUseCase`

### Output

Each scan session can be classified for Amasambililo ingestion.

### Definition of done

* Metadata is saved locally
* Validation blocks incomplete export
* Filename generation is consistent

---

## Phase 8 — PDF Export

### Objective

Generate the final usable document.

### Tasks

Build PDF pipeline:

* fetch ordered processed pages
* render pages into PDF
* compress reasonably
* save to app storage
* generate thumbnail
* persist `SavedDocument`

Create:

* `GeneratePdfUseCase`
* `SaveDocumentUseCase`

Add export screen:

* export button
* progress state
* success/failure result
* open/share action

### Output

User can export a full scan session into a PDF.

### Definition of done

* PDF is readable
* page order matches editor
* exported file opens correctly
* metadata record is saved

---

## Phase 9 — Local Library

### Objective

Allow browsing and reusing previously scanned documents.

### Tasks

Build:

* `LibraryScreen`
* list saved PDFs
* thumbnail + metadata display
* open details
* delete
* share/export again
* basic search/filter by subject/year

Create:

* `GetSavedDocumentsUseCase`
* `DeleteDocumentUseCase`

### Output

The app has a persistent local archive.

### Definition of done

* Saved documents appear in library
* Documents can be opened and deleted
* Metadata is visible and filterable

---

## Phase 10 — Quality Checks and Hardening

### Objective

Make the scanner dependable.

### Tasks

Add pre/post capture checks:

* blur score
* brightness score
* overexposure score
* document area score

Add user guidance:

* “Move closer”
* “Lighting too low”
* “Hold steady”
* “Document not detected”

Add safeguards:

* autosave in-progress session
* recover unfinished scan session
* storage space check
* large image memory protections
* crash-safe file cleanup

### Output

A scanner that behaves predictably in real-world conditions.

### Definition of done

* Common bad scans are flagged
* App recovers from interruptions
* Low-memory crashes are reduced

---

# 5. Suggested Build Order Inside Android Studio

Use this exact order to reduce churn:

## Step 1

Project shell, navigation, dependencies

## Step 2

Room entities, DAOs, repositories, file storage

## Step 3

Camera preview + manual image capture

## Step 4

Image-processing pipeline with OpenCV

## Step 5

Page review flow

## Step 6

Enhancement modes

## Step 7

Multi-page editor

## Step 8

Metadata form

## Step 9

PDF generation

## Step 10

Library and hardening

That sequence is correct because it avoids building complex UI before the capture pipeline works.

---

# 6. Milestones

## Milestone A — Capture Prototype

Includes:

* camera preview
* manual capture
* temp file save

### Goal

Prove scanning is technically viable on your target phone.

---

## Milestone B — Processing Prototype

Includes:

* document detection
* perspective correction
* processed output preview

### Goal

Prove page quality is good enough for past papers.

---

## Milestone C — End-to-End Single Page

Includes:

* capture
* process
* review
* export one-page PDF

### Goal

Prove full workflow works.

---

## Milestone D — Multi-Page MVP

Includes:

* multiple pages
* reorder/delete
* metadata
* final PDF
* local library

### Goal

Reach MVP-complete state.

---

# 7. Suggested Sprint Breakdown

## Sprint 1

* Phase 0
* Phase 1
* Phase 2

## Sprint 2

* Phase 3
* Phase 4

## Sprint 3

* Phase 5
* Phase 6

## Sprint 4

* Phase 7
* Phase 8

## Sprint 5

* Phase 9
* Phase 10

If working alone, these are not necessarily one-week sprints. Treat them as implementation blocks.

---

# 8. Technical Risks by Phase

## Phase 2 risk

CameraX lifecycle issues

### Mitigation

* isolate camera controller
* test on real device early

---

## Phase 3 risk

Document detection may fail on poor lighting or low contrast pages

### Mitigation

* keep manual crop fallback
* test with real exam pages early

---

## Phase 5 risk

Aggressive enhancement may damage diagrams

### Mitigation

* use separate scan modes
* compare original vs processed preview

---

## Phase 8 risk

Large PDFs may become too heavy

### Mitigation

* use controlled JPEG compression
* cap image resolution sensibly

---

## Phase 10 risk

Memory pressure on low-end devices

### Mitigation

* avoid loading full-size bitmaps into UI
* process off main thread
* use thumbnails in editor/library

---

# 9. What to Postpone

To keep delivery tight, postpone these until after MVP:

* OCR
* anti-glare multi-frame fusion
* backend upload
* cloud sync
* auto-capture
* AI subject/year detection
* public upload workflows

These are valuable, but they are not MVP-critical.

---



---

# 11. Recommended First Three  Tasks

## Task 1

Set up project foundation:

* package structure
* Hilt
* Navigation
* Room
* base result wrapper

## Task 2

Implement CameraX scanner screen with manual capture and image file save

## Task 3

Implement OpenCV document detection + perspective correction pipeline for a captured image

Those three tasks establish the technical core.

---

# 12. Completion Criteria for MVP

The MVP is done when an internal user can:

1. open scanner
2. capture all pages of a past paper
3. review and fix pages
4. reorder pages
5. enter metadata
6. export a readable PDF
7. find that PDF later in the local library

If that works reliably, the MVP is successful.

---

# 13. Strong Recommendation

Start with:

## **manual capture + strong review tools**

not auto-capture.

Why:

* simpler
* more controllable
* easier to debug
* faster to ship

