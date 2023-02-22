package com.viewer.pdf.page

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.viewer.pdf.PdfPageLinks
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.zoomable.ZoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@Stable
abstract class PdfPageState {

    abstract val scope: CoroutineScope
    abstract val readerState: PdfReaderState
    abstract val onLinkClick: (String) -> Unit
    abstract val zoomState: ZoomState

    /**
     * Current bitmap of the whole page scaled to the screen size
     */
    var entireBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Current links of the whole page scaled to the screen size
     */
    protected var links by mutableStateOf<PdfPageLinks?>(null)

    /**
     * Current bitmap of a page region scaled to the screen size. Null if not zooming
     */
    var zoomedBitmap by mutableStateOf<Bitmap?>(null)

    fun clearZoomedContent() {
        zoomedBitmap = null
        links?.resetScale()
    }

    fun handleClick(position: Offset) {
        val contentOffset = zoomState.getOffsetInContent(position)
        val link = links?.findLink(contentOffset)
        if (link != null) {
            onLinkClick(link.uri)
        }
    }

    fun dispose() {
        entireBitmap = null
        zoomedBitmap = null
    }

    abstract fun refreshZoomedContent(): Job?
}