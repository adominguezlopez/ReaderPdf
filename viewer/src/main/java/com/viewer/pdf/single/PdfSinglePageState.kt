package com.viewer.pdf.single

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.RectI
import com.viewer.pdf.PdfCore
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
     * Size of the page in the pdf
     */
    private var pageSize by mutableStateOf(IntSize.Zero)

    /**
     * Current bitmap of the whole page scaled to the screen size
     */
    var entireBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Current links of the whole page scaled to the screen size
     */
    private var entireLinks by mutableStateOf<List<Link>?>(null)

    /**
     * Current bitmap of a page region scaled to the screen size. Null if not zooming
     */
    var zoomedBitmap by mutableStateOf<Bitmap?>(null)

    /**
     * Current links of a page scaled to the screen size. Null if not zooming
     */
    private var zoomedLinks by mutableStateOf<List<Link>?>(null)

    /**
     * Cache to store the last zoomed bitmap that is reused when the dimensions are the same. This
     * prevents creating a new bitmap on every zoom change
     */
    private var lastZoomedBitmap: Bitmap? = null

    init {
        scope.launch {
            val (bitmap, links) = withContext(Dispatchers.IO) {
                val pdfPageSize = core.getPageSize(pageToLoad)
                val readerSize = readerState.readerSize

                pageSize = IntSize(pdfPageSize.x.toInt(), pdfPageSize.y.toInt())

                // calculates screen & pdf aspect ratios
                val aspectRatioReader = readerSize.width / readerSize.height
                val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

                // calculates visible width & height dimensions
                val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                    readerSize.width to (readerSize.width / aspectRatioPage).toInt()
                } else {
                    (readerSize.height * aspectRatioPage).toInt() to readerSize.height
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    val pageRect = RectI(0, 0, width, height)
                    core.drawPage(this, pageRect, height)
                }

                // calculates and scales links to bitmap dimensions
                val linkScaledWidth = width.toFloat() / pageSize.width
                val linkScaledHeight = height.toFloat() / pageSize.height
                val links = core.getPageLinks(pageToLoad)?.map { link ->
                    Link(
                        Rect(
                            link.bounds.x0 * linkScaledWidth,
                            link.bounds.y0 * linkScaledHeight,
                            link.bounds.x1 * linkScaledWidth,
                            link.bounds.y1 * linkScaledHeight
                        ),
                        link.uri
                    )
                }

                bitmap to links
            }

            // sets the bitmap content and links of the whole page
            zoomState.setContentSize(IntSize(bitmap.width, bitmap.height).toSize())
            zoomState.setLayoutSize(readerState.readerSize.toSize())
            entireBitmap = bitmap
            entireLinks = links
        }
    }

    fun dispose() {
        entireBitmap?.recycle()
        zoomedBitmap?.recycle()
        entireBitmap = null
        zoomedBitmap = null
        core.destroy()
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
        val width = min(scaledWidth, readerState.readerSize.width)
        val height = min(scaledHeight, readerState.readerSize.height)

        // calculates the offsets of the content of the scaled page
        val posX = zoomState.boundsX - zoomState.offsetX
        val posY = zoomState.boundsY - zoomState.offsetY

        return scope.launch {
            val (bitmap, links) = withContext(Dispatchers.IO) {
                synchronized(core) {
                    // get destination bitmap and draw page
                    val bitmap = getZoomedBitmap(width, height).apply {
                        val pageRect = RectI(posX.toInt(), posY.toInt(), width, height)
                        core.drawPage(
                            this,
                            pageRect,
                            scaledHeight
                        )
                    }

                    // rescale links
                    val links = entireLinks?.map { link ->
                        Link(
                            Rect(
                                link.bounds.x0 * scale,
                                link.bounds.y0 * scale,
                                link.bounds.x1 * scale,
                                link.bounds.y1 * scale
                            ),
                            link.uri
                        )
                    }

                    bitmap to links
                }
            }
            if (isActive) {
                zoomedBitmap = bitmap
                zoomedLinks = links
            }
        }
    }

    fun clearZoomedContent() {
        zoomedBitmap = null
        zoomedLinks = null
    }

    fun handleClick(position: Offset) {
        val contentOffset = zoomState.getOffsetInContent(position)
        (zoomedLinks ?: entireLinks)?.forEach { link ->
            if (link.bounds.contains(contentOffset.x, contentOffset.y)) {
                onLinkClick(link.uri)
            }
        }
    }
}
