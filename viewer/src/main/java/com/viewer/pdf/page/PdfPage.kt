package com.viewer.pdf.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.viewer.pdf.zoomable.zoomable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfPage(state: PdfPageState, position: Int) {
    Box(contentAlignment = Alignment.Center) {
        PdfEntirePage(state = state)
        PdfZoomedPage(state = state)
    }

    // Reset zoom state when the page is moved out of the window.
    val isVisible = position == state.readerState.pagerState.settledPage
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            state.zoomState.reset()
        }
    }
}

@Composable
fun PdfEntirePage(state: PdfPageState) {
    val entireBitmap = state.entireBitmap
    val currentZoomState = state.zoomState
    if (entireBitmap != null) {
        Image(
            bitmap = entireBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = currentZoomState,
                    onTap = state::handleClick,
                ),
        )

        if (state.readerState.initialPageAspectRatio == 0f) {
            state.readerState.initialPageAspectRatio = entireBitmap.width.toFloat() / entireBitmap.height
        }
    }
}

@Composable
fun PdfZoomedPage(state: PdfPageState) {
    val zoomState = state.zoomState
    if (zoomState.scale <= 1f) return

    DisposableEffect(zoomState.isSettled) {
        val job = state.refreshZoomedContent()
        onDispose {
            job?.cancel()
            state.clearZoomedContent()
        }
    }

    val zoomedBitmap = state.zoomedBitmap
    if (zoomedBitmap != null) {
        Image(
            bitmap = zoomedBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }
}
