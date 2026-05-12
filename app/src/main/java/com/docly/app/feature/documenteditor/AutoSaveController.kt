package com.docly.app.feature.documenteditor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutoSaveController(private val saveDelayMs: Long = DEFAULT_SAVE_DELAY_MS) {
    private var saveJob: Job? = null

    fun schedule(scope: CoroutineScope, save: suspend () -> Unit) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDelayMs)
            save()
        }
    }

    fun cancel() {
        saveJob?.cancel()
        saveJob = null
    }

    companion object {
        const val DEFAULT_SAVE_DELAY_MS = 1500L
    }
}
