package com.docly.app.data.storage

import android.content.ContentResolver
import android.net.Uri
import com.docly.app.core.image.readOrientedImageDimensions
import java.io.InputStream

internal class AndroidRawPhotoSource(private val contentResolver: ContentResolver) : RawPhotoSource {
    override fun openInputStream(sourceUri: String): InputStream? =
        contentResolver.openInputStream(Uri.parse(sourceUri))

    override fun mimeType(sourceUri: String): String? = contentResolver.getType(Uri.parse(sourceUri))
}

internal object BitmapFactoryImageBoundsReader : ImageBoundsReader {
    override fun readBounds(path: String): ImageBounds? {
        val dimensions = readOrientedImageDimensions(path) ?: return null
        return ImageBounds(width = dimensions.width, height = dimensions.height)
    }
}
