package com.docly.app.app.di

import com.docly.app.core.reader.AndroidPdfReaderEngine
import com.docly.app.core.reader.DefaultDocxReaderEngine
import com.docly.app.core.reader.DefaultHtmlReaderEngine
import com.docly.app.core.reader.DefaultMarkdownReaderEngine
import com.docly.app.core.reader.DefaultTextReaderEngine
import com.docly.app.core.reader.DefaultXlsxReaderEngine
import com.docly.app.core.reader.DocxReaderEngine
import com.docly.app.core.reader.HtmlReaderEngine
import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.PdfReaderEngine
import com.docly.app.core.reader.TextReaderEngine
import com.docly.app.core.reader.XlsxReaderEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {
    @Binds
    @Singleton
    abstract fun bindPdfReaderEngine(impl: AndroidPdfReaderEngine): PdfReaderEngine

    @Binds
    @Singleton
    abstract fun bindTextReaderEngine(impl: DefaultTextReaderEngine): TextReaderEngine

    @Binds
    @Singleton
    abstract fun bindMarkdownReaderEngine(impl: DefaultMarkdownReaderEngine): MarkdownReaderEngine

    @Binds
    @Singleton
    abstract fun bindHtmlReaderEngine(impl: DefaultHtmlReaderEngine): HtmlReaderEngine

    @Binds
    @Singleton
    abstract fun bindDocxReaderEngine(impl: DefaultDocxReaderEngine): DocxReaderEngine

    @Binds
    @Singleton
    abstract fun bindXlsxReaderEngine(impl: DefaultXlsxReaderEngine): XlsxReaderEngine
}
