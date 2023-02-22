package com.viewer.pdf.page.single

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    onLinkClick: (String) -> Unit
) {
    var state by remember { mutableStateOf<PdfSinglePageState?>(null) }

    DisposableEffect(pdfFile) {
        val scope = MainScope()
        scope.launch {
            val core = withContext(Dispatchers.IO) {
                PdfCore(pdfFile.file, pdfFile.password)
            }
            state = PdfSinglePageState(
                scope = scope,
                core = core,
                readerState = readerState,
                onLinkClick = onLinkClick
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
            modifier = Modifier
                .fillMaxSize()
        )
    }
}