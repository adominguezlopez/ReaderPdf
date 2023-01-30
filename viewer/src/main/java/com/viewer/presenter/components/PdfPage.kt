package com.viewer.presenter.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.PDFDocument
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.viewer.PDFCore
import com.viewer.presenter.pager.PdfReaderPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalPagerApi::class)
@Composable
fun PdfSinglePage(
    pdfFile: PdfReaderPage.PdfFile,
    pagerState: PagerState
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val page = 0
        val document =
            remember { PDFDocument.openDocument(pdfFile.file.absolutePath) as PDFDocument }
        document.authenticatePassword(pdfFile.password)
        val core = PDFCore(document)

        val pageOriginalSize = core.getPageSize(page)
        val aspectRatio = pageOriginalSize.x / pageOriginalSize.y

        val (width, height) = with(LocalDensity.current) {
            (maxWidth.toPx().toInt() to (maxHeight.toPx() * aspectRatio).toInt())
        }

        val mEntireBm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        core.drawPage(
            mEntireBm, page, width, height, 0, 0, width, height, Cookie()
        )

        ZoomableImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = mEntireBm.asImageBitmap(),
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
@OptIn(ExperimentalPagerApi::class, ExperimentalFoundationApi::class)
private fun ZoomableImage(
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap,
    minScale: Float = 1f,
    maxScale: Float = 6f,
    contentScale: ContentScale = ContentScale.Fit,
    pagerState: PagerState
) {
    val scale = remember { mutableStateOf(1f) }
    val rotationState = remember { mutableStateOf(1f) }
    val offsetX = remember { mutableStateOf(1f) }
    val offsetY = remember { mutableStateOf(1f) }

    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .clip(RectangleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {  },
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
                                    pagerState.disableScrolling(this)
                                }
                                val offset = event.calculatePan()
                                offsetX.value += offset.x
                                offsetY.value += offset.y
                                rotationState.value += event.calculateRotation()
                                coroutineScope.launch {
                                    pagerState.reenableScrolling(this)
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
                .align(Alignment.Center)
                .graphicsLayer {
                    scaleX = maxOf(minScale, minOf(maxScale, scale.value))
                    scaleY = maxOf(minScale, minOf(maxScale, scale.value))
                    translationX = offsetX.value
                    translationY = offsetY.value
                }
        )
    }
}

@OptIn(ExperimentalPagerApi::class)
fun PagerState.disableScrolling(scope: CoroutineScope) {
    scope.launch {
        scroll(scrollPriority = MutatePriority.PreventUserInput) {
            // Await indefinitely, blocking scrolls
            awaitCancellation()
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
fun PagerState.reenableScrolling(scope: CoroutineScope) {
    scope.launch {
        scroll(scrollPriority = MutatePriority.PreventUserInput) {
            // Do nothing, just cancel the previous indefinite "scroll"
        }
    }
}