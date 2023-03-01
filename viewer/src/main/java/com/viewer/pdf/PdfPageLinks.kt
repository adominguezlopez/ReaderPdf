package com.viewer.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.viewer.pdf.util.scale

class PdfPageLinks(links: List<PdfPageLink>) {
    var baseLinks = links
        private set

    private var scaledLinks: List<PdfPageLink>? = null

    fun setBaseScale(scale: Float) {
        baseLinks = baseLinks.scale(scale)
        scaledLinks = null
    }

    fun scale(scale: Float) {
        scaledLinks = baseLinks.scale(scale)
    }

    fun resetScale() {
        scaledLinks = null
    }

    fun findLink(position: Offset): PdfPageLink? {
        (scaledLinks ?: baseLinks).forEach { link ->
            if (link.bounds.contains(position)) {
                return link
            }
        }
        return null
    }

    private fun List<PdfPageLink>.scale(scale: Float): List<PdfPageLink> {
        return map { link ->
            link.copy(bounds = link.bounds.scale(scale))
        }
    }
}

data class PdfPageLink(
    val bounds: Rect,
    val uri: String,
)
