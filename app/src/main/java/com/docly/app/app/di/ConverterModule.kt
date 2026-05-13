package com.docly.app.app.di

import com.docly.app.data.converter.DocxDocumentConverterEngine
import com.docly.app.data.converter.HtmlDocumentConverterEngine
import com.docly.app.data.converter.ImageDocumentConverterEngine
import com.docly.app.data.converter.MarkdownDocumentConverterEngine
import com.docly.app.data.converter.TextDocumentConverterEngine
import com.docly.app.data.converter.XlsxDocumentConverterEngine
import com.docly.app.domain.converter.ConverterEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConverterModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindTextDocumentConverterEngine(impl: TextDocumentConverterEngine): ConverterEngine

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMarkdownDocumentConverterEngine(impl: MarkdownDocumentConverterEngine): ConverterEngine

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindHtmlDocumentConverterEngine(impl: HtmlDocumentConverterEngine): ConverterEngine

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindImageDocumentConverterEngine(impl: ImageDocumentConverterEngine): ConverterEngine

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindDocxDocumentConverterEngine(impl: DocxDocumentConverterEngine): ConverterEngine

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindXlsxDocumentConverterEngine(impl: XlsxDocumentConverterEngine): ConverterEngine
}
