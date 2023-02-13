package com.viewer.pdf.double

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Link
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfReaderState
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

    /**
     * Cache to store the last zoomed bitmap that is reused when the dimensions are the same. This
     * prevents creating a new bitmap on every zoom change
     */
    private var lastZoomedBitmap: Bitmap? = null

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
                readerSize.width to (readerSize.width / aspectRatioPage).toInt()
            } else {
                (readerSize.height * aspectRatioPage).toInt() to readerSize.height
            }
            bitmap1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page1Rect = Rect(0, 0, width, height)
            core1.drawPage(bitmap1, pageToLoad, width, height, 0, 0, Cookie())
        }

        if (core2 != null) {
            val pdfPageSize = core2.getPageSize(pageToLoad)
            val aspectRatioPage = pdfPageSize.x / pdfPageSize.y

            // calculates visible width & height dimensions
            val (width, height) = if (aspectRatioReader < aspectRatioPage) {
                readerSize.width to (readerSize.width / aspectRatioPage).toInt()
            } else {
                (readerSize.height * aspectRatioPage).toInt() to readerSize.height
            }

            bitmap2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val leftOffset = page1Rect?.width() ?: 0
            page2Rect = Rect(leftOffset, 0, width + leftOffset, height)
            core2.drawPage(bitmap2, pageToLoad, width, height, 0, 0, Cookie())
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

        val finalBitmap =
            Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(finalBitmap)
        if (bitmap1 != null) {
            canvas.drawBitmap(bitmap1, 0f, 0f, null)
        }

        if (bitmap2 != null) {
            canvas.drawBitmap(
                bitmap2,
                ((bitmap1?.width ?: (finalBitmap.width / 2))).toFloat(),
                0f,
                null
            )
        }

        bitmap1?.recycle()
        bitmap2?.recycle()
        return finalBitmap
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

        val left = (posX / scale).toInt()
        val top = (posY / scale).toInt()
        val right = left + (width / scale).toInt()
        val bottom = top + (height / scale).toInt()
        val zoomedWindow = Rect(left, top, right, bottom)
        val (page1Bounds, page2Bounds) = getZoomBounds(zoomedWindow)

        return scope.launch {
            val bitmap1 = withContext(Dispatchers.IO) {
                val core1 = core1
                if (core1 != null && !page1Bounds.isEmpty) {
                    synchronized(core1) {
                        // get destination bitmap and draw page
                        val percent = (page1Bounds.width().toFloat()/(zoomedWindow.width()))
                        val totalPercent = (page1Rect!!.width().toFloat()/(page1Rect!!.width()+page2Rect!!.width()))
                        Bitmap.createBitmap(
                            //page1Bounds.witdh/(page1Bounds.witdh+page2Bounds.wdith)*width
                            (percent*width).toInt(),
                            height,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            core1.drawPage(
                                this,
                                pageToLoad,
                                (scaledWidth*totalPercent).toInt(),
                                scaledHeight,
                                (page1Bounds.left*scale).toInt(),
                                (page1Bounds.top*scale).toInt(),
                                Cookie()
                            )
                        }
                    }
                } else null
            }


            val bitmap2 = withContext(Dispatchers.IO) {
                val core2 = core2
                if (core2 != null && !page2Bounds.isEmpty) {
                    synchronized(core2) {
                        val percent = (page2Bounds.width().toFloat()/(zoomedWindow.width()))
                        val totalPercent = (page2Rect!!.width().toFloat()/(page1Rect!!.width()+page2Rect!!.width()))
                        // get destination bitmap and draw page
                        Bitmap.createBitmap(
                            (percent*width).toInt(),
                            height,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            core2.drawPage(
                                this,
                                pageToLoad,
                                (scaledWidth*totalPercent).toInt(),
                                scaledHeight,
                                (page2Bounds.left*scale).toInt(),
                                (page2Bounds.top*scale).toInt(),
                                Cookie()
                            )
                        }
                    }
                } else null
            }

            val finalBitmap = withContext(Dispatchers.IO) {
                val finalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(finalBitmap)
                if (bitmap1 != null) {
                    canvas.drawBitmap(bitmap1, 0f, 0f, null)
                }

                if (bitmap2 != null) {
                    canvas.drawBitmap(
                        bitmap2,
                        ((bitmap1?.width ?: 0)).toFloat(),
                        0f,
                        null
                    )
                }

                finalBitmap
            }

            if (isActive) {
                zoomedBitmap = finalBitmap
                //zoomedLinks = links
            }
        }

    }

    private fun getZoomBounds(rect: Rect): Pair<Rect, Rect> {
        val page1Rect = page1Rect
        val page1RectRes = if (page1Rect != null && rect.left < page1Rect.right) {
            Rect(rect.left, rect.top, min(rect.right, page1Rect.right), rect.bottom)
        } else {
            Rect()
        }

        val page2Rect = page2Rect
        val page2RectRes = if (page2Rect != null && rect.right > page2Rect.left) {
            Rect(
                max(rect.left, page2Rect.left) - page2Rect.left,
                rect.top,
                rect.right - page2Rect.left,
                rect.bottom
            )
        } else {
            Rect()
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
}
