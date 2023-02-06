package com.viewer.presenter.components

import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

@Composable
private fun PdfEntireSinglePage(
    state: PdfPageState,
    readerState: PdfReaderState,
    core: PDFCore,
    position: Int,
    pageToLoad: Int = 0
) {
    DisposableEffect(core) {
        val job = state.scope.launch(Dispatchers.IO) {
            val pdfPageSize = core.getPageSize(pageToLoad)
            val aspectRatio = pdfPageSize.x / pdfPageSize.y
            val readerSize = readerState.readerSize
            val (width, height) = (readerSize.width to (readerSize.width / aspectRatio).toInt())

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            core.drawPage(bitmap, pageToLoad, width, height, 0, 0, Cookie())

            state.updateEntireBitmap(bitmap)
        }
        onDispose {
            job.cancel()
            state.disposeEntireBitmap()
        }
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
        val isVisible by remember {
            derivedStateOf {
                readerState.pagerState.isVisibleForPage(position)
            }
        }
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
        val job = state.scope.launch(Dispatchers.IO) {
            if (currentZoomState.isSettled && currentZoomState.scale > 1f) {

                val width = min(
                    (currentEntireBitmap.width * currentZoomState.scale).toInt(),
                    readerState.readerSize.width
                )
                val height = min(
                    (currentEntireBitmap.height * currentZoomState.scale).toInt(),
                    readerState.readerSize.height
                )

                val bitmap = state.getZoomedBitmap(width, height)

                val posX = currentZoomState.boundsX - currentZoomState.offsetX
                val posY = currentZoomState.boundsY - currentZoomState.offsetY

                core.drawPage(
                    bitmap,
                    pageToLoad,
                    (currentEntireBitmap.width * currentZoomState.scale).toInt(),
                    (currentEntireBitmap.height * currentZoomState.scale).toInt(),
                    posX.toInt(),
                    posY.toInt(),
                    Cookie()
                )

                if (isActive) {
                    state.updateZoomedBitmap(bitmap)
                }
            }
        }
        onDispose {
            /*
            if (zoomState.isDragInProgress) {
                zoomedBitmap?.recycle()
                zoomedBitmap = null
            }
             */
            job.cancel()
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