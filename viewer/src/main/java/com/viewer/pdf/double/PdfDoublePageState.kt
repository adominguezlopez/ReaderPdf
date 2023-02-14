package com.viewer.pdf.double

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.RectI
import com.artifex.mupdf.fitz.Rect as RectF
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.toRectI
import com.viewer.pdf.width
import com.viewer.pdf.zoomable.ZoomState
import kotlinx.coroutines.*
import kotlin.math.max
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
class PdfDoublePageState(
    val scope: CoroutineScope,
    val core1: PdfCore?,
    val core2: PdfCore?,
    val readerState: PdfReaderState,
    val onLinkClick: (String) -> Unit,
    val zoomState: ZoomState = ZoomState(maxScale = 600f),
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

    private var entireRect: RectI? = null

    private var page1Rect: Rect? = null
    private var page2Rect: Rect? = null

    init {
        scope.launch {
            val (bitmap, links) = withContext(Dispatchers.IO) {
                if (!readerState.reverseLayout) {
                    getPageBitmap(core1, core2) to listOf<Link>()
                } else {
                    getPageBitmap(core2, core1) to listOf<Link>()
                }
            }

            // sets the bitmap content and links of the whole page
            entireBitmap = bitmap
            entireLinks = links
            entireRect = RectI(0, 0, bitmap.width, bitmap.height)

            zoomState.setContentSize(IntSize(bitmap.width, bitmap.height).toSize())
            zoomState.setLayoutSize(readerState.readerSize.toSize())
        }
    }

    private fun getPageBitmap(core1: PdfCore?, core2: PdfCore?): Bitmap {
        val readerSize = readerState.readerSize
        val aspectRatioReader = (readerSize.width / 2) / readerSize.height

        var bitmap1: Bitmap? = null
        var bitmap2: Bitmap? = null

        if (core1 != null) {
            val pdfPageSize = core1.getPageSize(pageToLoad)
            val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

            // calculates visible width & height dimensions
            val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                readerSize.width/2 to (readerSize.width/2 / aspectRatioPage).toInt()
            } else {
                (readerSize.height * aspectRatioPage).toInt() to readerSize.height
            }
            bitmap1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page1Rect = Rect(0, 0, width, height).apply {
                core1.drawPage(bitmap1, toRectI(), height)
            }
        }

        if (core2 != null) {
            val pdfPageSize = core2.getPageSize(pageToLoad)
            val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

            // calculates visible width & height dimensions
            val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                readerSize.width/2 to (readerSize.width/2 / aspectRatioPage).toInt()
            } else {
                (readerSize.height * aspectRatioPage).toInt() to readerSize.height
            }

            bitmap2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val leftOffset = page1Rect?.width() ?: (readerSize.width - width)
            page2Rect = Rect(leftOffset, 0, width+leftOffset, height)
            val pageRect = Rect(0, 0, width, height)
            core2.drawPage(bitmap2, pageRect.toRectI(), height)
        }

        val bitmapSize = when {
            bitmap1 != null && bitmap2 != null -> IntSize(
                bitmap1.width + bitmap2.width,
                max(bitmap1.height, bitmap2.height)
            )
            bitmap1 != null -> IntSize(bitmap1.width * 2, bitmap1.height)
            bitmap2 != null -> IntSize(bitmap2.width * 2, bitmap2.height)
            else -> throw IllegalStateException("bitmap1 and bitmap2 must not be null")
        }

        val mergedBitmap =
            Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(mergedBitmap)
        if (bitmap1 != null) {
            canvas.drawBitmap(bitmap1, 0f, 0f, null)
        }

        if (bitmap2 != null) {
            canvas.drawBitmap(
                bitmap2,
                ((bitmap1?.width ?: (mergedBitmap.width / 2))).toFloat(),
                0f,
                null
            )
        }

        bitmap1?.recycle()
        bitmap2?.recycle()
        return mergedBitmap
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

        val scaledContentBounds = RectF(contentX, contentY, contentX + contentWidth, contentY + contentHeight).toRectI()
        val (scaledPage1Bounds, scaledPage2Bounds) = getPagesBounds(scaledContentBounds, scale)

        return scope.launch {
            val bitmap1 = withContext(Dispatchers.IO) {
                val core1 = core1
                if (core1 != null && !scaledPage1Bounds.isEmpty) {
                    synchronized(core1) {
                        Bitmap.createBitmap(
                            scaledPage1Bounds.width(),
                            contentHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            core1.drawPage(this, scaledPage1Bounds, scaledHeight)
                        }
                    }
                } else null
            }

            val bitmap2 = withContext(Dispatchers.IO) {
                val core2 = core2
                if (core2 != null && !scaledPage2Bounds.isEmpty) {
                    synchronized(core2) {
                        Bitmap.createBitmap(
                            scaledPage2Bounds.width(),
                            contentHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            core2.drawPage(this, scaledPage2Bounds, scaledHeight)
                        }
                    }
                } else null
            }

            val mergedBitmap = withContext(Dispatchers.IO) {
                val finalBitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(finalBitmap)
                if (bitmap1 != null) {
                    canvas.drawBitmap(bitmap1, 0f, 0f, null)
                }

                if (bitmap2 != null) {
                    val startX = if (bitmap1 != null) {
                        if (bitmap1.width + bitmap2.width > contentWidth) {
                            bitmap1.width - 1
                        } else {
                            bitmap1.width
                        }
                    } else {
                        contentWidth - bitmap2.width
                    }.toFloat()
                    canvas.drawBitmap(
                        bitmap2,
                        startX,
                        0f,
                        null
                    )
                }

                finalBitmap
            }

            if (isActive) {
                zoomedBitmap = mergedBitmap
                //zoomedLinks = links
            }
        }

    }

    private fun getPagesBounds(scaledContentBounds: RectI, scale: Float): Pair<RectI, RectI> {
        val page1Rect = page1Rect
        val page1RectRes = if (page1Rect != null) {
            val scaledPage1Rect = page1Rect.toRectI().transform(Matrix(scale))
            if (scaledContentBounds.x0 < scaledPage1Rect.x1) {
                RectI(
                    scaledContentBounds.x0,
                    scaledContentBounds.y0,
                    min(scaledContentBounds.x1, scaledPage1Rect.x1),
                    scaledContentBounds.y1
                )
            } else {
                RectI(0, 0, 0, 0)
            }
        } else {
            RectI(0, 0, 0 ,0)
        }

        val page2Rect = page2Rect
        val page2RectRes = if (page2Rect != null) {
            val scaledPage2Rect = page2Rect.toRectI().transform(Matrix(scale))
            if (scaledContentBounds.x1 > scaledPage2Rect.x0) {
                RectI(
                    max(scaledContentBounds.x0, scaledPage2Rect.x0) - scaledPage2Rect.x0,
                    scaledContentBounds.y0,
                    scaledContentBounds.x1 - scaledPage2Rect.x0,
                    scaledContentBounds.y1
                )
            } else {
                RectI(0, 0, 0, 0)
            }
        } else {
            RectI(0, 0, 0 ,0)
        }

        return page1RectRes to page2RectRes
    }

    fun dispose() {
        //entireBitmap?.recycle()
        //zoomedBitmap?.recycle()
        entireBitmap = null
        zoomedBitmap = null
        //core1?.destroy()
        //core2?.destroy()

    }

    fun clearZoomedContent() {
        zoomedBitmap = null
    }
}