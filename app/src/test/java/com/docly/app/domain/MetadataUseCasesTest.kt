package com.docly.app.domain

import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataUseCasesTest {
    private val fixedTimeProvider = object : TimeProvider {
        override fun now(): Long = GregorianCalendar(2026, Calendar.JANUARY, 1).timeInMillis
    }

    @Test
    fun validateMetadataAcceptsRequiredFieldsAndCurrentNextYear() {
        val metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Mathematics",
            year = 2027,
            paperType = "Past Paper"
        )

        val result = ValidateMetadataUseCase(fixedTimeProvider)(metadata)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validateMetadataRejectsMissingRequiredFieldsAndOutOfRangeYear() {
        val metadata = DocumentMetadata(
            grade = "",
            subject = "",
            year = 1979,
            paperType = ""
        )

        val result = ValidateMetadataUseCase(fixedTimeProvider)(metadata)

        assertFalse(result.isValid)
        assertEquals(4, result.errors.size)
    }

    @Test
    fun generateDocumentNameNormalizesUnsafeCharacters() {
        val metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math / Stats",
            year = 2026,
            paperType = "Paper 1",
            paperNumber = "A&B"
        )

        val fileName = GenerateDocumentNameUseCase()(metadata)

        assertEquals("grade_10_math_stats_2026_paper_1_a_b.pdf", fileName)
    }
}
