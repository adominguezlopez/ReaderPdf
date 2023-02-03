package com.viewer.presenter.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.viewer.presenter.pager.HorizontalPager
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState

@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
) {
    HorizontalPager(
        count = readerState.pageCount,
        state = readerState.pagerState,
        reverseLayout = rtl,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { readerState.readerSize = it }
    ) { position ->
        if (readerState.readerSize != IntSize.Zero) {
            when (val page = readerState.pages[position]) {
                PdfReaderPage.Empty -> {
                }
                is PdfReaderPage.PdfFile -> {
                    PdfSinglePage(page, readerState, position)
                }
            }
        }
    }
}
