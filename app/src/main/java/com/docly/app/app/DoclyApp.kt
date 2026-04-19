package com.docly.app.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.docly.app.app.navigation.AppNavHost
import com.docly.app.ui.theme.DoclyTheme

@Composable
fun DoclyApp(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    DoclyTheme {
        AppNavHost(
            navController = navController,
            modifier = modifier
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoclyAppPreview() {
    DoclyApp()
}
