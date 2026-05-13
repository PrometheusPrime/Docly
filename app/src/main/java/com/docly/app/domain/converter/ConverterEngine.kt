package com.docly.app.domain.converter

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ConversionPair
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import javax.inject.Inject

interface ConverterEngine {
    val supportedPairs: Set<ConversionPair>

    suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput>
}

data class ConverterOutput(val outputPath: String, val mimeType: String?, val pageCount: Int? = null)

class ConverterRegistry @Inject constructor(private val engines: Set<@JvmSuppressWildcards ConverterEngine>) {
    fun getSupportedOutputs(inputType: DocumentType): List<DocumentType> = engines
        .flatMap { engine -> engine.supportedPairs }
        .filter { pair -> pair.input == inputType }
        .map { pair -> pair.output }
        .distinct()
        .sortedBy { type -> OUTPUT_ORDER.indexOf(type).takeIf { it >= 0 } ?: Int.MAX_VALUE }

    fun findEngine(input: DocumentType, output: DocumentType): ConverterEngine? = engines.firstOrNull { engine ->
        engine.supportedPairs.any { pair -> pair.input == input && pair.output == output }
    }

    private companion object {
        val OUTPUT_ORDER = listOf(
            DocumentType.PDF,
            DocumentType.HTML,
            DocumentType.MARKDOWN,
            DocumentType.TXT,
            DocumentType.CSV
        )
    }
}
