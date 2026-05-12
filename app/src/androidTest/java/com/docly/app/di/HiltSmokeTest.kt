package com.docly.app.di

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.app.di.CoreModule
import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ScanRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(CoreModule::class)
@RunWith(AndroidJUnit4::class)
class HiltSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var idProvider: IdProvider

    @Inject lateinit var timeProvider: TimeProvider

    @Inject lateinit var fileRepository: FileRepository

    @Inject lateinit var scanRepository: ScanRepository

    @Test
    fun coreProvidersCanBeOverriddenAndRepositoriesResolve() {
        hiltRule.inject()

        assertEquals("fixed-id", idProvider.generateId())
        assertEquals(1234L, timeProvider.now())
        assertTrue(fileRepository.createPdfPath("Test PDF").endsWith(".pdf"))
        assertNotNull(scanRepository)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object TestCoreModule {
        @Provides
        @Singleton
        fun provideDispatcherProvider(): DispatcherProvider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
        }

        @Provides
        @Singleton
        fun provideIdProvider(): IdProvider = object : IdProvider {
            override fun generateId(): String = "fixed-id"
        }

        @Provides
        @Singleton
        fun provideTimeProvider(): TimeProvider = object : TimeProvider {
            override fun now(): Long = 1234L
        }

        @Provides
        @Singleton
        fun provideAppLogger(): AppLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warning(tag: String, message: String, throwable: Throwable?) = Unit
            override fun error(tag: String, message: String, throwable: Throwable?) = Unit
        }

        @Provides
        @Singleton
        fun provideAppFileDirectories(@ApplicationContext context: Context): AppFileDirectories {
            val root = File(context.cacheDir, "docly-hilt-test")
            return object : AppFileDirectories {
                override val doclyRootDirectory: File = root
                override val rawScanDirectory: File = File(root, "scans/raw")
                override val processedScanDirectory: File = File(root, "scans/processed")
                override val thumbnailDirectory: File = File(root, "scans/thumbnails")
                override val pdfDirectory: File = File(root, "documents/pdf")
                override val txtDirectory: File = File(root, "documents/txt")
                override val markdownDirectory: File = File(root, "documents/markdown")
                override val htmlDirectory: File = File(root, "documents/html")
                override val docxDirectory: File = File(root, "documents/docx")
                override val xlsxDirectory: File = File(root, "documents/xlsx")
                override val csvDirectory: File = File(root, "documents/csv")
                override val imageDirectory: File = File(root, "documents/images")
                override val exportDirectory: File = File(root, "exports")
                override val tempDirectory: File = File(root, "temp")
                override val ocrDirectory: File = File(root, "ocr")

                override fun documentDirectory(type: DocumentType): File = when (type) {
                    DocumentType.PDF -> pdfDirectory
                    DocumentType.TXT -> txtDirectory
                    DocumentType.MARKDOWN -> markdownDirectory
                    DocumentType.HTML -> htmlDirectory
                    DocumentType.DOCX -> docxDirectory
                    DocumentType.XLSX -> xlsxDirectory
                    DocumentType.CSV -> csvDirectory
                    DocumentType.IMAGE -> imageDirectory
                    DocumentType.UNKNOWN -> tempDirectory
                }

                override fun ensureDirectories() {
                    listOf(
                        rawScanDirectory,
                        processedScanDirectory,
                        thumbnailDirectory,
                        pdfDirectory,
                        txtDirectory,
                        markdownDirectory,
                        htmlDirectory,
                        docxDirectory,
                        xlsxDirectory,
                        csvDirectory,
                        imageDirectory,
                        exportDirectory,
                        tempDirectory,
                        ocrDirectory
                    ).forEach { directory -> directory.mkdirs() }
                }
            }
        }
    }
}
