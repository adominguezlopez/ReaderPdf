package com.viewer.pdf

import android.graphics.Bitmap
import android.graphics.PointF
import com.artifex.mupdf.fitz.*
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File

class PdfCore(
    file: File,
    password: String? = null,
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
        password?.let { doc.authenticatePassword(it) }
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
    fun getPageSize(pageNum: Int): PointF {
        gotoPage(pageNum)
        return PointF(pageWidth, pageHeight)
    }

    @Synchronized
    fun drawPage(
        bm: Bitmap?, pageNum: Int,
        pageW: Int, pageH: Int,
        patchX: Int, patchY: Int,
        cookie: Cookie?
    ) {
        gotoPage(pageNum)
        if (displayList == null && page != null) displayList = page!!.toDisplayList()
        if (displayList == null || page == null) return
        val zoom = (resolution / 72).toFloat()
        val ctm = Matrix(zoom, zoom)
        val bbox = RectI(page!!.bounds.transform(ctm))
        val xscale = pageW.toFloat() / (bbox.x1 - bbox.x0).toFloat()
        val yscale = pageH.toFloat() / (bbox.y1 - bbox.y0).toFloat()
        ctm.scale(xscale, yscale)
        val dev = AndroidDrawDevice(bm, patchX, patchY)
        try {
            displayList!!.run(dev, ctm, cookie)
            dev.close()
        } finally {
            dev.destroy()
        }
    }

    @Synchronized
    fun getPageLinks(pageNum: Int): Array<Link>? {
        gotoPage(pageNum)
        return if (page != null) page!!.links else null
    }
}