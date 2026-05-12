package com.docly.app.feature.create

import com.docly.app.core.testing.FakeDoclyStorageManager
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.core.testing.SequenceIdProvider
import com.docly.app.core.testing.TestDispatcherProvider
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.usecase.create.CreateDocumentUseCase
import com.docly.app.domain.usecase.create.DefaultDocumentContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createMarkdownDocumentNavigatesToEditor() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeDocumentRepository()
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val viewModel = CreateViewModel(
            createDocumentUseCase = CreateDocumentUseCase(
                storageManager = storageManager,
                documentRepository = repository,
                defaultDocumentContentFactory = DefaultDocumentContentFactory(),
                idProvider = SequenceIdProvider(listOf("created-id")),
                timeProvider = FixedTimeProvider(100L),
                dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)
            )
        )
        val effects = async { viewModel.uiEffect.take(2).toList() }

        viewModel.onEvent(CreateUiEvent.OnTitleChanged("Notes"))
        viewModel.onEvent(CreateUiEvent.OnTypeSelected(DocumentType.MARKDOWN))
        viewModel.onEvent(CreateUiEvent.OnCreateClicked)
        advanceUntilIdle()

        val emittedEffects = effects.await()
        assertEquals(DocumentType.MARKDOWN, repository.documents.single().type)
        assertTrue(emittedEffects.last() is CreateUiEffect.NavigateToEditor)
        assertEquals("created-id", (emittedEffects.last() as CreateUiEffect.NavigateToEditor).documentId)
    }

    class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
