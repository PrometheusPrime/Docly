package com.docly.app.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.docly.app.app.navigation.AppNavHost
import com.docly.app.core.file.DocumentIntentFactory
import com.docly.app.ui.theme.DoclyTheme

@Composable
fun DoclyApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    documentIntentFactory: DocumentIntentFactory? = null
) {
    val context = LocalContext.current
    val resolvedDocumentIntentFactory = documentIntentFactory ?: remember(context) {
        DocumentIntentFactory(context.applicationContext)
    }

    DoclyTheme {
        AppNavHost(
            navController = navController,
            documentIntentFactory = resolvedDocumentIntentFactory,
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoclyAppPreview() {
    DoclyApp()
}
