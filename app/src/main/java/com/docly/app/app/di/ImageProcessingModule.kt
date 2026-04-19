package com.docly.app.app.di

import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.ImageEnhancer
import com.docly.app.core.image.NotImplementedDocumentDetector
import com.docly.app.core.image.NotImplementedImageEnhancer
import com.docly.app.core.image.NotImplementedPerspectiveTransformer
import com.docly.app.core.image.NotImplementedThumbnailGenerator
import com.docly.app.core.image.PerspectiveTransformer
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
    abstract fun bindDocumentDetector(impl: NotImplementedDocumentDetector): DocumentDetector

    @Binds
    @Singleton
    abstract fun bindPerspectiveTransformer(impl: NotImplementedPerspectiveTransformer): PerspectiveTransformer

    @Binds
    @Singleton
    abstract fun bindImageEnhancer(impl: NotImplementedImageEnhancer): ImageEnhancer

    @Binds
    @Singleton
    abstract fun bindThumbnailGenerator(impl: NotImplementedThumbnailGenerator): ThumbnailGenerator
}
