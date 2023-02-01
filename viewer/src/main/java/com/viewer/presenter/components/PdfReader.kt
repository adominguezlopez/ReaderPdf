package com.viewer.presenter.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.viewer.presenter.pager.HorizontalPager
import com.viewer.presenter.pager.pdf.PdfReaderPage
import com.viewer.presenter.pager.pdf.PdfReaderState

@Composable
fun PdfReader(
    readerState: PdfReaderState,
    doublePage: Boolean,
    rtl: Boolean = false,
    pageContent: (Int) -> Unit
) {
    HorizontalPager(
        state = readerState.pageState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = rtl,
        count = readerState.pageCount,
    ) { position ->

        when (val page = readerState.pages[position]){
            PdfReaderPage.Empty -> TODO()
            is PdfReaderPage.PdfFile -> {
                PdfSinglePage(page, readerState.pageState)
            }
            is PdfReaderPage.Url -> {
                PdfPageUrl(
                    page.url
                )
            }
        }
    }

    LaunchedEffect(readerState.pageState) {
        snapshotFlow { readerState.pageState.currentPage }.collect { page ->
            pageContent.invoke(page)
        }
    }
}