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
                val bitmap1 = core1?.let {
                    getPageBitmap(it)
                }

                val bitmap2 = core2?.let {
                    getPageBitmap(it)
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

    private fun getPageBitmap(core: PdfCore): Bitmap{
        val pdfPageSize = core.getPageSize(pageToLoad)
        val aspectRatioPage = pdfPageSize.x / pdfPageSize.y
        val readerSize = readerState.readerSize
        val aspectRatioReader = (readerSize.width / 2) / readerSize.height

        // calculates visible width & height dimensions
        val (width, height) = if (aspectRatioReader < aspectRatioPage) {
            readerSize.width to (readerSize.width / aspectRatioPage).toInt()
        } else {
            (readerSize.height * aspectRatioPage).toInt() to readerSize.height
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            core.drawPage(this, pageToLoad, width, height, 0, 0, Cookie())
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
