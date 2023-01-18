package com.viewer

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.FileUriExposedException
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import com.artifex.mupdf.fitz.Cookie
import com.artifex.mupdf.fitz.Link
import com.artifex.mupdf.fitz.Quad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Make our ImageViews opaque to optimize redraw
internal class OpaqueImageView(context: Context?) :
    ImageView(context) {
    override fun isOpaque(): Boolean {
        return true
    }
}

class PageView(
    protected val mContext: Context,
    private val mCore: PDFCore,
    private val mParentSize: Point,
    sharedHqBm: Bitmap?
) : ViewGroup(
    mContext
) {
    private val APP = "MuPDF"
    var page = 0
    protected var mSize // Size of page at minimum zoom
            : Point? = null
    protected var mSourceScale = 0f
    private var mEntire // Image rendered at minimum zoom
            : ImageView? = null
    private var mEntireBm: Bitmap?
    private val mEntireMat: Matrix
    private var mPatchViewSize // View size on the basis of which the patch was created
            : Point? = null
    private var mPatchArea: Rect? = null
    private var mPatch: ImageView? = null
    private var mPatchBm: Bitmap?
    private var mSearchBoxes: Array<Array<Quad>>? = null
    protected var mLinks: Array<Link>? = null
    private var mLinksView: View? = null
    private var mIsBlank = false
    private var mHighlightLinks = false
    private var mBusyIndicator: ProgressBar? = null
    private val mHandler = Handler()

    init {
        setBackgroundColor(BACKGROUND_COLOR)
        mEntireBm = Bitmap.createBitmap(mParentSize.x, mParentSize.y, Bitmap.Config.ARGB_8888)
        mPatchBm = sharedHqBm
        mEntireMat = Matrix()
    }

    fun reinit() {
        // Cancel pending render task
        mIsBlank = true
        page = 0
        if (mSize == null) mSize = mParentSize
        if (mEntire != null) {
            mEntire!!.setImageBitmap(null)
            mEntire!!.invalidate()
        }
        if (mPatch != null) {
            mPatch!!.setImageBitmap(null)
            mPatch!!.invalidate()
        }
        mPatchViewSize = null
        mPatchArea = null
        mSearchBoxes = null
        mLinks = null
    }

    fun releaseResources() {
        reinit()
        if (mBusyIndicator != null) {
            removeView(mBusyIndicator)
            mBusyIndicator = null
        }
    }

    fun releaseBitmaps() {
        reinit()

        // recycle bitmaps before releasing them.
        if (mEntireBm != null) mEntireBm!!.recycle()
        mEntireBm = null
        if (mPatchBm != null) mPatchBm!!.recycle()
        mPatchBm = null
    }

    fun blank(page: Int) {
        reinit()
        this.page = page
        if (mBusyIndicator == null) {
            mBusyIndicator = ProgressBar(mContext)
            mBusyIndicator!!.isIndeterminate = true
            addView(mBusyIndicator)
        }
        setBackgroundColor(BACKGROUND_COLOR)
    }

    fun setPage(page: Int, size: PointF) {
        // Cancel pending render task
        Log.d("setPage", "page $page, size: $size")
        mIsBlank = false
        // Highlights may be missing because mIsBlank was true on last draw
        if (mLinksView != null) mLinksView!!.invalidate()
        this.page = page
        if (mEntire == null) {
            mEntire = OpaqueImageView(mContext)
            mEntire?.scaleType = ImageView.ScaleType.MATRIX
            addView(mEntire)
        }

        // Calculate scaled size that fits within the screen limits
        // This is the size at minimum zoom
        Log.d(
            "imageThing7",
            "== page " + this.page + ", old scale was " + mSourceScale + ". new will be " + Math.min(
                mParentSize.x / size.x, mParentSize.y / size.y
            )
        )
        mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y)
        Log.d("ImageThing15", page.toString() + "setting scale from setPage(): " + mSourceScale)
        val newSize = Point((size.x * mSourceScale).toInt(), (size.y * mSourceScale).toInt())
        mSize = newSize
        mEntire!!.setImageBitmap(null)
        mEntire!!.invalidate()


        setBackgroundColor(BACKGROUND_COLOR)
        mEntire!!.setImageBitmap(null)
        mEntire!!.invalidate()
        if (mBusyIndicator == null) {
            mBusyIndicator = ProgressBar(mContext)
            mBusyIndicator!!.isIndeterminate = true
            addView(mBusyIndicator)
            mBusyIndicator!!.visibility = INVISIBLE
            mHandler.postDelayed({
                if (mBusyIndicator != null) mBusyIndicator!!.visibility = VISIBLE
            }, PROGRESS_DIALOG_DELAY.toLong())
        }

        CoroutineScope(Dispatchers.IO).launch {
            // Render the page in the background
            mCore.drawPage(
                mEntireBm, page, mSize!!.x, mSize!!.y, 0, 0, mSize!!.x, mSize!!.y, Cookie()
            )

            withContext(Dispatchers.Main) {
                removeView(mBusyIndicator)
                mBusyIndicator = null
                Log.d("imageThing", "setPage.onPostExecute ")
                mEntire!!.setImageBitmap(mEntireBm)
                mEntire!!.invalidate()
                setBackgroundColor(Color.TRANSPARENT)
            }
        }

        if (mLinksView == null) {
            mLinksView = object : View(mContext) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    if (!mIsBlank && mLinks != null && mLinks!!.size > 0 && mHighlightLinks) {
                        Log.d(
                            "linksThing",
                            "mSearchView.onDraw page " + page + ". Links: " + if (mLinks == null) 0 else mLinks!!.size
                        )
                        // Work out current total scale factor
                        // from source to view
                        val scale = mSourceScale * width.toFloat() / mSize!!.x.toFloat()
                        val paint = Paint()
                        if (!mIsBlank && mSearchBoxes != null) {
                            paint.color = HIGHLIGHT_COLOR
                            for (searchBox in mSearchBoxes!!) {
                                for (q in searchBox) {
                                    val path = Path()
                                    path.moveTo(q.ul_x * scale, q.ul_y * scale)
                                    path.lineTo(q.ll_x * scale, q.ll_y * scale)
                                    path.lineTo(q.lr_x * scale, q.lr_y * scale)
                                    path.lineTo(q.ur_x * scale, q.ur_y * scale)
                                    path.close()
                                    canvas.drawPath(path, paint)
                                }
                            }
                        }
                        Log.d(
                            "linksThing",
                            "PAINTING LINKS ON PAGE " + page + " scale: " + mSourceScale
                        )
                        Log.d(
                            "linksThing",
                            "size of this " + mLinksView!!.width + " x " + mLinksView!!.height
                        )
                        paint.color = LINK_COLOR
                        for (link in mLinks!!) {
                            Log.d(
                                "linksThing", "painting link page " + page + " " + link.toString()
                            )
                            canvas.drawRect(
                                link.bounds.x0 * scale,
                                link.bounds.y0 * scale,
                                link.bounds.x1 * scale,
                                link.bounds.y1 * scale,
                                paint
                            )
                        }
                    }
                }
            }
            addView(mLinksView)
        }
        requestLayout()
    }

    fun setSearchBoxes(searchBoxes: Array<Array<Quad>>?) {
        mSearchBoxes = searchBoxes
        if (mLinksView != null) mLinksView!!.invalidate()
    }

    fun setLinkHighlighting(f: Boolean) {
        mHighlightLinks = f
        if (mLinksView != null) mLinksView!!.invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val x: Int
        val y: Int
        x = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> mSize!!.x
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        y = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> mSize!!.y
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(x, y)
        if (mBusyIndicator != null) {
            val limit = Math.min(mParentSize.x, mParentSize.y) / 2
            mBusyIndicator!!.measure(MeasureSpec.AT_MOST or limit, MeasureSpec.AT_MOST or limit)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        if (mEntire != null) {
            if (mEntire!!.width != w || mEntire!!.height != h) {
                mEntireMat.setScale(w / mSize!!.x.toFloat(), h / mSize!!.y.toFloat())
                mEntire!!.imageMatrix = mEntireMat
                mEntire!!.invalidate()
            }
            mEntire!!.layout(0, 0, w, h)
        }
        if (mLinksView != null) {
            mLinksView!!.layout(0, 0, w, h)
        }
        if (mPatchViewSize != null) {
            if (mPatchViewSize!!.x != w || mPatchViewSize!!.y != h) {
                // Zoomed since patch was created
                mPatchViewSize = null
                mPatchArea = null
                if (mPatch != null) {
                    mPatch!!.setImageBitmap(null)
                    mPatch!!.invalidate()
                }
            } else {
                mPatch!!.layout(
                    mPatchArea!!.left, mPatchArea!!.top, mPatchArea!!.right, mPatchArea!!.bottom
                )
            }
        }
        if (mBusyIndicator != null) {
            val bw = mBusyIndicator!!.measuredWidth
            val bh = mBusyIndicator!!.measuredHeight
            mBusyIndicator!!.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
        }
    }

    fun updateHq(update: Boolean) {
        val viewArea = Rect(left, top, right, bottom)
        if (viewArea.width() == mSize!!.x || viewArea.height() == mSize!!.y) {
            // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
            if (mPatch != null) {
                mPatch!!.setImageBitmap(null)
                mPatch!!.invalidate()
            }
        } else {
            val patchViewSize = Point(viewArea.width(), viewArea.height())
            val patchArea = Rect(0, 0, mParentSize.x, mParentSize.y)

            // Intersect and test that there is an intersection
            if (!patchArea.intersect(viewArea)) return

            // Offset patch area to be relative to the view top left
            patchArea.offset(-viewArea.left, -viewArea.top)
            val area_unchanged = patchArea == mPatchArea && patchViewSize == mPatchViewSize

            // If being asked for the same area as last time and not because of an update then nothing to do
            if (area_unchanged && !update) return
            val completeRedraw = !(area_unchanged && update)

            // Create and add the image view if not already done
            if (mPatch == null) {
                mPatch = OpaqueImageView(mContext)
                mPatch?.scaleType = ImageView.ScaleType.MATRIX
                addView(mPatch)
                if (mLinksView != null) mLinksView!!.bringToFront()
            }

            CoroutineScope(Dispatchers.IO).launch {
                if (completeRedraw) {
                    mCore.drawPage(
                        mPatchBm,
                        page,
                        patchViewSize.x,
                        patchViewSize.y,
                        patchArea.left,
                        patchArea.top,
                        patchArea.width(),
                        patchArea.height(),
                        Cookie()
                    )
                } else {
                    mCore.updatePage(
                        mPatchBm,
                        page,
                        patchViewSize.x,
                        patchViewSize.y,
                        patchArea.left,
                        patchArea.top,
                        patchArea.width(),
                        patchArea.height(),
                        Cookie()
                    )
                }
                withContext(Dispatchers.Main) {
                    mPatchViewSize = patchViewSize
                    mPatchArea = patchArea
                    Log.d("imageThing", "updateHq.onPostExecute ")
                    mPatch!!.setImageBitmap(mPatchBm)
                    mPatch!!.invalidate()
                    //requestLayout();
                    // Calling requestLayout here doesn't lead to a later call to layout. No idea
                    // why, but apparently others have run into the problem.
                    mPatch!!.layout(
                        mPatchArea!!.left, mPatchArea!!.top, mPatchArea!!.right, mPatchArea!!.bottom
                    )
                }
            }
        }
    }

    fun updateLinks(links: Array<Link>?) {
        Log.d("linksThing2", "updating page " + page + " links " + (links?.size ?: 0))
        mLinks = links
        refreshLinksView()
        Log.d(
            "linksThing12",
            page.toString() + " getting links from updateLinks() " + if (mLinks == null) 0 else mLinks!!.size
        )
    }

    private fun refreshLinksView() {
        if (mLinks != null && mLinks!!.size > 0 && mLinksView != null) {
            mLinksView!!.invalidate()
        }
    }

    fun update() {
        // Cancel pending render task
        CoroutineScope(Dispatchers.IO).launch {
            val size = mCore.getPageSize(page)

            withContext(Dispatchers.Main) {
                Log.d("imageThing2", page.toString() + " size: " + size.x + "")
                mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y)
                Log.d(
                    "ImageThing15",
                    page.toString() + "setting scale from onPostExecute: " + mSourceScale
                )
                refreshLinksView()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            mCore.updatePage(
                mEntireBm, page, mSize!!.x, mSize!!.y, 0, 0, mSize!!.x, mSize!!.y, Cookie()
            )

            withContext(Dispatchers.Main) {
                Log.d("imageThing", "update.onPostExecute ")
                Log.d("sourceScaleThing", "mEntireBm2.getWidth(): " + mEntireBm!!.width)
                Log.d("sourceScaleThing", "mEntireBm2.getWidth(): " + mEntireBm!!.width)
                Log.d("sourceScaleThing", "mEntireBm2.mSize.x: " + mSize!!.x)
                Log.d("sourceScaleThing", "mEntireBm2.mSize.y: " + mSize!!.y)
                mEntire!!.setImageBitmap(mEntireBm)
                mEntire!!.invalidate()
            }
        }

        updateHq(true)
    }

    fun removeHq() {

        // And get rid of it
        mPatchViewSize = null
        mPatchArea = null
        if (mPatch != null) {
            mPatch!!.setImageBitmap(null)
            mPatch!!.invalidate()
        }
    }

    override fun isOpaque(): Boolean {
        return true
    }

    fun hitLink(link: Link): Int {
        return if (link.isExternal) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET) // API>=21: FLAG_ACTIVITY_NEW_DOCUMENT
            try {
                mContext.startActivity(intent)
            } catch (x: FileUriExposedException) {
                Log.e(APP, x.toString())
                Toast.makeText(
                    context,
                    "Android does not allow following file:// link: " + link.uri,
                    Toast.LENGTH_LONG
                ).show()
            } catch (x: Throwable) {
                Log.e(APP, x.toString())
                Toast.makeText(context, x.message, Toast.LENGTH_LONG).show()
            }
            0
        } else {
            mCore.resolveLink(link)
        }
    }

    fun hitLink(x: Float, y: Float): Int {
        // Since link highlighting was implemented, the super class
        // PageView has had sufficient information to be able to
        // perform this method directly. Making that change would
        // make PDFCore.hitLinkPage superfluous.
        val scale = mSourceScale * width.toFloat() / mSize!!.x.toFloat()
        val docRelX = (x - left) / scale
        val docRelY = (y - top) / scale
        if (mLinks != null) for (l in mLinks!!) if (l.bounds.contains(
                docRelX, docRelY
            )
        ) return hitLink(l)
        return 0
    }

    companion object {
        private const val HIGHLIGHT_COLOR = -0x7f339a00
        private const val LINK_COLOR = -0x7fff9934
        private const val BOX_COLOR = -0xbbbb01
        private const val BACKGROUND_COLOR = -0x1
        private const val PROGRESS_DIALOG_DELAY = 200
    }
}