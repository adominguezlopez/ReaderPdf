package com.viewer.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.viewer.pdf.double.PdfDoublePage
import com.viewer.pdf.single.PdfSinglePage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfReader(
    readerState: PdfReaderState,
    onLinkClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!readerState.doublePage) {
        HorizontalPager(
            pageCount = readerState.pageCount,
            state = readerState.pagerState,
            reverseLayout = readerState.reverseLayout,
            beyondBoundsPageCount = 1,
            modifier = modifier
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
    } else {
        HorizontalPager(
            pageCount = readerState.pageCount,
            state = readerState.pagerState,
            reverseLayout = readerState.reverseLayout,
            beyondBoundsPageCount = 1,
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { readerState.readerSize = it }
        ) { position ->
            if (readerState.readerSize != IntSize.Zero) {
                val page1 = readerState.pages.getOrNull(position * 2 - 1)
                val page2 = readerState.pages.getOrNull(position * 2)

                if (page1 is PdfReaderPage.Empty || page2 is PdfReaderPage.Empty) {

                } else {
                    PdfDoublePage(
                        pdfFile1 = page1 as? PdfReaderPage.PdfFile,
                        pdfFile2 = page2 as? PdfReaderPage.PdfFile,
                        readerState = readerState,
                        position = position,
                        onLinkClick = onLinkClick
                    )
                }
            }
        }
    }
}
