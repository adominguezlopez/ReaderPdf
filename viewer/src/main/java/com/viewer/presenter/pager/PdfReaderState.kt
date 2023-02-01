package com.viewer.presenter.pager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.File

@Composable
fun rememberPdfReaderState(
    initialPage: Int = 0,
    pages: SnapshotStateList<PdfReaderPage>
): PdfReaderState {
    return remember {
        PdfReaderState(initialPage = initialPage, pages = pages)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Stable
class PdfReaderState(
    val initialPage: Int,
    val pages: SnapshotStateList<PdfReaderPage>
) {
    val pageState = PagerState(initialPage = initialPage, initialPageOffsetFraction = 0f)
    val pageCount get() = pages.size
}

sealed class PdfReaderPage {
    object Empty : PdfReaderPage()
    data class PdfFile(val file: File, val password: String? = null) : PdfReaderPage()
    data class Url(val url: String) : PdfReaderPage()
}