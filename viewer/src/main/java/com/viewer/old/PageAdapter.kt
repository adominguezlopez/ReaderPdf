package com.viewer.old

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.PointF
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.artifex.mupdf.fitz.Link
import com.viewer.pdf.PdfCore
import kotlinx.coroutines.*
import java.util.HashMap

class PageAdapter(private val mContext: Context, private val mCore: PdfCore) : BaseAdapter(),
    CoroutineScope by MainScope() {
    private val mPageSizes = HashMap<Int, PointF>()
    private val mLinks = HashMap<Int, Array<Link>?>()
    private var mSharedHqBm: Bitmap? = null
    private var size = 0

    init {
        size = mCore.countPages()
    }

    override fun getCount(): Int {
        return size
    }

    override fun getItem(position: Int): Any {
        return Any()
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    fun releaseBitmaps() {
        //  recycle and release the shared bitmap.
        if (mSharedHqBm != null) mSharedHqBm!!.recycle()
        mSharedHqBm = null
    }

    fun reset() {
        mPageSizes.clear()
        size = 0
        notifyDataSetChanged()
    }

    fun removePageSize(position: Int) {
        mPageSizes.remove(position)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val pageView: PageView
        if (convertView == null) {
            if (mSharedHqBm == null || mSharedHqBm!!.width != parent.width || mSharedHqBm!!.height != parent.height) mSharedHqBm =
                Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888)
            pageView = PageView(mContext, mCore, Point(parent.width, parent.height), mSharedHqBm)
        } else {
            pageView = convertView as PageView
        }

        fetchPageSize(pageView, position)
        fetchLinks(pageView, position)

        return pageView
    }

    private fun fetchPageSize(pageView: PageView, position: Int) {
        val pageSize = mPageSizes[position]
        if (pageSize != null) {
            // We already know the page size. Set it up
            // immediately
            Log.d("ImageThing15", "${position} page size from cache: ${pageSize.x}")

            pageView.setPage(position, pageSize)
        } else {
            // Page size as yet unknown. Blank it for now, and
            // start a background task to find the size
            pageView.blank(position)

            CoroutineScope(Dispatchers.IO).launch {
                val result = mCore.getPageSize(position)
                Log.d("ImageThing15", "${position} page size updated to: ${result.x}")

                // We now know the page size
                mPageSizes[position] = result

                // Check that this view hasn't been reused for
                // another page since we started
                withContext(Dispatchers.Main) {
                    if (pageView.page == position) pageView.setPage(position, result)
                }
            }
        }
    }

    private fun fetchLinks(pageView: PageView, position: Int) {
        if (mLinks.containsKey(position)) {
            val links = mLinks[position]
            Log.d("settingLinks", "${links?.size} links found in page ${position}")
            pageView.updateLinks(links)
        } else {
            CoroutineScope(Dispatchers.IO).launch {

                Log.d("settingLinks", "links not found in page ${position}. Fetching...")
                var links = mCore.getPageLinks(position)
                if (links == null) links = arrayOf()
                Log.d("settingLinks", "... ${links.size} links found!")
                mLinks[position] = links
                if (pageView.page == position)
                    pageView.updateLinks(links)
            }
        }
    }

    fun setLinks(links: Array<Link>?, position: Int) {
        mLinks[position] = links
    }
}