package com.viewer.pdf.single

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfPageLinks
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.zoomable.ZoomState
import kotlinx.coroutines.*
import kotlin.math.min

/**
 * A state object that manage the pdf entire bitmap and links, also the zoomed bitmap and zoomed
 * links
 *
 * @param scope The scope to perform asynchronous executions
 * @param core The pdf manager that allows to read from .pdf files
 * @param readerState The state of the pager
 * @param onLinkClick Lambda triggered on link clicked by the user
 * @param zoomState Holds the state of the zoom
 * @param pageToLoad The page index to load from the pdf file
 */
@Stable
class PdfSinglePageState(
    val scope: CoroutineScope,
    val core: PdfCore,
    val readerState: PdfReaderState,
    val onLinkClick: (String) -> Unit,
    val zoomState: ZoomState = ZoomState(maxScale = 6f),
    private val pageToLoad: Int = 0
) {
    /**
     * Current bitmap of the whole page scaled to the screen size
     */
    var entireBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Current links of the whole page scaled to the screen size
     */
    private var links by mutableStateOf<PdfPageLinks?>(null)

    /**
     * Current bitmap of a page region scaled to the screen size. Null if not zooming
     */
    var zoomedBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Cache to store the last zoomed bitmap that is reused when the dimensions are the same. This
     * prevents creating a new bitmap on every zoom change
     */
    private var lastZoomedBitmap: Bitmap? = null

    init {
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                val pdfPageSize = core.getPageSize(pageToLoad)
                val readerSize = readerState.readerSize

                // calculates screen & pdf aspect ratios
                val aspectRatioReader = readerSize.width / readerSize.height
                val aspectRatioPage = pdfPageSize.width / pdfPageSize.height

                // calculates visible width & height dimensions
                val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                    readerSize.width to (readerSize.width / aspectRatioPage).toInt()
                } else {
                    (readerSize.height * aspectRatioPage).toInt() to readerSize.height
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    val pageRect = IntRect(0, 0, width, height)
                    core.drawPage(this, pageRect, height)
                }

                // calculates and scales links to bitmap dimensions
                val links = core.getPageLinks(pageToLoad).apply {
                    val scaleFactor = height.toFloat() / pdfPageSize.height
                    setBaseScale(scaleFactor)
                }

                PdfSinglePageContent(bitmap, links)
            }

            // sets the bitmap content and links of the whole page
            val contentSize = IntSize(content.bitmap.width, content.bitmap.height)
            zoomState.setContentSize(contentSize.toSize())
            zoomState.setLayoutSize(readerState.readerSize.toSize())

            entireBitmap = content.bitmap
            links = content.links
        }
    }

    fun dispose() {
        entireBitmap = null
        zoomedBitmap = null
    }

    private fun getZoomedBitmap(width: Int, height: Int): Bitmap {
        val current = lastZoomedBitmap
        return if (current != null && current.width == width && current.height == height) {
            current
        } else {
            lastZoomedBitmap = null
            current?.recycle()
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                lastZoomedBitmap = this
            }
        }
    }

    fun refreshZoomedContent(): Job? {
        val entireBitmap = entireBitmap ?: return null
        if (!zoomState.isSettled || zoomState.scale <= 1f) return null

        // calculates the scaled width and height
        val scale = zoomState.scale
        val scaledWidth = (entireBitmap.width * scale).toInt()
        val scaledHeight = (entireBitmap.height * scale).toInt()

        // fits the scaled page to the screen bounds
        val contentWidth = min(scaledWidth, readerState.readerSize.width)
        val contentHeight = min(scaledHeight, readerState.readerSize.height)

        // calculates the offsets of the content of the scaled page
        val contentX = zoomState.boundsX - zoomState.offsetX
        val contentY = zoomState.boundsY - zoomState.offsetY

        return scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                synchronized(core) {
                    // get destination bitmap and draw page
                    val bitmap = getZoomedBitmap(contentWidth, contentHeight).apply {
                        val pageRect = IntRect(contentX.toInt(), contentY.toInt(), contentWidth, contentHeight)
                        core.drawPage(
                            this,
                            pageRect,
                            scaledHeight
                        )
                    }

                    // scale links to new zoom
                    links?.scale(scale)

                    bitmap
                }
            }
            if (isActive) {
                zoomedBitmap = bitmap
            }
        }
    }

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
}

private data class PdfSinglePageContent(
    val bitmap: Bitmap,
    val links: PdfPageLinks,
)
