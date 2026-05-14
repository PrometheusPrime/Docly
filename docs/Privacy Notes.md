# Privacy Notes

Docly MVP is a local-first Android document utility. Scanning, reading, creating, editing, converting, library management, and sharing must work without an account, backend, cloud backup, or server-side conversion.

## Local Data

- Imported, scanned, created, edited, and converted documents are stored in app-specific storage by default.
- Room stores metadata, file references, job state, and indexes only. It must not store large PDF/image/document binaries.
- Imported external files are copied into Docly-managed storage unless a future advanced setting explicitly chooses URI-only access.
- Original external files are not overwritten unless the user explicitly exports to a chosen destination through Android system UI.
- Deleted app-owned documents should remove the associated app-owned files and metadata.

## Permissions

- Camera permission is requested only when the user starts document scanning.
- File import/export should use the Android Storage Access Framework.
- Image picking should use system picker/scanner result URIs rather than broad storage permissions.
- Sharing uses `FileProvider` content URIs with temporary read grants.
- MVP should not request contacts, location, microphone, SMS, phone, notification, or broad all-files permissions.
- Internet permission is not required for the local-first MVP. Add it only for a separately reviewed cloud, sync, upload, ads, crash reporting, or analytics feature.
- WorkManager is used only for local background thumbnail generation in MVP. It must not be used for uploads, sync, analytics, or remote diagnostics without a separate privacy review.

## Backup

- Private document content should not be backed up by default unless the product explicitly adds encrypted backup with user control.
- Backup rules should avoid restoring Room records whose app-private files may not exist.
- If backup behavior changes, update this file and the release checklist before shipping.

## Open, Export, And Share Boundary

- Documents are exposed to other apps only after explicit user action: open, share, print, or export.
- SAF export lets the user choose the destination.
- `FileProvider` should expose only intended output directories, not raw scanner temp files, database files, OCR indexes, or internal caches.

## OCR And Diagnostics

- OCR is a later feature and should run locally first where practical.
- OCR text is stored separately from source documents and may be deleted independently if product settings require it.
- Diagnostics should be local and technical by default.
- Remote diagnostics, analytics, crash reporting, account sync, cloud conversion, cloud backup, or upload require a separate privacy review and explicit documentation update.
