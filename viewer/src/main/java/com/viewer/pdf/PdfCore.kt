package com.viewer.pdf

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import com.artifex.mupdf.fitz.*
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File

class PdfCore(
    file: File,
    passwords: List<String>? = null,
    private val resolution: Int = 160
) {
    private val doc: Document
    private var pageCount = -1
    private var currentPage = -1
    private var page: Page? = null
    private var pageWidth = 0f
    private var pageHeight = 0f
    private var displayList: DisplayList? = null

    init {
        doc = PDFDocument.openDocument(file.absolutePath) as PDFDocument
        passwords?.find { doc.authenticatePassword(it) }
        pageCount = doc.countPages()
    }

    @Synchronized
    fun destroy() {
        if (displayList != null) displayList!!.destroy()
        displayList = null
        if (page != null) page!!.destroy()
        page = null
        doc.destroy()
    }

    @Synchronized
    private fun gotoPage(pageNumber: Int) {
        var pageNum = pageNumber
        if (pageNum > pageCount - 1) pageNum = pageCount - 1 else if (pageNum < 0) pageNum = 0
        if (pageNum != currentPage) {
            currentPage = pageNum
            if (page != null) page!!.destroy()
            page = null
            if (displayList != null) displayList!!.destroy()
            displayList = null
            page = doc.loadPage(pageNum)
            val b = page!!.bounds
            pageWidth = b.x1 - b.x0
            pageHeight = b.y1 - b.y0
        }
    }

    @Synchronized
    fun getPageSize(pageNum: Int): Size {
        gotoPage(pageNum)
        return Size(pageWidth, pageHeight)
    }

    @Synchronized
    fun drawPage(
        bitmap: Bitmap,
        rect: IntRect,
        scaledHeight: Int,
        pageNum: Int = 0,
    ) {
        gotoPage(pageNum)
        
        (page as? PDFPage)?.apply {
            annotations?.forEach {
                deleteAnnotation(it)
            }
        }

        if (displayList == null && page != null) displayList = page!!.toDisplayList()
        if (displayList == null || page == null) return

        val zoom = (resolution / 72).toFloat()
        val ctm = Matrix(zoom, zoom)
        val bbox = page!!.bounds.transform(ctm)

        val yscale = scaledHeight.toFloat() / (bbox.y1 - bbox.y0)
        ctm.scale(yscale)

        val dev = AndroidDrawDevice(bitmap, rect.left, rect.top)
        try {
            displayList!!.run(dev, ctm, Cookie())
            dev.close()
        } finally {
            dev.destroy()
        }
    }

    @Synchronized
    fun getPageLinks(pageNum: Int): PdfPageLinks {
        gotoPage(pageNum)
        val page = page
        return if (page != null) {
            PdfPageLinks(page.links.orEmpty().toList().map {
                val rect = Rect(
                    left = it.bounds.x0,
                    top = it.bounds.y0,
                    right = it.bounds.x1,
                    bottom = it.bounds.y1
                )
                PdfPageLink(rect, it.uri)
            })
        } else {
            PdfPageLinks(emptyList())
        }
    }

}
