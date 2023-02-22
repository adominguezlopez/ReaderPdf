package com.viewer.pdf.page.double

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toRect
import androidx.compose.ui.unit.toSize
import com.viewer.pdf.PdfCore
import com.viewer.pdf.PdfPageLinks
import com.viewer.pdf.PdfReaderState
import com.viewer.pdf.page.PdfPageState
import com.viewer.pdf.util.scale
import com.viewer.pdf.util.toIntRect
import com.viewer.pdf.zoomable.ZoomState
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min

/**
 * A state object that manage the pdf entire bitmap and links, also the zoomed bitmap and zoomed
 * links
 *
 * @param scope The scope to perform asynchronous executions
 * @param core1 The first pdf manager that allows to read from .pdf files
 * @param core2 The second pdf manager that allows to read from .pdf files
 * @param readerState The state of the pager
 * @param onLinkClick Lambda triggered on link clicked by the user
 * @param zoomState Holds the state of the zoom
 * @param core1PageToLoad The page index to load from the core1
 * @param core2PageToLoad The page index to load from the core2
 */
@Stable
class PdfDoublePageState(
    override val scope: CoroutineScope,
    override val readerState: PdfReaderState,
    override val onLinkClick: (String) -> Unit,
    override val zoomState: ZoomState = ZoomState(maxScale = 6f),
    private val core1: PdfCore?,
    private val core2: PdfCore?,
    private val core1PageToLoad: Int = 0,
    private val core2PageToLoad: Int = 0,
) : PdfPageState() {

    private var leftPageRect: Rect? = null
    private var rightPageRect: Rect? = null

    init {
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                getDoublePageContent(core1, core2)
            }

            // sets the bitmap content and links of the whole page
            entireBitmap = content.bitmap
            links = content.links

            val contentSize = IntSize(content.bitmap.width, content.bitmap.height)
            zoomState.setContentSize(contentSize.toSize())
            zoomState.setLayoutSize(readerState.readerSize.toSize())
        }
    }

    private suspend fun getDoublePageContent(
        core1: PdfCore?,
        core2: PdfCore?
    ): PdfDoublePageContent {
        val readerSize = readerState.readerSize
        val aspectRatioReader = (readerSize.width / 2) / readerSize.height

        var (leftContent, rightContent) = coroutineScope {
            val job1 = if (core1 != null) {
                async { getPageContent(core1, aspectRatioReader, readerSize, core1PageToLoad) }
            } else null

            val job2 = if (core2 != null) {
                async { getPageContent(core2, aspectRatioReader, readerSize, core2PageToLoad) }
            } else null

            // Find out left and right pages
            val page1 = job1?.await()
            val page2 = job2?.await()
            readerState.getContentForCurrentLayout(page1, page2)
        }

        // Now offset the right page as needed
        val rightPageOffset = when {
            leftContent != null -> leftContent.screenBounds.width
            rightContent != null -> rightContent.screenBounds.width
            else -> throw IllegalStateException("bitmap and bitmap2 must not be null")
        }
        rightContent = rightContent?.let { content ->
            content.copy(
                screenBounds = content.screenBounds.translate(Offset(rightPageOffset, 0f))
            )
        }

        leftPageRect = leftContent?.screenBounds
        rightPageRect = rightContent?.screenBounds

        val bitmapSize = when {
            leftContent != null && rightContent != null -> IntSize(
                leftContent.bitmap.width + rightContent.bitmap.width,
                max(leftContent.bitmap.height, rightContent.bitmap.height)
            )
            leftContent != null -> IntSize(leftContent.bitmap.width * 2, leftContent.bitmap.height)
            rightContent != null -> IntSize(rightContent.bitmap.width * 2, rightContent.bitmap.height)
            else -> throw IllegalStateException("bitmap and bitmap2 must not be null")
        }

        val mergedBitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(mergedBitmap)
        if (leftContent != null) {
            canvas.drawBitmap(leftContent.bitmap, 0f, 0f, null)
        }

        if (rightContent != null) {
            canvas.drawBitmap(rightContent.bitmap, rightPageOffset, 0f, null)
        }

        leftContent?.bitmap?.recycle()
        rightContent?.bitmap?.recycle()

        val links = PdfPageLinks(buildList {
            leftContent?.links?.baseLinks?.let(::addAll)
            // Offset links of the right page
            rightContent?.links?.baseLinks?.map {
                it.copy(bounds = it.bounds.translate(Offset(rightPageOffset, 0f)))
            }?.let(::addAll)
        })

        return PdfDoublePageContent(mergedBitmap, links)
    }

    private fun getPageContent(
        core: PdfCore,
        aspectRatioReader: Int,
        readerSize: IntSize,
        pageToLoad: Int
    ): PdfPageContent {
        val pdfPageSize = core.getPageSize(pageToLoad)
        val aspectRatioPage = pdfPageSize.width / pdfPageSize.height

        // calculates visible width & height dimensions
        val (width, height) = if (aspectRatioReader < aspectRatioPage) {
            readerSize.width / 2 to (readerSize.width / 2 / aspectRatioPage).toInt()
        } else {
            (readerSize.height * aspectRatioPage).toInt() to readerSize.height
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bounds = IntRect(0, 0, width, height).apply {
            core.drawPage(bitmap, this, height)
        }

        // calculates and scales links to bitmap dimensions
        val links = core.getPageLinks(pageToLoad).apply {
            val scaleFactor = height.toFloat() / pdfPageSize.height
            setBaseScale(scaleFactor)
        }

        return PdfPageContent(
            bitmap = bitmap,
            links = links,
            screenBounds = bounds.toRect(),
            originalSize = pdfPageSize
        )
    }

    override fun refreshZoomedContent(): Job? {
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

        val scaledContentBounds =
            Rect(contentX, contentY, contentX + contentWidth, contentY + contentHeight).toIntRect()
        val (leftScaledPageBounds, rightScaledPageBounds) = getPagesBounds(scaledContentBounds, scale)

        return scope.launch {
            val mergedBitmap = withContext(Dispatchers.IO) {
                val (leftCore, rightCore) = readerState.getContentForCurrentLayout(core1, core2)
                val (leftBitmap, rightBitmap) = coroutineScope {
                    val leftJob = if (leftCore != null && !leftScaledPageBounds.isEmpty) {
                        async {
                            getZoomedPageBitmap(leftCore, leftScaledPageBounds, contentHeight, scaledHeight)
                        }
                    } else null

                    val rightJob = if (rightCore != null && !rightScaledPageBounds.isEmpty) {
                        async {
                            getZoomedPageBitmap(rightCore, rightScaledPageBounds, contentHeight, scaledHeight)
                        }
                    } else null

                    leftJob?.await() to rightJob?.await()
                }

                withContext(Dispatchers.IO) {
                    val finalBitmap = Bitmap.createBitmap(contentWidth, contentHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(finalBitmap)
                    if (leftBitmap != null) {
                        canvas.drawBitmap(leftBitmap, 0f, 0f, null)
                    }

                    if (rightBitmap != null) {
                        val startX = if (leftBitmap != null) {
                            if (leftBitmap.width + rightBitmap.width > contentWidth) {
                                leftBitmap.width - 1
                            } else {
                                leftBitmap.width
                            }
                        } else {
                            contentWidth - rightBitmap.width
                        }.toFloat()
                        canvas.drawBitmap(rightBitmap, startX, 0f, null)
                    }

                    finalBitmap
                }
            }

            // scale links to new zoom
            links?.scale(scale)

            if (isActive) {
                zoomedBitmap = mergedBitmap
            }
        }

    }

    private fun getPagesBounds(scaledContentBounds: IntRect, scale: Float): Pair<IntRect, IntRect> {
        val leftPageRect = leftPageRect
        val leftPageRectRes = if (leftPageRect != null) {
            val leftScaledPageRect = leftPageRect.scale(scale).toIntRect()
            if (scaledContentBounds.left < leftScaledPageRect.right) {
                IntRect(
                    scaledContentBounds.left,
                    scaledContentBounds.top,
                    min(scaledContentBounds.right, leftScaledPageRect.right),
                    scaledContentBounds.bottom
                )
            } else {
                IntRect(0, 0, 0, 0)
            }
        } else {
            IntRect(0, 0, 0, 0)
        }

        val rightPageRect = rightPageRect
        val rightPageRectRes = if (rightPageRect != null) {
            val rightScaledPageRect = rightPageRect.scale(scale).toIntRect()
            if (scaledContentBounds.right > rightScaledPageRect.left) {
                IntRect(
                    max(scaledContentBounds.left, rightScaledPageRect.left) - rightScaledPageRect.left,
                    scaledContentBounds.top,
                    scaledContentBounds.right - rightScaledPageRect.left,
                    scaledContentBounds.bottom
                )
            } else {
                IntRect(0, 0, 0, 0)
            }
        } else {
            IntRect(0, 0, 0, 0)
        }

        return leftPageRectRes to rightPageRectRes
    }

    private fun getZoomedPageBitmap(
        core: PdfCore,
        scaledPageBounds: IntRect,
        contentHeight: Int,
        scaledHeight: Int
    ): Bitmap {
        return synchronized(core) {
            Bitmap.createBitmap(scaledPageBounds.width, contentHeight, Bitmap.Config.ARGB_8888).apply {
                core.drawPage(this, scaledPageBounds, scaledHeight)
            }
        }
    }
}

private data class PdfPageContent(
    val bitmap: Bitmap,
    val links: PdfPageLinks,
    val originalSize: Size,
    val screenBounds: Rect
)

private data class PdfDoublePageContent(
    val bitmap: Bitmap,
    val links: PdfPageLinks,
)
