package com.docly.app.app.di

import com.docly.app.data.repository.CleanupRepositoryImpl
import com.docly.app.data.repository.ConverterRepositoryImpl
import com.docly.app.data.repository.DevicePhotoRepositoryImpl
import com.docly.app.data.repository.DiagnosticsRepositoryImpl
import com.docly.app.data.repository.DocumentRepositoryImpl
import com.docly.app.data.repository.FileRepositoryImpl
import com.docly.app.data.repository.ImageProcessingRepositoryImpl
import com.docly.app.data.repository.PdfRepositoryImpl
import com.docly.app.data.repository.ScanRepositoryImpl
import com.docly.app.data.storage.AndroidDoclyStorageManager
import com.docly.app.data.storage.DoclyStorageManager
import com.docly.app.domain.repository.CleanupRepository
import com.docly.app.domain.repository.ConverterRepository
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.DiagnosticsRepository
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindScanRepository(impl: ScanRepositoryImpl): ScanRepository

    @Binds
    @Singleton
    abstract fun bindCleanupRepository(impl: CleanupRepositoryImpl): CleanupRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindConverterRepository(impl: ConverterRepositoryImpl): ConverterRepository

    @Binds
    @Singleton
    abstract fun bindDoclyStorageManager(impl: AndroidDoclyStorageManager): DoclyStorageManager

    @Binds
    @Singleton
    abstract fun bindImageProcessingRepository(impl: ImageProcessingRepositoryImpl): ImageProcessingRepository

    @Binds
    @Singleton
    abstract fun bindPdfRepository(impl: PdfRepositoryImpl): PdfRepository

    @Binds
    @Singleton
    abstract fun bindDevicePhotoRepository(impl: DevicePhotoRepositoryImpl): DevicePhotoRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository
}
