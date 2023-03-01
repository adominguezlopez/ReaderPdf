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

    /**
     * Scope of the page state used to perform async operations
     */
    abstract val scope: CoroutineScope

    /**
     * State of the reader
     */
    abstract val readerState: PdfReaderState

    /**
     * Callback called when the screen is tapped. A string is provided if the tap was on an URI
     */
    abstract val onClick: (Offset, String?) -> Unit

    /**
     * Zoom state used to handle gestures and reload bitmaps from pdf
     */
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

        // Always dispatch the on click event, even if there's no link
        onClick(position, link?.uri)
    }

    fun dispose() {
        entireBitmap = null
        zoomedBitmap = null
    }

    abstract fun refreshZoomedContent(): Job?
}
