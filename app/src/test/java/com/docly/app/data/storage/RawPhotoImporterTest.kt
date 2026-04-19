package com.docly.app.data.storage

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.data.repository.FileRepositoryImpl
import com.docly.app.data.repository.RepositoryFailure
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RawPhotoImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun importCopiesSourceBytesAndUsesMimeExtension() = runBlocking {
        val appFilesRoot = temporaryFolder.newFolder("docly-files")
        val importer = importer(
            appFilesRoot = appFilesRoot,
            rawPhotoSource = FakeRawPhotoSource(
                streamsByUri = mapOf("content://photo" to { ByteArrayInputStream(PNG_BYTES) }),
                mimeTypesByUri = mapOf("content://photo" to "image/png")
            ),
            imageBoundsReader = FakeImageBoundsReader(ImageBounds(width = 640, height = 480))
        )

        val importedImage = importer.importRawPhoto(
            sessionId = "Session ID",
            pageId = "Page 1",
            sourceUri = "content://photo"
        )

        assertTrue(importedImage.path.endsWith("raw_session_id_page_1.png"))
        assertEquals(640, importedImage.width)
        assertEquals(480, importedImage.height)
        assertArrayEquals(PNG_BYTES, File(importedImage.path).readBytes())
    }

    @Test
    fun importReturnsStorageFailureWhenInputStreamIsMissing() = runBlocking {
        val appFilesRoot = temporaryFolder.newFolder("docly-files")
        val importer = importer(
            appFilesRoot = appFilesRoot,
            rawPhotoSource = FakeRawPhotoSource(
                streamsByUri = mapOf("content://missing" to { null }),
                mimeTypesByUri = mapOf("content://missing" to "image/jpeg")
            ),
            imageBoundsReader = FakeImageBoundsReader(ImageBounds(width = 100, height = 100))
        )

        val failure = repositoryFailure {
            importer.importRawPhoto(
                sessionId = "session",
                pageId = "page",
                sourceUri = "content://missing"
            )
        }

        assertEquals(AppErrorCategory.STORAGE, failure.category)
        assertTrue(rawFiles(appFilesRoot).isEmpty())
    }

    @Test
    fun importDeletesPartialOutputWhenStreamReadFails() = runBlocking {
        val appFilesRoot = temporaryFolder.newFolder("docly-files")
        val importer = importer(
            appFilesRoot = appFilesRoot,
            rawPhotoSource = FakeRawPhotoSource(
                streamsByUri = mapOf("content://broken" to { ThrowingInputStream() }),
                mimeTypesByUri = mapOf("content://broken" to "image/jpeg")
            ),
            imageBoundsReader = FakeImageBoundsReader(ImageBounds(width = 100, height = 100))
        )

        val failure = repositoryFailure {
            importer.importRawPhoto(
                sessionId = "session",
                pageId = "page",
                sourceUri = "content://broken"
            )
        }

        assertEquals(AppErrorCategory.STORAGE, failure.category)
        assertTrue(rawFiles(appFilesRoot).isEmpty())
    }

    @Test
    fun importDeletesCopiedOutputWhenImageBoundsAreInvalid() = runBlocking {
        val appFilesRoot = temporaryFolder.newFolder("docly-files")
        val importer = importer(
            appFilesRoot = appFilesRoot,
            rawPhotoSource = FakeRawPhotoSource(
                streamsByUri = mapOf("content://invalid" to { ByteArrayInputStream(PNG_BYTES) }),
                mimeTypesByUri = mapOf("content://invalid" to "image/png")
            ),
            imageBoundsReader = FakeImageBoundsReader(bounds = null)
        )

        val failure = repositoryFailure {
            importer.importRawPhoto(
                sessionId = "session",
                pageId = "page",
                sourceUri = "content://invalid"
            )
        }

        assertEquals(AppErrorCategory.VALIDATION, failure.category)
        assertTrue(rawFiles(appFilesRoot).isEmpty())
    }

    @Test
    fun importFallsBackToJpgExtensionForUnknownMimeType() = runBlocking {
        val appFilesRoot = temporaryFolder.newFolder("docly-files")
        val importer = importer(
            appFilesRoot = appFilesRoot,
            rawPhotoSource = FakeRawPhotoSource(
                streamsByUri = mapOf("content://photo" to { ByteArrayInputStream(PNG_BYTES) }),
                mimeTypesByUri = mapOf("content://photo" to "text/plain")
            ),
            imageBoundsReader = FakeImageBoundsReader(ImageBounds(width = 100, height = 100))
        )

        val importedImage = importer.importRawPhoto(
            sessionId = "session",
            pageId = "page",
            sourceUri = "content://photo"
        )

        assertTrue(importedImage.path.endsWith("raw_session_page.jpg"))
    }

    private fun importer(
        appFilesRoot: File,
        rawPhotoSource: RawPhotoSource,
        imageBoundsReader: ImageBoundsReader
    ): RawPhotoImporter = RawPhotoImporter(
        fileRepository = FileRepositoryImpl(
            appFileDirectories = TempAppFileDirectories(appFilesRoot),
            dispatcherProvider = UnconfinedDispatcherProvider()
        ),
        rawPhotoSource = rawPhotoSource,
        imageBoundsReader = imageBoundsReader
    )

    private fun rawFiles(appFilesRoot: File): List<File> =
        File(appFilesRoot, "scans/raw").listFiles().orEmpty().toList()

    private suspend fun repositoryFailure(block: suspend () -> Unit): RepositoryFailure = try {
        block()
        fail("Expected RepositoryFailure.")
        error("Unreachable.")
    } catch (failure: RepositoryFailure) {
        failure
    }

    private class FakeRawPhotoSource(
        private val streamsByUri: Map<String, () -> InputStream?>,
        private val mimeTypesByUri: Map<String, String?>
    ) : RawPhotoSource {
        override fun openInputStream(sourceUri: String): InputStream? = streamsByUri[sourceUri]?.invoke()

        override fun mimeType(sourceUri: String): String? = mimeTypesByUri[sourceUri]
    }

    private class FakeImageBoundsReader(private val bounds: ImageBounds?) : ImageBoundsReader {
        override fun readBounds(path: String): ImageBounds? = bounds
    }

    private class ThrowingInputStream : InputStream() {
        private val delegate = ByteArrayInputStream(PNG_BYTES)
        private var bytesRead = 0

        override fun read(): Int {
            if (bytesRead > 2) {
                throw IOException("Read failed.")
            }
            bytesRead += 1
            return delegate.read()
        }
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

    private companion object {
        val PNG_BYTES = "fake-png-bytes".toByteArray()
    }
}
