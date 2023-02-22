package com.viewer.pdf.page.double

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.page.PdfPage
import kotlinx.coroutines.*
import java.io.File

@Composable
fun PdfDoublePage(
    pdfFile1: PdfReaderPage.PdfFile?,
    pdfFile2: PdfReaderPage.PdfFile?,
    readerState: PdfReaderState,
    position: Int,
    onLinkClick: (String) -> Unit
) {
    var state by remember { mutableStateOf<PdfDoublePageState?>(null) }

    if (pdfFile1 == null && pdfFile2 == null) return

    DisposableEffect(position) {
        val scope = MainScope()
        scope.launch {
            val core1 = pdfFile1?.let {
                withContext(Dispatchers.IO) {
                    PdfCore(pdfFile1.file, pdfFile1.password)
                }
            }
            val core2 = pdfFile2?.let {
                withContext(Dispatchers.IO) {
                    PdfCore(pdfFile2.file, pdfFile2.password)
                }
            }
            state = PdfDoublePageState(
                scope = scope,
                core1 = core1,
                core2 = core2,
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
    } else {
        Row(
            horizontalArrangement = Arrangement.Center
        ) {
            val (leftThumbnail, rightThumbnail) = readerState.getContentForCurrentLayout(pdfFile1?.thumbnail, pdfFile2?.thumbnail)

            ThumbnailImage(
                thumbnail = leftThumbnail,
                alignment = Alignment.CenterEnd
            )
            ThumbnailImage(
                thumbnail = rightThumbnail,
                alignment = Alignment.CenterStart
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RowScope.ThumbnailImage(
    thumbnail: File?,
    alignment: Alignment
) {
    Box(modifier = Modifier.weight(1f)) {
        if (thumbnail != null) {
            GlideImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}

