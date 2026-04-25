package com.docly.app.core.image

import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import org.opencv.android.OpenCVLoader

interface OpenCvInitializer {
    fun initialize(): AppResult<Unit>
}

@Singleton
class DefaultOpenCvInitializer @Inject constructor(private val logger: AppLogger) : OpenCvInitializer {
    @Volatile
    private var initialized = false

    override fun initialize(): AppResult<Unit> {
        if (initialized) return AppResult.Success(Unit)

        return synchronized(this) {
            if (initialized) {
                AppResult.Success(Unit)
            } else {
                try {
                    if (OpenCVLoader.initLocal()) {
                        initialized = true
                        logger.debug(TAG, "OpenCV initialized.")
                        AppResult.Success(Unit)
                    } else {
                        AppResult.Error(
                            message = OPEN_CV_UNAVAILABLE_MESSAGE,
                            category = AppErrorCategory.PROCESSING
                        )
                    }
                } catch (throwable: Throwable) {
                    AppResult.Error(
                        message = OPEN_CV_UNAVAILABLE_MESSAGE,
                        category = AppErrorCategory.PROCESSING,
                        throwable = throwable
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "OpenCvInitializer"
        const val OPEN_CV_UNAVAILABLE_MESSAGE = "Image processing is unavailable on this device."
    }
}
