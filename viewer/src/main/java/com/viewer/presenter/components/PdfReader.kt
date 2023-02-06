package com.viewer.presenter.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.viewer.presenter.pager.HorizontalPager
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState
import com.viewer.presenter.pager.rememberPagerState

@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
    pageContent: (Int) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val pagerState = rememberPagerState()

        HorizontalPager(

            count = readerState.pageCount,
            state = pagerState,
        ) { position ->

            when (val page = readerState.pages[position]) {
                PdfReaderPage.Empty -> TODO()
                is PdfReaderPage.PdfFile -> {
                    PdfSinglePage(page, this, position)
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

