package com.docly.app.domain.usecase.converter

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.ConversionResult
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.repository.ConverterRepository
import javax.inject.Inject

class GetSupportedConversionOutputsUseCase @Inject constructor(private val converterRepository: ConverterRepository) {
    operator fun invoke(inputType: DocumentType): List<DocumentType> =
        converterRepository.getSupportedOutputs(inputType)
}

class ConvertDocumentUseCase @Inject constructor(private val converterRepository: ConverterRepository) {
    suspend operator fun invoke(request: ConversionRequest): AppResult<ConversionResult> =
        converterRepository.convert(request)
}
