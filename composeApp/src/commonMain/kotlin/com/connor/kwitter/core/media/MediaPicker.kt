package com.connor.kwitter.core.media

import androidx.compose.runtime.Composable

data class SelectedMedia(
    val platformData: Any,
    val name: String,
    val mimeType: String
)

@Composable
expect fun rememberMediaPickerLauncher(
    onResult: (List<SelectedMedia>) -> Unit
): () -> Unit

expect suspend fun SelectedMedia.readBytes(): ByteArray
