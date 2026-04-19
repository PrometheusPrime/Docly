package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRepositoryImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun pathCreationCreatesDirectoriesAndSanitizesFileNames() {
        val repository = repository()

        val rawPath = repository.createSessionImagePath("Session ID", "Page #1")
        val processedPath = repository.createProcessedImagePath("Session ID", "Page #1")
        val thumbnailPath = repository.createThumbnailPath("Session ID", "Page #1")

        assertEquals("raw_session_id_page_1.jpg", File(rawPath).name)
        assertEquals("processed_session_id_page_1.jpg", File(processedPath).name)
        assertEquals("thumb_session_id_page_1.jpg", File(thumbnailPath).name)
        assertTrue(File(rawPath).parentFile?.isDirectory == true)
        assertTrue(File(processedPath).parentFile?.isDirectory == true)
        assertTrue(File(thumbnailPath).parentFile?.isDirectory == true)
    }

    @Test
    fun rawPathSupportsSanitizedCustomImageExtensions() {
        val repository = repository()

        val pngPath = repository.createSessionImagePath("Session ID", "Page #1", "PNG")
        val jpegPath = repository.createSessionImagePath("Session ID", "Page #2", ".jpeg")
        val fallbackPath = repository.createSessionImagePath("Session ID", "Page #3", "")

        assertEquals("raw_session_id_page_1.png", File(pngPath).name)
        assertEquals("raw_session_id_page_2.jpg", File(jpegPath).name)
        assertEquals("raw_session_id_page_3.jpg", File(fallbackPath).name)
    }

    @Test
    fun pdfPathAddsPdfExtensionWhenMissingAndPreservesExistingExtension() {
        val repository = repository()

        val addedExtensionPath = repository.createPdfPath("Grade 10 Math")
        val existingExtensionPath = repository.createPdfPath("Already.PDF")

        assertEquals("grade_10_math.pdf", File(addedExtensionPath).name)
        assertEquals("already.pdf", File(existingExtensionPath).name)
        assertTrue(File(addedExtensionPath).parentFile?.isDirectory == true)
    }

    @Test
    fun deleteFileAndDeleteFilesAreIdempotentForMissingFiles() = runBlocking {
        val repository = repository()
        val firstFile = temporaryFolder.newFile("first.jpg")
        val secondFile = temporaryFolder.newFile("second.jpg")
        firstFile.writeText("first")
        secondFile.writeText("second")

        assertSuccess(repository.deleteFile(firstFile.absolutePath))
        assertSuccess(repository.deleteFile(firstFile.absolutePath))
        assertSuccess(repository.deleteFiles(listOf(secondFile.absolutePath, "/missing/file.jpg")))

        assertFalse(firstFile.exists())
        assertFalse(secondFile.exists())
    }

    @Test
    fun deletePageAssetsDeletesOriginalProcessedAndThumbnailFiles() = runBlocking {
        val repository = repository()
        val page = page(
            originalImagePath = temporaryFolder.writeFixture("raw/page.jpg").absolutePath,
            processedImagePath = temporaryFolder.writeFixture("processed/page.jpg").absolutePath,
            thumbnailPath = temporaryFolder.writeFixture("thumb/page.jpg").absolutePath
        )

        assertSuccess(repository.deletePageAssets(page))

        assertFalse(File(page.originalImagePath).exists())
        assertFalse(File(page.processedImagePath.orEmpty()).exists())
        assertFalse(File(page.thumbnailPath.orEmpty()).exists())
    }

    @Test
    fun deleteSessionAssetsDeletesEachPageAsset() = runBlocking {
        val repository = repository()
        val firstPage = page(
            id = "first",
            originalImagePath = temporaryFolder.writeFixture("raw/first.jpg").absolutePath,
            processedImagePath = temporaryFolder.writeFixture("processed/first.jpg").absolutePath,
            thumbnailPath = null
        )
        val secondPage = page(
            id = "second",
            originalImagePath = temporaryFolder.writeFixture("raw/second.jpg").absolutePath,
            processedImagePath = null,
            thumbnailPath = temporaryFolder.writeFixture("thumb/second.jpg").absolutePath
        )
        val session = ScanSession(
            id = "session-id",
            createdAt = 1L,
            updatedAt = 2L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = ScanMode.DOCUMENT,
            pages = listOf(firstPage, secondPage)
        )

        assertSuccess(repository.deleteSessionAssets(session))

        session.pages.flatMap { page ->
            listOfNotNull(page.originalImagePath, page.processedImagePath, page.thumbnailPath)
        }.forEach { path ->
            assertFalse(File(path).exists())
        }
    }

    @Test
    fun deleteSavedDocumentAssetsDeletesPdfAndThumbnailFiles() = runBlocking {
        val repository = repository()
        val document = SavedDocument(
            id = "document-id",
            sessionId = "session-id",
            title = "Title",
            pdfPath = temporaryFolder.writeFixture("documents/document.pdf").absolutePath,
            thumbnailPath = temporaryFolder.writeFixture("thumb/document.jpg").absolutePath,
            metadata = metadata(),
            pageCount = 1,
            createdAt = 3L
        )

        assertSuccess(repository.deleteSavedDocumentAssets(document))

        assertFalse(File(document.pdfPath).exists())
        assertFalse(File(document.thumbnailPath.orEmpty()).exists())
    }

    @Test
    fun ensureStorageAvailableReturnsStorageErrorWhenRequiredBytesExceedUsableSpace() = runBlocking {
        val repository = repository()

        assertSuccess(repository.ensureStorageAvailable(0L))

        val result = repository.ensureStorageAvailable(Long.MAX_VALUE)

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
    }

    private fun repository(): FileRepositoryImpl = FileRepositoryImpl(
        appFileDirectories = TempAppFileDirectories(temporaryFolder.newFolder("docly-files")),
        dispatcherProvider = UnconfinedDispatcherProvider()
    )

    private fun TemporaryFolder.writeFixture(relativePath: String): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("fixture")
        return file
    }

    private fun page(
        id: String = "page-id",
        originalImagePath: String,
        processedImagePath: String?,
        thumbnailPath: String?
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = originalImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

    private fun metadata(): DocumentMetadata = DocumentMetadata(
        grade = "Grade 10",
        subject = "Math",
        year = 2026,
        paperType = "Past Paper"
    )

    private fun assertSuccess(result: AppResult<Unit>) {
        assertTrue(result is AppResult.Success)
    }

    private class TempAppFileDirectories(root: File) : AppFileDirectories {
        override val rawScanDirectory: File = File(root, "scans/raw")
        override val processedScanDirectory: File = File(root, "scans/processed")
        override val thumbnailDirectory: File = File(root, "scans/thumbnails")
        override val pdfDirectory: File = File(root, "documents/pdf")

        override fun ensureDirectories() {
            rawScanDirectory.mkdirs()
            processedScanDirectory.mkdirs()
            thumbnailDirectory.mkdirs()
            pdfDirectory.mkdirs()
        }
    }

    private class UnconfinedDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
