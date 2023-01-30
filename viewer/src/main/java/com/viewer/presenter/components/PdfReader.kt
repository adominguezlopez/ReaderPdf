package com.viewer.presenter.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.viewer.presenter.pager.PdfReaderPage
import com.viewer.presenter.pager.PdfReaderState

@OptIn(ExperimentalFoundationApi::class)
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
        pageCount = readerState.pageCount
    ) { position ->
        when (val page = readerState.pages[position]){
            PdfReaderPage.Empty -> TODO()
            is PdfReaderPage.PdfFile -> TODO()
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