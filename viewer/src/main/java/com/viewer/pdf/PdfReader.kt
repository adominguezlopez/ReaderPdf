package com.viewer.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.viewer.pdf.single.PdfSinglePage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
    onLinkClick: (String) -> Unit = {}
) {
    HorizontalPager(
        pageCount = readerState.pageCount,
        state = readerState.pagerState,
        reverseLayout = rtl,
        beyondBoundsPageCount = 1,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { readerState.readerSize = it }
    ) { position ->
        if (readerState.readerSize != IntSize.Zero) {
            when (val page = readerState.pages[position]) {
                PdfReaderPage.Empty -> {
                }
                is PdfReaderPage.PdfFile -> {
                    PdfSinglePage(
                        pdfFile = page,
                        readerState = readerState,
                        position = position,
                        onLinkClick = onLinkClick
                    )
                }
            }
        }
    }
}
