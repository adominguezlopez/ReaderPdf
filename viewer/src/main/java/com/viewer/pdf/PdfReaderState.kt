package com.viewer.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

@Stable
@OptIn(ExperimentalFoundationApi::class)
class PdfReaderState(
    val initialPage: Int,
    val pages: SnapshotStateList<PdfReaderPage>,
    val doublePage: Boolean = false,
    val reverseLayout: Boolean = false,
) {
    val pageCount get() = if (doublePage) pages.size / 2 + 1 else pages.size
    val pagerState = PagerState(initialPage = if (doublePage) (initialPage + 1) / 2 else initialPage)
    var readerSize by mutableStateOf(IntSize.Zero)
    var initialPageAspectRatio by mutableStateOf(0f)

    val currentPage get() = pagerState.currentPage
    val realPage by derivedStateOf {
        if (doublePage) {
            max(currentPage * 2 - 1, 0)
        } else {
            currentPage
        }
    }

    var scope: CoroutineScope? = null

    fun setCurrentPage(position: Int) {
        scope?.launch {
            pagerState.animateScrollToPage(position)
        }
    }

    fun <T> getContentForCurrentLayout(param1: T, param2: T): Pair<T, T> {
        return if (!reverseLayout) {
            param1 to param2
        } else {
            param2 to param1
        }
    }
}

sealed class PdfReaderPage {
    object Empty : PdfReaderPage()
    data class PdfFile(val file: File, val passwords: List<String>? = null, val thumbnail: File? = null) : PdfReaderPage()
}
