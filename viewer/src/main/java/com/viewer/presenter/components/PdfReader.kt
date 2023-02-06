package com.viewer.presenter.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
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
                    PdfSinglePage(page, readerState, position)
                }
            }
        }
    }
}
