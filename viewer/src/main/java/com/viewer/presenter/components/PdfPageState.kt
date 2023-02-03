package com.viewer.presenter.components

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.viewer.presenter.pager.zoomable.ZoomState
import kotlinx.coroutines.CoroutineScope

@Stable
class PdfPageState(
    val scope: CoroutineScope,
    private val maxScale: Float = 6f
) {
    var entireBitmap by mutableStateOf<Bitmap?>(null)
    var zoomedBitmap by mutableStateOf<Bitmap?>(null)
    var zoomState by mutableStateOf<ZoomState?>(null)

    // TODO needed?
    fun clear() {
        entireBitmap?.recycle()
        zoomedBitmap?.recycle()
    }

    fun updateEntireBitmap(bitmap: Bitmap) {
        zoomState = ZoomState(
            maxScale = maxScale,
            contentSize = IntSize(bitmap.width, bitmap.height).toSize()
        )
        entireBitmap = bitmap
    }

    fun disposeEntireBitmap() {
        entireBitmap?.recycle()
        entireBitmap = null
        zoomState = null
    }

    fun updateZoomedBitmap(bitmap: Bitmap) {
        zoomedBitmap = bitmap
    }

    fun disposeZoomedBitmap() {
        zoomedBitmap = null
    }
}

@Composable
fun rememberPdfPageState(scope: CoroutineScope) = remember {
    PdfPageState(scope)
}