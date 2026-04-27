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
import androidx.navigation.toRoute
import com.docly.app.core.file.PdfIntentFactory
import com.docly.app.core.result.AppResult
import com.docly.app.feature.editor.EditorScreen
import com.docly.app.feature.editor.EditorUiEffect
import com.docly.app.feature.editor.EditorViewModel
import com.docly.app.feature.export.ExportScreen
import com.docly.app.feature.export.ExportUiEffect
import com.docly.app.feature.export.ExportViewModel
import com.docly.app.feature.library.LibraryScreen
import com.docly.app.feature.library.LibraryViewModel
import com.docly.app.feature.metadata.MetadataScreen
import com.docly.app.feature.metadata.MetadataUiEffect
import com.docly.app.feature.metadata.MetadataViewModel
import com.docly.app.feature.review.ReviewScreen
import com.docly.app.feature.review.ReviewUiEffect
import com.docly.app.feature.review.ReviewViewModel
import com.docly.app.feature.scanner.ScannerScreen
import com.docly.app.feature.scanner.ScannerUiEffect
import com.docly.app.feature.scanner.ScannerViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    pdfIntentFactory: PdfIntentFactory
) {
    NavHost(
        navController = navController,
        startDestination = ScannerRoute(),
        modifier = modifier
    ) {
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
            val viewModel = hiltViewModel<ReviewViewModel>(backStackEntry)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val route = backStackEntry.toRoute<ReviewRoute>()
            val context = LocalContext.current
            LaunchedEffect(viewModel) {
                viewModel.uiEffect.collect { effect ->
                    when (effect) {
                        is ReviewUiEffect.NavigateBackToScanner -> {
                            navController.navigate(ScannerRoute(effect.sessionId)) {
                                launchSingleTop = true
                            }
                        }

                        is ReviewUiEffect.NavigateToEditor -> {
                            navController.navigate(EditorRoute(effect.sessionId))
                        }

                        is ReviewUiEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ReviewScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onEditPages = {
                    navController.navigate(EditorRoute(route.sessionId))
                },
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
                            context.startPdfIntent(
                                intentResult = pdfIntentFactory.createOpenIntent(effect.pdfPath),
                                noHandlerMessage = "No PDF viewer is available."
                            )
                        }

                        is ExportUiEffect.SharePdf -> {
                            context.startPdfIntent(
                                intentResult = pdfIntentFactory.createShareIntent(
                                    pdfPath = effect.pdfPath,
                                    title = effect.title
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

        composable<LibraryRoute> {
            val viewModel = hiltViewModel<LibraryViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = uiState,
                onStartScanner = {
                    navController.navigate(ScannerRoute()) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private fun Context.startPdfIntent(intentResult: AppResult<Intent>, noHandlerMessage: String) {
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
