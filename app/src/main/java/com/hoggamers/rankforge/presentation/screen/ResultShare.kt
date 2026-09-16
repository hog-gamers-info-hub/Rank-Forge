package com.hoggamers.rankforge.presentation.screen

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import kotlinx.coroutines.flow.Flow

data class ResultShareRequest(
    val uri: Uri,
    val format: ResultExportFileFormat,
    val displayName: String,
)

internal fun createResultShareChooserIntent(request: ResultShareRequest): Intent {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = request.format.mimeType
        putExtra(Intent.EXTRA_STREAM, request.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(request.displayName, request.uri)
    }
    return Intent.createChooser(shareIntent, null)
}

@Composable
internal fun ResultShareEventEffect(
    shareEvents: Flow<ResultShareRequest>,
) {
    val context = LocalContext.current
    LaunchedEffect(shareEvents) {
        shareEvents.collect { request ->
            runCatching {
                context.startActivity(createResultShareChooserIntent(request))
            }
        }
    }
}
