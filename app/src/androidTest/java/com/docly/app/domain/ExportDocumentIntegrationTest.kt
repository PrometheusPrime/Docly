package com.docly.app.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AndroidAppFileDirectories
import com.docly.app.core.image.AndroidBitmapLoader
import com.docly.app.core.pdf.AndroidPdfGenerator
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.local.db.AppDatabase
import com.docly.app.data.repository.DocumentRepositoryImpl
import com.docly.app.data.repository.FileRepositoryImpl
import com.docly.app.data.repository.PdfRepositoryImpl
import com.docly.app.data.repository.ScanRepositoryImpl
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.export.ExportDocumentUseCase
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.export.PrepareExportUseCase
import com.docly.app.domain.usecase.export.SaveDocumentUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanMetadataUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportDocumentIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportCreatesPdfPersistsSavedDocumentAndMarksSessionExported() = runBlocking {
        val dispatcherProvider = UnconfinedDispatcherProvider()
        val idProvider = SequenceIdProvider(listOf(SESSION_ID, DOCUMENT_ID))
        val timeProvider = FixedTimeProvider()
        val fileRepository = FileRepositoryImpl(AndroidAppFileDirectories(context), dispatcherProvider)
        val scanRepository = ScanRepositoryImpl(
            database = database,
            scanSessionDao = database.scanSessionDao(),
            scannedPageDao = database.scannedPageDao(),
            idProvider = idProvider,
            timeProvider = timeProvider,
            dispatcherProvider = dispatcherProvider,
            fileRepository = fileRepository
        )
        val documentRepository = DocumentRepositoryImpl(
            savedDocumentDao = database.savedDocumentDao(),
            dispatcherProvider = dispatcherProvider,
            fileRepository = fileRepository
        )
        val pdfRepository = PdfRepositoryImpl(
            AndroidPdfGenerator(
                bitmapLoader = AndroidBitmapLoader(dispatcherProvider),
                dispatcherProvider = dispatcherProvider
            )
        )

        val session = scanRepository.createSession(ScanMode.DOCUMENT).successData()
        val metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1"
        )
        UpdateScanMetadataUseCase(scanRepository)(session.id, metadata).assertSuccess()
        val processedImagePath = fileRepository.createProcessedImagePath(session.id, "page-1")
        writeFixtureImage(processedImagePath)
        val thumbnailPath = fileRepository.createThumbnailPath(session.id, "page-1")
        writeFixtureImage(thumbnailPath)
        scanRepository.addPage(
            ScannedPage(
                id = "page-id",
                sessionId = session.id,
                pageIndex = 0,
                originalImagePath = processedImagePath,
                processedImagePath = processedImagePath,
                thumbnailPath = thumbnailPath,
                rotationDegrees = 0,
                scanMode = ScanMode.DOCUMENT,
                width = 240,
                height = 320,
                createdAt = 10L
            )
        ).assertSuccess()

        val result = ExportDocumentUseCase(
            prepareExportUseCase = PrepareExportUseCase(
                getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
                generateDocumentNameUseCase = GenerateDocumentNameUseCase(),
                validateMetadataUseCase = ValidateMetadataUseCase(timeProvider)
            ),
            generatePdfUseCase = GeneratePdfUseCase(pdfRepository, fileRepository),
            saveDocumentUseCase = SaveDocumentUseCase(documentRepository),
            updateScanSessionStatusUseCase = UpdateScanSessionStatusUseCase(scanRepository),
            deleteSavedDocumentUseCase = DeleteSavedDocumentUseCase(documentRepository),
            fileRepository = fileRepository,
            idProvider = idProvider,
            timeProvider = timeProvider
        )(session.id)

        val document = result.successData().document
        assertEquals(DOCUMENT_ID, document.id)
        assertEquals("grade_10_math_2026_past_paper_1", document.title)
        assertEquals(1, document.pageCount)
        assertEquals(document, documentRepository.getDocument(DOCUMENT_ID).successData())
        assertEquals(ScanSessionStatus.EXPORTED, scanRepository.getSession(session.id).successData()?.status)
        assertTrue(File(document.pdfPath).isFile)
        File(document.pdfPath).openRenderer().use { renderer ->
            assertEquals(1, renderer.pageCount)
        }
    }

    private fun writeFixtureImage(path: String) {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        File(path).apply {
            parentFile?.mkdirs()
            outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            }
        }
        bitmap.recycle()
    }

    private fun File.openRenderer(): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor)
    }

    private fun AppResult<Unit>.assertSuccess() {
        assertTrue(this is AppResult.Success)
    }

    private fun <T> AppResult<T>.successData(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> throw AssertionError("Expected success, got $this")
    }

    private class SequenceIdProvider(ids: List<String>) : IdProvider {
        private val iterator = ids.iterator()

        override fun generateId(): String {
            check(iterator.hasNext()) { "No more test IDs are available." }
            return iterator.next()
        }
    }

    private class FixedTimeProvider : TimeProvider {
        override fun now(): Long = 1_767_225_600_000L
    }

    private class UnconfinedDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val SESSION_ID = "session-id"
        const val DOCUMENT_ID = "document-id"
        const val JPEG_QUALITY = 95
    }
}
