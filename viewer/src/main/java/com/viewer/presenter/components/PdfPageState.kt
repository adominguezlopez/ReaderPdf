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
    private var _zoomedBitmap: Bitmap? = null

    // TODO needed?
    fun clear() {
        entireBitmap?.recycle()
        zoomedBitmap?.recycle()
        entireBitmap = null
        zoomedBitmap = null
        zoomState = null
    }

    fun updateEntireBitmap(bitmap: Bitmap) {
        zoomState = ZoomState(
            maxScale = maxScale,
            contentSize = IntSize(bitmap.width, bitmap.height).toSize()
        )
        entireBitmap = bitmap
    }

    fun updateZoomedBitmap(bitmap: Bitmap) {
        zoomedBitmap = bitmap
    }

    fun disposeZoomedBitmap() {
        zoomedBitmap = null
    }

    fun getZoomedBitmap(width: Int, height: Int): Bitmap {
        val current = _zoomedBitmap
        return if (current != null && current.width == width && current.height == height) {
            current
        } else {
            _zoomedBitmap = null
            current?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                _zoomedBitmap = this
            }
        }
    }
}

@Composable
fun rememberPdfPageState(scope: CoroutineScope) = remember {
    PdfPageState(scope)
}