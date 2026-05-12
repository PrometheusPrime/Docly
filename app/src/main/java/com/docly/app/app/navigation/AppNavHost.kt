package com.docly.app.app.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.docly.app.core.file.DocumentIntentFactory
import com.docly.app.core.result.AppResult
import com.docly.app.feature.editor.EditorScreen
import com.docly.app.feature.editor.EditorUiEffect
import com.docly.app.feature.editor.EditorViewModel
import com.docly.app.feature.export.ExportScreen
import com.docly.app.feature.export.ExportUiEffect
import com.docly.app.feature.export.ExportViewModel
import com.docly.app.feature.home.HomeScreen
import com.docly.app.feature.library.LibraryScreen
import com.docly.app.feature.library.LibraryUiEffect
import com.docly.app.feature.library.LibraryViewModel
import com.docly.app.feature.metadata.MetadataScreen
import com.docly.app.feature.metadata.MetadataUiEffect
import com.docly.app.feature.metadata.MetadataViewModel
import com.docly.app.feature.placeholder.PlaceholderScreen
import com.docly.app.feature.reader.ReaderScreen
import com.docly.app.feature.reader.ReaderViewModel
import com.docly.app.feature.scanner.ScannerScreen
import com.docly.app.feature.scanner.ScannerUiEffect
import com.docly.app.feature.scanner.ScannerViewModel
import com.docly.app.feature.scanreview.ScanReviewScreen
import com.docly.app.feature.scanreview.ScanReviewUiEffect
import com.docly.app.feature.scanreview.ScanReviewViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    documentIntentFactory: DocumentIntentFactory
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> { backStackEntry ->
            val viewModel = hiltViewModel<LibraryViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                uiState = uiState,
                onOpenDocuments = {
                    navController.navigate(LibraryRoute) {
                        launchSingleTop = true
                    }
                },
                onStartScanner = {
                    navController.navigate(ScannerRoute()) {
                        launchSingleTop = true
                    }
                },
                onOpenCreate = { navController.navigate(CreateRoute) },
                onOpenTools = { navController.navigate(ToolsRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) }
            )
        }

        composable<ScannerRoute> { backStackEntry ->
            val viewModel = hiltViewModel<ScannerViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is ScannerUiEffect.NavigateToReview -> {
                            navController.navigate(ReviewRoute(effect.sessionId))
                        }

                        is ScannerUiEffect.NavigateToEditor -> {
                            navController.navigate(EditorRoute(effect.sessionId))
                        }

                        is ScannerUiEffect.NavigateToExport -> {
                            navController.navigate(ExportRoute(effect.sessionId))
                        }

                        is ScannerUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ScannerScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onOpenLibrary = {
                    navController.navigate(LibraryRoute) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ReviewRoute> { backStackEntry ->
            val viewModel = hiltViewModel<ScanReviewViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is ScanReviewUiEffect.NavigateToScanner -> {
                            navController.navigate(ScannerRoute(effect.sessionId)) {
                                launchSingleTop = true
                            }
                        }

                        ScanReviewUiEffect.NavigateToDocuments -> {
                            navController.navigate(LibraryRoute) {
                                launchSingleTop = true
                                popUpTo(HomeRoute)
                            }
                        }

                        is ScanReviewUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ScanReviewScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val viewModel = hiltViewModel<EditorViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is EditorUiEffect.NavigateToScanner -> {
                            navController.navigate(ScannerRoute(effect.sessionId))
                        }

                        is EditorUiEffect.NavigateToMetadata -> {
                            navController.navigate(MetadataRoute(effect.sessionId))
                        }

                        is EditorUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            EditorScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack
            )
        }

        composable<MetadataRoute> { backStackEntry ->
            val viewModel = hiltViewModel<MetadataViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is MetadataUiEffect.NavigateToExport -> {
                            navController.navigate(ExportRoute(effect.sessionId))
                        }

                        is MetadataUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            MetadataScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack
            )
        }

        composable<ExportRoute> { backStackEntry ->
            val viewModel = hiltViewModel<ExportViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is ExportUiEffect.NavigateToLibrary -> {
                            navController.navigate(LibraryRoute) {
                                launchSingleTop = true
                            }
                        }

                        is ExportUiEffect.OpenPdf -> {
                            context.startDocumentIntent(
                                intentResult = documentIntentFactory.createOpenIntent(
                                    filePath = effect.pdfPath,
                                    mimeType = "application/pdf"
                                ),
                                noHandlerMessage = "No PDF viewer is available."
                            )
                        }

                        is ExportUiEffect.SharePdf -> {
                            context.startDocumentIntent(
                                intentResult = documentIntentFactory.createShareIntent(
                                    filePath = effect.pdfPath,
                                    title = effect.title,
                                    mimeType = "application/pdf"
                                ),
                                noHandlerMessage = "No app is available to share this PDF."
                            )
                        }

                        is ExportUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ExportScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack
            )
        }

        composable<LibraryRoute> { backStackEntry ->
            val viewModel = hiltViewModel<LibraryViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        LibraryUiEffect.LaunchImportPicker -> Unit

                        is LibraryUiEffect.OpenDocument -> {
                            context.startDocumentIntent(
                                intentResult = documentIntentFactory.createOpenIntent(
                                    filePath = effect.filePath,
                                    mimeType = effect.mimeType
                                ),
                                noHandlerMessage = "No app is available to open this document."
                            )
                        }

                        is LibraryUiEffect.OpenReader -> {
                            navController.navigate(ReaderRoute(effect.documentId))
                        }

                        is LibraryUiEffect.ShareDocument -> {
                            context.startDocumentIntent(
                                intentResult = documentIntentFactory.createShareIntent(
                                    filePath = effect.filePath,
                                    title = effect.title,
                                    mimeType = effect.mimeType
                                ),
                                noHandlerMessage = "No app is available to share this document."
                            )
                        }

                        is LibraryUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            LibraryScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onStartScanner = {
                    navController.navigate(ScannerRoute()) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ReaderRoute> { backStackEntry ->
            val viewModel = hiltViewModel<ReaderViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ReaderScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack
            )
        }

        composable<SearchRoute> {
            PlaceholderScreen(
                title = "Search",
                message = "Document search is available from the Documents screen in this phase.",
                onNavigateBack = navController::popBackStack
            )
        }

        composable<CreateRoute> {
            PlaceholderScreen(
                title = "Create",
                message = "TXT, Markdown, and HTML creation are planned after the document foundation.",
                onNavigateBack = navController::popBackStack
            )
        }

        composable<ToolsRoute> {
            PlaceholderScreen(
                title = "Tools",
                message = "Conversion and PDF tools will appear here when those phases are implemented.",
                onNavigateBack = navController::popBackStack
            )
        }

        composable<SettingsRoute> {
            PlaceholderScreen(
                title = "Settings",
                message = "Document defaults and appearance settings are planned for a later phase.",
                onNavigateBack = navController::popBackStack
            )
        }
    }
}

private fun Context.startDocumentIntent(intentResult: AppResult<Intent>, noHandlerMessage: String) {
    when (intentResult) {
        is AppResult.Error -> {
            Toast.makeText(this, intentResult.message, Toast.LENGTH_SHORT).show()
        }

        is AppResult.Success -> {
            val intent = intentResult.data
            try {
                startActivity(intent)
            } catch (exception: ActivityNotFoundException) {
                Toast.makeText(this, noHandlerMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
