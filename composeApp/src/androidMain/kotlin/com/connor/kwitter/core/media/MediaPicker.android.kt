package com.connor.kwitter.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.koin.mp.KoinPlatform

@Composable
actual fun rememberMediaPickerLauncher(
    onResult: (List<SelectedMedia>) -> Unit
): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris: List<Uri> ->
        val media = uris.mapNotNull { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: return@mapNotNull null
            val name = queryFileName(context, uri) ?: "media"
            SelectedMedia(
                platformData = uri,
                name = name,
                mimeType = mimeType
            )
        }
        onResult(media)
    }

    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    }
}

actual suspend fun SelectedMedia.readBytes(): ByteArray {
    val uri = platformData as Uri
    val context = KoinPlatform.getKoin().get<Context>()
    return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Cannot read URI: $uri")
}
