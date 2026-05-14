package com.docly.app.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docly.app.core.file.DocumentIntentFactory
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var documentIntentFactory: DocumentIntentFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appSettings = settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            DoclyApp(
                documentIntentFactory = documentIntentFactory,
                appSettings = appSettings.value
            )
        }
    }
}
