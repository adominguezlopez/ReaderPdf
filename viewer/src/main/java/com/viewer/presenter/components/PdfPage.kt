package com.viewer.presenter.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import com.viewer.presenter.pager.PdfReaderPage

@Composable
fun PdfPageUrl(
    url: String
) {
    AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = url,
        contentDescription = null
    )
}

@Composable
fun PdfSinglePage(
    file: PdfReaderPage.PdfFile
) {

}

@Composable
fun PdfDoublePage(
    file1: PdfReaderPage.PdfFile?,
    file2: PdfReaderPage.PdfFile?,
) {

}