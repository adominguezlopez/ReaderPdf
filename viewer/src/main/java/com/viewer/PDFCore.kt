package com.viewer

import android.graphics.Bitmap
import android.graphics.PointF
import com.artifex.mupdf.fitz.*
import kotlin.jvm.Synchronized
import com.artifex.mupdf.fitz.android.AndroidDrawDevice

class PDFCore(doc: Document) {
    private var resolution = 0
    private var doc: Document? = null
    private var pageCount = -1
    private var currentPage = 0
    private var page: Page? = null
    private var pageWidth = 0f
    private var pageHeight = 0f
    private var displayList: DisplayList? = null

    /* Default to "A Format" pocket book size. */
    private var layoutW = 312
    private var layoutH = 504
    private var layoutEM = 10

    init {
        refresh(doc)
    }

    fun refresh(doc: Document) {
        this.doc = doc
        doc.layout(layoutW.toFloat(), layoutH.toFloat(), layoutEM.toFloat())
        pageCount = doc.countPages()
        resolution = 160
        currentPage = -1
    }

    fun destroy() {
        doc!!.destroy()
    }

    fun loadPage(pageNum: Int) {
        doc!!.loadPage(pageNum)
    }

    val title: String
        get() = doc!!.getMetaData(Document.META_INFO_TITLE)

    fun countPages(): Int {
        return pageCount
    }

    @get:Synchronized
    val isReflowable: Boolean
        get() = doc!!.isReflowable

    @Synchronized
    fun layout(oldPage: Int, w: Int, h: Int, em: Int): Int {
        if (w != layoutW || h != layoutH || em != layoutEM) {
            println("LAYOUT: $w,$h")
            layoutW = w
            layoutH = h
            layoutEM = em
            val mark = doc!!.makeBookmark(doc!!.locationFromPageNumber(oldPage))
            doc!!.layout(layoutW.toFloat(), layoutH.toFloat(), layoutEM.toFloat())
            currentPage = -1
            pageCount = doc!!.countPages()
            return doc!!.pageNumberFromLocation(doc!!.findBookmark(mark))
        }
        return oldPage
    }

    @Synchronized
    private fun gotoPage(pageNum: Int) {
        /* TODO: page cache */
        var pageNum = pageNum
        if (pageNum > pageCount - 1) pageNum = pageCount - 1 else if (pageNum < 0) pageNum = 0
        if (pageNum != currentPage) {
            currentPage = pageNum
            if (page != null) page!!.destroy()
            page = null
            if (displayList != null) displayList!!.destroy()
            displayList = null
            if (doc != null) {
                page = doc!!.loadPage(pageNum)
                val b = page!!.bounds
                pageWidth = b.x1 - b.x0
                pageHeight = b.y1 - b.y0
            } else {
                page = null
                pageWidth = 0f
                pageHeight = 0f
            }
        }
    }

    @Synchronized
    fun getPageSize(pageNum: Int): PointF {
        gotoPage(pageNum)
        return PointF(pageWidth, pageHeight)
    }

    @Synchronized
    fun onDestroy() {
        if (displayList != null) displayList!!.destroy()
        displayList = null
        if (page != null) page!!.destroy()
        page = null
        if (doc != null) doc!!.destroy()
        doc = null
    }

    @Synchronized
    fun drawPage(
        bm: Bitmap?, pageNum: Int,
        pageW: Int, pageH: Int,
        patchX: Int, patchY: Int,
        patchW: Int, patchH: Int,
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
    fun updatePage(
        bm: Bitmap?, pageNum: Int,
        pageW: Int, pageH: Int,
        patchX: Int, patchY: Int,
        patchW: Int, patchH: Int,
        cookie: Cookie?
    ) {
        drawPage(bm, pageNum, pageW, pageH, patchX, patchY, patchW, patchH, cookie)
    }

    @Synchronized
    fun getPageLinks(pageNum: Int): Array<Link>? {
        gotoPage(pageNum)
        return if (page != null) page!!.links else null
    }

    @Synchronized
    fun addPageLinks(pageNum: Int, links: Array<Link>?) {
        if (links == null || links.size == 0) return
        gotoPage(pageNum)
        for (link in links) {
            page!!.createLink(link.bounds, link.uri)
        }
        return
    }

    @Synchronized
    fun resolveLink(link: Link?): Int {
        return doc!!.pageNumberFromLocation(doc!!.resolveLink(link))
    }

    @Synchronized
    fun searchPage(pageNum: Int, text: String?): Array<Array<Quad>> {
        gotoPage(pageNum)
        return page!!.search(text)
    }

    @Synchronized
    fun needsPassword(): Boolean {
        return doc!!.needsPassword()
    }

    @Synchronized
    fun authenticatePassword(password: String?): Boolean {
        return doc!!.authenticatePassword(password)
    }
}