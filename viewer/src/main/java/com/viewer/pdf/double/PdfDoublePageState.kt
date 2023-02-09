package com.viewer.pdf.double

import android.graphics.Bitmap
import android.graphics.Canvas
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
            core2.drawPage(bitmap2, pageToLoad, width, height, 0, 0, Cookie())
        }

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
            core1.drawPage(bitmap1, pageToLoad, width, height, 0, 0, Cookie())
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
            bitmap1.recycle()
        }
        if (bitmap2 != null) {
            canvas.drawBitmap(
                bitmap2,
                ((bitmap1?.width ?: (finalBitmap.width / 2))).toFloat(),
                0f,
                null
            )
            bitmap2.recycle()
        }
        return finalBitmap
    }

    fun dispose() {
        entireBitmap?.recycle()
        zoomedBitmap?.recycle()
        entireBitmap = null
        zoomedBitmap = null
        core1?.destroy()
        core2?.destroy()
    }
}
