package com.docly.app.app.di

import com.docly.app.data.repository.DevicePhotoRepositoryImpl
import com.docly.app.data.repository.DocumentRepositoryImpl
import com.docly.app.data.repository.FileRepositoryImpl
import com.docly.app.data.repository.ImageProcessingRepositoryImpl
import com.docly.app.data.repository.PdfRepositoryImpl
import com.docly.app.data.repository.ScanRepositoryImpl
import com.docly.app.domain.repository.DevicePhotoRepository
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
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

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
