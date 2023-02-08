package com.viewer.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.IntSize
import java.io.File

@Stable
@OptIn(ExperimentalFoundationApi::class)
class PdfReaderState(
    val initialPage: Int,
    val pages: SnapshotStateList<PdfReaderPage>
) {
    val pagerState = PagerState(initialPage = initialPage)
    val pageCount get() = pages.size
    var readerSize by mutableStateOf(IntSize.Zero)

    val currentPage get() = pagerState.currentPage

    suspend fun setCurrentPage(position: Int) {
        pagerState.animateScrollToPage(position)
    }
}

@Composable
fun rememberPdfReaderState(
    initialPage: Int = 0,
    pages: SnapshotStateList<PdfReaderPage>
): PdfReaderState {
    return remember {
        PdfReaderState(initialPage = initialPage, pages = pages)
    }
}

sealed class PdfReaderPage {
    object Empty : PdfReaderPage()
    data class PdfFile(val file: File, val password: String? = null) : PdfReaderPage()
}