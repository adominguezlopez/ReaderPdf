package com.viewer.presenter.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.PDFDocument
import com.viewer.PDFCore
import com.viewer.presenter.pager.PagerScope
import com.viewer.presenter.pager.calculateCurrentOffsetForPage
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.zoomable.rememberZoomState
import com.viewer.presenter.pager.zoomable.zoomable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.min

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
    pagerSize: IntSize,
    position: Int
) {
    val page = 0
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoomedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var core by remember { mutableStateOf<PDFCore?>(null) }

    DisposableEffect(pdfFile) {
        val job = scope.launch(Dispatchers.IO) {
            core = run {
                val document = PDFDocument.openDocument(pdfFile.file.absolutePath) as PDFDocument
                document.authenticatePassword(pdfFile.password)
                PDFCore(document)
            }

            val pageOriginalSize = core!!.getPageSize(page)
            val aspectRatio = pageOriginalSize.x / pageOriginalSize.y

            val (width, height) = (pagerSize.width to (pagerSize.width / aspectRatio).toInt())

            val tmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            core!!.drawPage(
                tmpBitmap, page, width, height, 0, 0, Cookie()
            )

            bitmap = tmpBitmap
        }
        onDispose {
            job.cancel()
            bitmap?.recycle()
            bitmap = null
        }
    }

    bitmap?.let {
        Box(
            contentAlignment = Alignment.Center
        ) {
            val zoomState =
                rememberZoomState(
                    contentSize = Size(it.width.toFloat(), it.height.toFloat()),
                    maxScale = 50f
                )
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Zoomable image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .background(Color.Gray)
                    .fillMaxSize()
                    .zoomable(zoomState)
            )

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

            DisposableEffect(zoomState.isDragInProgress) {
                if (!zoomState.isDragInProgress && zoomState.scale > 1f) {
                    val tmpBitmap = Bitmap.createBitmap(
                        min(
                            (bitmap!!.width * zoomState.scale).toInt(),
                            pagerSize.width
                        ),
                        min((bitmap!!.height * zoomState.scale).toInt(), pagerSize.height),
                        Bitmap.Config.ARGB_8888
                    )

                    val posX = zoomState.boundsX - zoomState.offsetX
                    val posY = zoomState.boundsY - zoomState.offsetY

                    core!!.drawPage(
                        tmpBitmap,
                        page,
                        (bitmap!!.width * zoomState.scale).toInt(),
                        (bitmap!!.height * zoomState.scale).toInt(),
                        posX.toInt(),
                        posY.toInt(),
                        Cookie()
                    )

                    zoomedBitmap = tmpBitmap
                }
                onDispose {
                    /*
                    if (zoomState.isDragInProgress) {
                        zoomedBitmap?.recycle()
                        zoomedBitmap = null
                    }
                     */
                    zoomedBitmap = null
                }
            }

            zoomedBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Zoomable image",
                    contentScale = ContentScale.Fit
                )
            }
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