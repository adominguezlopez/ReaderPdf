package com.viewer.pdf.single

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.artifex.mupdf.fitz.PDFDocument
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.zoomable.zoomable
import kotlinx.coroutines.*

@OptIn(ExperimentalFoundationApi::class)
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
                val document = PDFDocument.openDocument(pdfFile.file.absolutePath) as PDFDocument
                pdfFile.password?.let { document.authenticatePassword(it) }
                PdfCore(document)
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
    if (currentState != null) {
        Box(contentAlignment = Alignment.Center) {
            PdfEntireSinglePage(state = currentState)
            PdfZoomedSinglePage(state = currentState)
        }

        // Reset zoom state when the page is moved out of the window.
        val isVisible = position == currentState.readerState.pagerState.settledPage
        LaunchedEffect(isVisible) {
            if (!isVisible) {
                currentState.zoomState.reset()
            }
        }
    }
}

@Composable
private fun PdfEntireSinglePage(state: PdfSinglePageState) {
    val entireBitmap = state.entireBitmap
    val currentZoomState = state.zoomState
    if (entireBitmap != null) {
        Image(
            bitmap = entireBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = currentZoomState,
                    onTap = state::handleClick
                )
        )
    }
}

@Composable
fun PdfZoomedSinglePage(state: PdfSinglePageState) {
    val zoomState = state.zoomState
    if (zoomState.scale <= 1f) return

    DisposableEffect(zoomState.isSettled) {
        val job = state.refreshZoomedContent()
        onDispose {
            job?.cancel()
            state.clearZoomedContent()
        }
    }

    val zoomedBitmap = state.zoomedBitmap
    if (zoomedBitmap != null) {
        Image(
            bitmap = zoomedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit
        )
    }
}
