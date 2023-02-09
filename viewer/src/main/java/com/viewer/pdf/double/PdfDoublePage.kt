package com.viewer.pdf.double

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderPage
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.zoomable.zoomable
import kotlinx.coroutines.*

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

    DisposableEffect(pdfFile1, pdfFile2) {
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
    if (currentState != null) {
        Row(
            modifier = Modifier.zoomable(zoomState = currentState.zoomState)
        ) {
            Box(modifier = Modifier
                .fillMaxHeight()
                .weight(1f)) {
                if (currentState.entireBitmap1 != null) {
                    Image(
                        bitmap = currentState.entireBitmap1!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                    )
                }
            }

            Box(modifier = Modifier
                .fillMaxHeight()
                .weight(1f)) {
                if (currentState.entireBitmap2 != null) {
                    Image(
                        bitmap = currentState.entireBitmap2!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                    )
                }
            }
        }
    }
}