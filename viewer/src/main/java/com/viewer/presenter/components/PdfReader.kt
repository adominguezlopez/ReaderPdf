package com.viewer.presenter.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.viewer.presenter.pager.HorizontalPager
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState
import com.viewer.presenter.pager.rememberPagerState

@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
) {
    val pagerState = rememberPagerState()
    var pagerSize by remember { mutableStateOf(IntSize.Zero) }

    HorizontalPager(
        count = readerState.pageCount,
        state = pagerState,
        reverseLayout = rtl,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { pagerSize = it }
    ) { position ->
        if (pagerSize != IntSize.Zero) {
            when (val page = readerState.pages[position]) {
                PdfReaderPage.Empty -> {
                }
                is PdfReaderPage.PdfFile -> {
                    PdfSinglePage(page, this, pagerSize, position)
                }
                is PdfReaderPage.Url -> {
                    PdfPageUrl(
                        page.url
                    )
                }
            }
        }
    }
}
