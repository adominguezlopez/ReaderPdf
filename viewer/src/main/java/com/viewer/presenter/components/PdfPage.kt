package com.viewer.presenter.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.PDFDocument
import com.viewer.PDFCore
import com.viewer.presenter.pager.PagerState
import com.viewer.presenter.pager.pdf.PdfReaderPage
import kotlinx.coroutines.*

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
    pagerState: PagerState
) {
    val page = 0
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

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
        ZoomableImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = it.asImageBitmap(),
            pagerState = pagerState
        )
    }
}

@Composable
fun PdfDoublePage(
    pdfFile1: PdfReaderPage.PdfFile?,
    pdfFile2: PdfReaderPage.PdfFile?,
) {

}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap,
    minScale: Float = 6f,
    maxScale: Float = 1f,
    contentScale: ContentScale = ContentScale.Fit,
    pagerState: PagerState
) {
    Log.d("PdfSinglePage", "image. Size: ${bitmap.width} ${bitmap.height}")

    val scale = remember { mutableStateOf(1f) }
    val rotationState = remember { mutableStateOf(1f) }
    val offsetX = remember { mutableStateOf(1f) }
    val offsetY = remember { mutableStateOf(2f) }

    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
                onDoubleClick = {
                    if (scale.value >= 2f) {
                        scale.value = 1f
                        offsetX.value = 1f
                        offsetY.value = 1f
                    } else scale.value = 3f
                },
            )
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            scale.value *= event.calculateZoom()
                            if (scale.value > 1) {
                                coroutineScope.launch {
                                    pagerState.lazyListState.stopScroll()
                                }
                                val offset = event.calculatePan()
                                offsetX.value += offset.x
                                offsetY.value += offset.y
                                rotationState.value += event.calculateRotation()
                                coroutineScope.launch {
                                    pagerState.lazyListState.scroll {  }
                                }
                            } else {
                                scale.value = 1f
                                offsetX.value = 1f
                                offsetY.value = 1f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }

    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = maxOf(maxScale, minOf(minScale, scale.value))
                    scaleY = maxOf(maxScale, minOf(minScale, scale.value))
                    translationX = offsetX.value
                    translationY = offsetY.value
                }
        )
    }
}