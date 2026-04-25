package com.docly.app.app.navigation

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
import com.docly.app.feature.editor.EditorScreen
import com.docly.app.feature.editor.EditorViewModel
import com.docly.app.feature.export.ExportScreen
import com.docly.app.feature.export.ExportViewModel
import com.docly.app.feature.library.LibraryScreen
import com.docly.app.feature.library.LibraryViewModel
import com.docly.app.feature.metadata.MetadataScreen
import com.docly.app.feature.metadata.MetadataViewModel
import com.docly.app.feature.review.ReviewScreen
import com.docly.app.feature.review.ReviewUiEffect
import com.docly.app.feature.review.ReviewViewModel
import com.docly.app.feature.scanner.ScannerScreen
import com.docly.app.feature.scanner.ScannerUiEffect
import com.docly.app.feature.scanner.ScannerViewModel

@Composable
fun AppNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = ScannerRoute,
        modifier = modifier
    ) {
        composable<ScannerRoute> {
            val viewModel = hiltViewModel<ScannerViewModel>()
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
                            navController.navigate(ScannerRoute) {
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
            val route = backStackEntry.toRoute<EditorRoute>()
            EditorScreen(
                uiState = uiState,
                onEditMetadata = {
                    navController.navigate(MetadataRoute(route.sessionId))
                },
                onNavigateBack = navController::popBackStack
            )
        }

        composable<MetadataRoute> { backStackEntry ->
            hiltViewModel<MetadataViewModel>(backStackEntry)
            val route = backStackEntry.toRoute<MetadataRoute>()
            MetadataScreen(
                sessionId = route.sessionId,
                onExport = {
                    navController.navigate(ExportRoute(route.sessionId))
                },
                onNavigateBack = navController::popBackStack
            )
        }

        composable<ExportRoute> { backStackEntry ->
            hiltViewModel<ExportViewModel>(backStackEntry)
            val route = backStackEntry.toRoute<ExportRoute>()
            ExportScreen(
                sessionId = route.sessionId,
                onViewLibrary = {
                    navController.navigate(LibraryRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateBack = navController::popBackStack
            )
        }

        composable<LibraryRoute> {
            val viewModel = hiltViewModel<LibraryViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = uiState,
                onStartScanner = {
                    navController.navigate(ScannerRoute) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
