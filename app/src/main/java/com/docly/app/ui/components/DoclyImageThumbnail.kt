package com.docly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun DoclyImageThumbnail(
    imagePath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val taggedModifier = if (testTag != null) {
        modifier.testTag(testTag)
    } else {
        modifier
    }

    Box(
        modifier = taggedModifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (imagePath.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        this.contentDescription = contentDescription
                    }
            )
        } else {
            AsyncImage(
                model = File(imagePath),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
