package com.docly.app.app.di

import com.docly.app.core.camera.CameraPreviewBinder
import com.docly.app.core.camera.CameraXPreviewBinder
import com.docly.app.core.scanner.DocumentScannerService
import com.docly.app.core.scanner.MlKitDocumentScannerService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {
    @Binds
    @Singleton
    abstract fun bindCameraPreviewBinder(impl: CameraXPreviewBinder): CameraPreviewBinder

    @Binds
    @Singleton
    abstract fun bindDocumentScannerService(impl: MlKitDocumentScannerService): DocumentScannerService
}
