package com.viewer.presenter.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.PDFDocument
import com.viewer.PDFCore
import com.viewer.presenter.pager.PagerScope
import com.viewer.presenter.pager.calculateCurrentOffsetForPage
import com.viewer.presenter.pager.pdf.PdfReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.viewer.presenter.pager.zoomable.rememberZoomState
import com.viewer.presenter.pager.zoomable.zoomable

@Composable
fun PdfPageUrl(
    url: String
) {
    AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = url,
        contentDescription = null
    )
}

@Composable
fun PdfSinglePage(
    pdfFile: PdfReaderPage.PdfFile,
    pagerState: PagerScope,
    position: Int
) {
    val page = 0
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val zoomState = rememberZoomState()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        DisposableEffect(pdfFile) {
            val job = scope.launch(Dispatchers.IO) {
                val core = run {
                    val document = PDFDocument.openDocument(pdfFile.file.absolutePath) as PDFDocument
                    document.authenticatePassword(pdfFile.password)
                    PDFCore(document)
                }

                val pageOriginalSize = core.getPageSize(page)
                val aspectRatio = pageOriginalSize.x / pageOriginalSize.y

                val (width, height) = with(density) {
                    (maxWidth.toPx().toInt() to (maxWidth.toPx() / aspectRatio).toInt())
                }

                val tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                core.drawPage(
                    tmpBitmap, page, width, height, 0, 0, width, height, Cookie()
                )

                bitmap = tmpBitmap
            }
            onDispose {
                job.cancel()
                bitmap?.recycle()
                bitmap = null
            }
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Zoomable image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState)
        )
    }

    // Reset zoom state when the page is moved out of the window.
    val isVisible by remember {
        derivedStateOf {
            pagerState.isVisibleForPage(position)
        }
    }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            zoomState.reset()
        }
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
fun PagerScope.isVisibleForPage(page: Int): Boolean {
    val offset = calculateCurrentOffsetForPage(page)
    return (-1.0f < offset) and (offset < 1.0f)
}