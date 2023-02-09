package com.viewer.pdf.double

import android.graphics.Bitmap
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
    var entireBitmap1 by mutableStateOf<Bitmap?>(null)
    var entireBitmap2 by mutableStateOf<Bitmap?>(null)

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
                val pdfPageSize1 = core1?.getPageSize(pageToLoad)
                val pdfPageSize2 = core2?.getPageSize(pageToLoad)
                val readerSize = readerState.readerSize

                var bitmap1: Bitmap? = null
                var bitmap2: Bitmap? = null
                val aspectRatioReader = (readerSize.width / 2) / readerSize.height

                if (pdfPageSize1 != null) {
                    // calculates screen & pdf aspect ratios

                    val aspectRatioPage1 = pdfPageSize1.x / pdfPageSize1.y

                    // calculates visible width & height dimensions
                    val (width1, height1) = if (aspectRatioReader < aspectRatioPage1) {
                        readerSize.width to (readerSize.width / aspectRatioPage1).toInt()
                    } else {
                        (readerSize.height * aspectRatioPage1).toInt() to readerSize.height
                    }

                    bitmap1 = Bitmap.createBitmap(width1, height1, Bitmap.Config.ARGB_8888)

                    bitmap1.apply {
                        core1?.drawPage(this, pageToLoad, width1, height1, 0, 0, Cookie())
                    }
                }

                if (pdfPageSize2 != null) {
                    val aspectRatioPage2 = pdfPageSize2.x / pdfPageSize2.y

                    // calculates visible width & height dimensions
                    val (width2, height2) = if (aspectRatioReader < aspectRatioPage2) {
                        readerSize.width to (readerSize.width / aspectRatioPage2).toInt()
                    } else {
                        (readerSize.height * aspectRatioPage2).toInt() to readerSize.height
                    }

                    bitmap2 = Bitmap.createBitmap(width2, height2, Bitmap.Config.ARGB_8888)

                    bitmap2.apply {
                        core2?.drawPage(this, pageToLoad, width2, height2, 0, 0, Cookie())
                    }
                }

                Pair(bitmap1, bitmap2) to listOf<Link>()
            }

            // sets the bitmap content and links of the whole page
            entireBitmap1 = bitmap.first
            entireBitmap2 = bitmap.second
            entireLinks = links

            zoomState.setContentSize(
                IntSize(
                    (entireBitmap1?.width ?: 0) + (entireBitmap2?.width ?: 0),
                    max((entireBitmap1?.height ?: 0), (entireBitmap2?.height ?: 0))
                ).toSize()
            )
            zoomState.setLayoutSize(readerState.readerSize.toSize())
        }
    }

    fun dispose() {
        entireBitmap1?.recycle()
        entireBitmap2?.recycle()
        zoomedBitmap?.recycle()
        entireBitmap1 = null
        entireBitmap2 = null
        zoomedBitmap = null
        core1?.destroy()
    }
}
