package com.viewer.presenter.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.PDFDocument
import com.viewer.PDFCore
import com.viewer.presenter.pager.PagerState
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState
import com.viewer.presenter.pager.zoomable.zoomable
import kotlinx.coroutines.*
import kotlin.math.min

@Composable
fun PdfSinglePage(
    pdfFile: PdfReaderPage.PdfFile,
    readerState: PdfReaderState,
    position: Int,
) {
    val scope = rememberCoroutineScope()
    val state = rememberPdfPageState(scope)
    var core by remember { mutableStateOf<PDFCore?>(null) }

    DisposableEffect(pdfFile) {
        val job = scope.launch(Dispatchers.IO) {
            val document = PDFDocument.openDocument(pdfFile.file.absolutePath) as PDFDocument
            pdfFile.password?.let { document.authenticatePassword(it) }
            core = PDFCore(document)
        }
        onDispose {
            job.cancel()
            core?.destroy()
            state.clear() // TODO needed?
            core = null
        }
    }

    val currentCore = core
    if (currentCore != null) {
        Box(contentAlignment = Alignment.Center) {
            PdfEntireSinglePage(
                state = state,
                readerState = readerState,
                core = currentCore,
                position = position
            )
            PdfZoomedSinglePage(
                state = state,
                readerState = readerState,
                core = currentCore
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfEntireSinglePage(
    state: PdfPageState,
    readerState: PdfReaderState,
    core: PDFCore,
    position: Int,
    pageToLoad: Int = 0
) {
    LaunchedEffect(core) {
        val bitmap = withContext(Dispatchers.IO) {
            val pdfPageSize = core.getPageSize(pageToLoad)
            val readerSize = readerState.readerSize

            val aspectRatioReader = readerSize.width / readerSize.height
            val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

            val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                readerSize.width to (readerSize.width / aspectRatioPage).toInt()
            } else {
                (readerSize.height * aspectRatioPage).toInt() to readerSize.height
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                core.drawPage(this, pageToLoad, width, height, 0, 0, Cookie())
            }
        }
        state.updateEntireBitmap(bitmap)
    }

    val currentBitmap = state.entireBitmap
    val currentZoomState = state.zoomState
    if (currentBitmap != null && currentZoomState != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .background(Color.Gray)
                .fillMaxSize()
                .zoomable(currentZoomState)
        )

        // Reset zoom state when the page is moved out of the window.
        val isVisible = position == readerState.pagerState.settledPage
        LaunchedEffect(isVisible) {
            if (!isVisible) {
                currentZoomState.reset()
            }
        }
    }
}

@Composable
fun PdfZoomedSinglePage(
    state: PdfPageState,
    readerState: PdfReaderState,
    core: PDFCore,
    pageToLoad: Int = 0
) {
    val currentZoomState = state.zoomState ?: return
    val currentEntireBitmap = state.entireBitmap ?: return
    if (currentZoomState.scale <= 1f) return

    DisposableEffect(currentZoomState.isSettled) {
        var job: Job? = null
        if (currentZoomState.isSettled && currentZoomState.scale > 1f) {

            val scaledWidth = (currentEntireBitmap.width * currentZoomState.scale).toInt()
            val scaledHeight = (currentEntireBitmap.height * currentZoomState.scale).toInt()

            val width = min(scaledWidth, readerState.readerSize.width)
            val height = min(scaledHeight, readerState.readerSize.height)

            val posX = currentZoomState.boundsX - currentZoomState.offsetX
            val posY = currentZoomState.boundsY - currentZoomState.offsetY

            job = state.scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    synchronized(core) {
                        state.getZoomedBitmap(width, height).apply {
                            core.drawPage(
                                this,
                                pageToLoad,
                                scaledWidth,
                                scaledHeight,
                                posX.toInt(),
                                posY.toInt(),
                                Cookie()
                            )
                        }
                    }
                }

                if (isActive) {
                    state.updateZoomedBitmap(bitmap)
                }
            }
        }
        onDispose {
            job?.cancel()
            state.disposeZoomedBitmap()
        }
    }

    val currentZoomedBitmap = state.zoomedBitmap
    if (currentZoomedBitmap != null) {
        Image(
            bitmap = currentZoomedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun PdfDoublePage(
    pdfFile1: PdfReaderPage.PdfFile?,
    pdfFile2: PdfReaderPage.PdfFile?,
) {

}

/**
 * Determine if the page is visible.
 *
 * @param page Page index to be determined.
 * @return true if the page is visible.
 */
fun PagerState.isVisibleForPage(page: Int): Boolean {
    val offset = (currentPage - page) + currentPageOffset
    return (-1.0f < offset) and (offset < 1.0f)
}