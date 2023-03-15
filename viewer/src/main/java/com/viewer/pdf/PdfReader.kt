package com.viewer.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.viewer.pdf.page.double.PdfDoublePage
import com.viewer.pdf.page.single.PdfSinglePage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfReader(
    readerState: PdfReaderState,
    modifier: Modifier = Modifier,
    onClick: (Offset, String?) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    DisposableEffect(readerState, scope) {
        readerState.scope = scope
        onDispose {
            readerState.scope = null
        }
    }

    HorizontalPager(
        pageCount = readerState.pageCount,
        state = readerState.pagerState,
        reverseLayout = readerState.reverseLayout,
        beyondBoundsPageCount = 1,
        pageSpacing = 10.dp,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { readerState.readerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick(it, null) })
            },
    ) { position ->
        if (readerState.readerSize != IntSize.Zero) {
            if (!readerState.doublePage) {
                when (val page = readerState.pages[position]) {
                    is PdfReaderPage.PdfFile -> {
                        PdfSinglePage(
                            pdfFile = page,
                            readerState = readerState,
                            position = position,
                            onClick = onClick,
                        )
                    }
                    PdfReaderPage.Empty -> {
                        EmptyPage(readerState)
                    }
                }
            } else {
                val page1 = readerState.pages.getOrNull(position * 2 - 1)
                val page2 = readerState.pages.getOrNull(position * 2)

                if (page1 !is PdfReaderPage.Empty && page2 !is PdfReaderPage.Empty) {
                    PdfDoublePage(
                        pdfFile1 = page1 as? PdfReaderPage.PdfFile,
                        pdfFile2 = page2 as? PdfReaderPage.PdfFile,
                        readerState = readerState,
                        position = position,
                        onClick = onClick,
                    )
                } else {
                    EmptyPage(readerState)
                }
            }
        }
    }
}

@Composable
private fun EmptyPage(readerState: PdfReaderState) {
    val aspectRatio = readerState.initialPageAspectRatio
    if (aspectRatio == 0f) return

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(readerState.initialPageAspectRatio)
            .background(Color.White),
    )
}
