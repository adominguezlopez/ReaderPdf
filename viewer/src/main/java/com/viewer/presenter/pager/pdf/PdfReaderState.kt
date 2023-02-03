package com.viewer.presenter.pager.pdf

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.IntSize
import com.viewer.presenter.pager.PagerState
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

@Stable
class PdfReaderState(
    val initialPage: Int,
    val pages: SnapshotStateList<PdfReaderPage>
) {
    val pagerState = PagerState(currentPage = initialPage)
    val pageCount get() = pages.size
    var readerSize by mutableStateOf(IntSize.Zero)
}

sealed class PdfReaderPage {
    object Empty : PdfReaderPage()
    data class PdfFile(val file: File, val password: String? = null) : PdfReaderPage()
}