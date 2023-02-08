package com.viewer.pdf.single

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Rect
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.zoomable.ZoomState
import kotlinx.coroutines.*
import kotlin.math.min

@Stable
class PdfSinglePageState(
    val scope: CoroutineScope,
    val core: PdfCore,
    val readerState: PdfReaderState,
    val onLinkClick: (String) -> Unit,
    val zoomState: ZoomState = ZoomState(maxScale = 6f),
    private val pageToLoad: Int = 0
) {
    private var pageSize by mutableStateOf(IntSize.Zero)
    var entireBitmap by mutableStateOf<Bitmap?>(null)
    private var entireLinks by mutableStateOf<List<Link>?>(null)
    var zoomedBitmap by mutableStateOf<Bitmap?>(null)
    private var zoomedLinks by mutableStateOf<List<Link>?>(null)
    private var _zoomedBitmap: Bitmap? = null

    init {
        scope.launch {
            val (bitmap, links) = withContext(Dispatchers.IO) {
                val pdfPageSize = core.getPageSize(pageToLoad)
                val readerSize = readerState.readerSize

                pageSize = IntSize(pdfPageSize.x.toInt(), pdfPageSize.y.toInt())

                val aspectRatioReader = readerSize.width / readerSize.height
                val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

                val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                    readerSize.width to (readerSize.width / aspectRatioPage).toInt()
                } else {
                    (readerSize.height * aspectRatioPage).toInt() to readerSize.height
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    core.drawPage(this, pageToLoad, width, height, 0, 0, Cookie())
                }

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

    fun clearZoomedContent() {
        zoomedBitmap = null
        zoomedLinks = null
    }

    private fun getZoomedBitmap(width: Int, height: Int): Bitmap {
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

    fun handleClick(position: Offset) {
        val contentOffset = zoomState.getOffsetInContent(position)
        (zoomedLinks ?: entireLinks)?.forEach { link ->
            if (link.bounds.contains(contentOffset.x, contentOffset.y)) {
                onLinkClick(link.uri)
            }
        }
    }

    fun refreshZoomedContent(): Job? {
        val entireBitmap = entireBitmap ?: return null
        if (!zoomState.isSettled || zoomState.scale <= 1f) return null

        val scaledWidth = (entireBitmap.width * zoomState.scale).toInt()
        val scaledHeight = (entireBitmap.height * zoomState.scale).toInt()

        val width = min(scaledWidth, readerState.readerSize.width)
        val height = min(scaledHeight, readerState.readerSize.height)

        val posX = zoomState.boundsX - zoomState.offsetX
        val posY = zoomState.boundsY - zoomState.offsetY

        return scope.launch {
            val (bitmap, links) = withContext(Dispatchers.IO) {
                synchronized(core) {
                    // Get destination bitmap and draw page
                    val bitmap = getZoomedBitmap(width, height).apply {
                        core.drawPage(
                            this,
                            pageToLoad,
                            scaledWidth,
                            scaledHeight,
                            posX.toInt(),
                            posY.toInt(),
                            Cookie()
                        )
                    }

                    // Refresh links
                    val links = entireLinks?.map { link ->
                        Link(
                            Rect(
                                link.bounds.x0 * zoomState.scale,
                                link.bounds.y0 * zoomState.scale,
                                link.bounds.x1 * zoomState.scale,
                                link.bounds.y1 * zoomState.scale
                            ),
                            link.uri
                        )
                    }

                    bitmap to links
                }
            }
            if (isActive) { // TODO test if isActive is checked while returning from withContext
                zoomedBitmap = bitmap
                zoomedLinks = links
            }
        }
    }

}
