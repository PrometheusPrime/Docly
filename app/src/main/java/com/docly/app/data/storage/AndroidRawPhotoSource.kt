package com.docly.app.data.storage

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

internal class AndroidRawPhotoSource(private val contentResolver: ContentResolver) : RawPhotoSource {
    override fun openInputStream(sourceUri: String): InputStream? =
        contentResolver.openInputStream(Uri.parse(sourceUri))

    override fun mimeType(sourceUri: String): String? = contentResolver.getType(Uri.parse(sourceUri))
}

internal object BitmapFactoryImageBoundsReader : ImageBoundsReader {
    override fun readBounds(path: String): ImageBounds? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageBounds(width = options.outWidth, height = options.outHeight)
        } else {
            null
        }
    }
}
