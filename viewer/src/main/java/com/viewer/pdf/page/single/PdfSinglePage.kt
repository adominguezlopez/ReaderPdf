package com.viewer.pdf.page.single

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.page.PdfPage
import kotlinx.coroutines.*

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PdfSinglePage(
    pdfFile: PdfReaderPage.PdfFile,
    readerState: PdfReaderState,
    position: Int,
    onClick: (Offset, String?) -> Unit,
) {
    var state by remember { mutableStateOf<PdfSinglePageState?>(null) }

    DisposableEffect(pdfFile) {
        val scope = MainScope()
        scope.launch {
            val core = withContext(Dispatchers.IO) {
                PdfCore(pdfFile.file, pdfFile.passwords)
            }
            state = PdfSinglePageState(
                scope = scope,
                core = core,
                readerState = readerState,
                onClick = onClick,
            )
        }
        onDispose {
            scope.cancel()
            state?.dispose()
            state = null
        }
    }

    val currentState = state
    if (currentState?.entireBitmap != null) {
        PdfPage(currentState, position)
    } else if (pdfFile.thumbnail != null) {
        GlideImage(
            model = pdfFile.thumbnail,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
