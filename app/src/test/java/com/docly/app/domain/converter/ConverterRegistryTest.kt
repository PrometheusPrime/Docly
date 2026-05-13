package com.docly.app.domain.converter

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ConversionPair
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ConverterRegistryTest {
    @Test
    fun supportedOutputsAreDistinctAndOrdered() {
        val txtEngine = fakeEngine(
            ConversionPair(DocumentType.TXT, DocumentType.MARKDOWN),
            ConversionPair(DocumentType.TXT, DocumentType.PDF)
        )
        val duplicateEngine = fakeEngine(
            ConversionPair(DocumentType.TXT, DocumentType.PDF),
            ConversionPair(DocumentType.TXT, DocumentType.HTML)
        )
        val registry = ConverterRegistry(setOf(txtEngine, duplicateEngine))

        assertEquals(
            listOf(DocumentType.PDF, DocumentType.HTML, DocumentType.MARKDOWN),
            registry.getSupportedOutputs(DocumentType.TXT)
        )
    }

    @Test
    fun findEngineReturnsMatchingEngineOnly() {
        val txtEngine = fakeEngine(ConversionPair(DocumentType.TXT, DocumentType.PDF))
        val markdownEngine = fakeEngine(ConversionPair(DocumentType.MARKDOWN, DocumentType.HTML))
        val registry = ConverterRegistry(setOf(txtEngine, markdownEngine))

        assertSame(markdownEngine, registry.findEngine(DocumentType.MARKDOWN, DocumentType.HTML))
        assertNull(registry.findEngine(DocumentType.HTML, DocumentType.MARKDOWN))
    }

    private fun fakeEngine(vararg pairs: ConversionPair): ConverterEngine = object : ConverterEngine {
        override val supportedPairs: Set<ConversionPair> = pairs.toSet()

        override suspend fun convert(
            request: ConversionRequest,
            sourceDocument: DoclyDocument,
            outputPath: String
        ): AppResult<ConverterOutput> = AppResult.Success(ConverterOutput(outputPath, null))
    }
}
