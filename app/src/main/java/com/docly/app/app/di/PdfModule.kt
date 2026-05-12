package com.docly.app.app.di

import com.docly.app.core.pdf.AndroidHtmlToPdfExporter
import com.docly.app.core.pdf.AndroidPdfGenerator
import com.docly.app.core.pdf.HtmlToPdfExporter
import com.docly.app.core.pdf.PdfGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfModule {
    @Binds
    @Singleton
    abstract fun bindPdfGenerator(impl: AndroidPdfGenerator): PdfGenerator

    @Binds
    @Singleton
    abstract fun bindHtmlToPdfExporter(impl: AndroidHtmlToPdfExporter): HtmlToPdfExporter
}
