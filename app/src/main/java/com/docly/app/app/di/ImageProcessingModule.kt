package com.docly.app.app.di

import com.docly.app.core.image.AndroidBitmapLoader
import com.docly.app.core.image.AndroidScanPageRenderer
import com.docly.app.core.image.AndroidScanQualityEvaluator
import com.docly.app.core.image.AndroidThumbnailGenerator
import com.docly.app.core.image.BitmapLoader
import com.docly.app.core.image.DefaultOpenCvInitializer
import com.docly.app.core.image.DefaultScanQualityScorer
import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.ImageEnhancer
import com.docly.app.core.image.OpenCvDocumentDetector
import com.docly.app.core.image.OpenCvImageEnhancer
import com.docly.app.core.image.OpenCvInitializer
import com.docly.app.core.image.OpenCvPerspectiveTransformer
import com.docly.app.core.image.PerspectiveTransformer
import com.docly.app.core.image.ScanPageRenderer
import com.docly.app.core.image.ScanQualityEvaluator
import com.docly.app.core.image.ScanQualityScorer
import com.docly.app.core.image.ThumbnailGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageProcessingModule {
    @Binds
    @Singleton
    abstract fun bindBitmapLoader(impl: AndroidBitmapLoader): BitmapLoader

    @Binds
    @Singleton
    abstract fun bindOpenCvInitializer(impl: DefaultOpenCvInitializer): OpenCvInitializer

    @Binds
    @Singleton
    abstract fun bindDocumentDetector(impl: OpenCvDocumentDetector): DocumentDetector

    @Binds
    @Singleton
    abstract fun bindPerspectiveTransformer(impl: OpenCvPerspectiveTransformer): PerspectiveTransformer

    @Binds
    @Singleton
    abstract fun bindImageEnhancer(impl: OpenCvImageEnhancer): ImageEnhancer

    @Binds
    @Singleton
    abstract fun bindScanQualityScorer(impl: DefaultScanQualityScorer): ScanQualityScorer

    @Binds
    @Singleton
    abstract fun bindScanQualityEvaluator(impl: AndroidScanQualityEvaluator): ScanQualityEvaluator

    @Binds
    @Singleton
    abstract fun bindScanPageRenderer(impl: AndroidScanPageRenderer): ScanPageRenderer

    @Binds
    @Singleton
    abstract fun bindThumbnailGenerator(impl: AndroidThumbnailGenerator): ThumbnailGenerator
}
