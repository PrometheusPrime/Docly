# Release Checklist

Use this checklist for every internal release candidate.

## Versioning

- Increase `docly.versionCode` in `gradle.properties` for every distributed build.
- Update `docly.versionName` using pre-1.0 semantic versioning until the MVP is declared stable.
- Record the release scope in implementation notes or release notes.

## Build Gate

Run the local gate:

```sh
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:ktlintCheck :app:testDebugUnitTest :app:assembleRelease --console=plain
```

Run instrumentation tests for changed features when a device or emulator is available:

```sh
JAVA_HOME=/usr/lib/jvm/java-26-openjdk ./gradlew --no-daemon --max-workers=1 :app:connectedDebugAndroidTest --console=plain
```

Expected release artifacts:

- Unsigned without signing values: `app/build/outputs/apk/release/app-release-unsigned.apk`
- Signed with signing values: `app/build/outputs/apk/release/app-release.apk`

## Signing Setup

Release signing is optional at build time. Provide all values as Gradle properties or environment variables for a signed APK:

```sh
export DOCLY_RELEASE_STORE_FILE=/absolute/path/to/docly-release.jks
export DOCLY_RELEASE_STORE_PASSWORD=...
export DOCLY_RELEASE_KEY_ALIAS=...
export DOCLY_RELEASE_KEY_PASSWORD=...
```

Keep keystores and passwords out of git.

## Manifest And Permission Check

Inspect the merged release manifest.

Expected for local-first MVP:

- `android.permission.CAMERA`
- WorkManager scheduler permissions for local thumbnail generation: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and `FOREGROUND_SERVICE`
- Optional camera hardware declaration with `required=false`
- `FileProvider` exported `false` with temporary URI grants
- Backup behavior matching `docs/Privacy Notes.md`
- `androidx.work.WorkManagerInitializer` absent so Hilt-backed workers use the app-provided `HiltWorkerFactory`

Must be absent unless a separately reviewed feature requires it:

- `MANAGE_EXTERNAL_STORAGE`
- Broad storage/media permissions
- Contacts, location, microphone, SMS, phone, or notification permissions
- `INTERNET` and `ACCESS_NETWORK_STATE` for local-only MVP builds
- Extra background scheduler permissions unless a local background feature is included and documented above

## Smoke Test

Install the release APK on a physical device.

Documents:

- Launch Docly.
- Verify empty state offers scan, import, and create actions.
- Import PDF, TXT, Markdown, HTML, image, DOCX, and XLSX fixture files through SAF.
- Verify document cards show type, title, updated date, and supported actions.
- Rename, delete, share, sort, filter, and search documents.

Scanner:

- Start scanning and verify camera permission request timing.
- Scan a one-page document and a multi-page document.
- Review pages, reorder, rotate, delete, and add a page.
- Save as PDF and as images.
- Verify outputs appear in Documents and can be opened/shared.

Readers:

- Open a PDF and verify page rendering, navigation, zoom, and large-file behavior.
- Open TXT, Markdown, and HTML files.
- Open DOCX and XLSX fixtures and verify simplified-mode messaging.

Creators:

- Create TXT, Markdown, and HTML documents.
- Create a PDF from scanned pages or imported images.
- Create a PDF from TXT, Markdown, and HTML content.
- Verify created documents appear in Documents.

Editors:

- Edit TXT, Markdown, and HTML documents.
- Verify autosave/manual save behavior.
- Close and reopen each edited document.
- Verify Markdown and HTML preview modes.
- Verify scan-source PDF page management if included in the release.

Converter:

- Select TXT, Markdown, HTML, and image inputs.
- Confirm only supported output formats are offered.
- Run successful conversions.
- Trigger at least one unsupported or failed conversion and verify the error message.
- Verify conversion outputs appear in Documents and can be opened/shared.

Settings and privacy:

- Verify appearance settings if included.
- Verify scanner, reader, export, and storage settings if included.
- Deny camera permission and confirm recovery messaging.
- Confirm the app works offline for MVP workflows.
- Confirm no automatic upload, sync, analytics, or document content transmission occurs in MVP builds.

## Performance And Accessibility

- Test on at least one low-to-mid range physical device.
- Open a large PDF and verify memory-safe rendering.
- Scroll a library with at least 100 documents or fixture records.
- Enable large font and verify controls remain usable.
- Run a TalkBack pass for main navigation, document cards, scanner controls, reader controls, editor actions, and converter actions.

## Privacy Review Gate

Before adding any of the following, update `docs/Privacy Notes.md`, this checklist, and product copy:

- Cloud backup.
- Account sync.
- Server-side conversion.
- Upload.
- Ads.
- Analytics.
- Crash reporting.
- Remote diagnostics.
- Premium billing tied to identity.
