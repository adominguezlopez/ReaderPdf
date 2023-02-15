package com.viewer.pdf.zoomable

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.lang.Float.max
import kotlin.math.abs

/**
 * A state object that manage scale and offset.
 *
 * @param maxScale The maximum scale of the content.
 * @param contentSize Size of content (i.e. image size.) If Zero, the composable layout size will
 * be used as content size.
 * @param velocityDecay The decay animation spec for fling behaviour.
 */
@Stable
class ZoomState(
    @FloatRange(from = 1.0) private val maxScale: Float = 5f,
    private var contentSize: Size = Size.Zero,
    private val absVelocityThreshold: Float = 40f,
    private val frictionMultiplier: Float = 2f
) {
    init {
        require(maxScale >= 1.0f) { "maxScale must be at least 1.0." }
    }

    private var _scale = Animatable(1f).apply {
        updateBounds(0.9f, maxScale)
    }
    /**
     * The scale of the content.
     */
    val scale: Float
        get() = _scale.value

    private var _offsetX = Animatable(0f)
    /**
     * The horizontal offset of the content.
     */
    val offsetX: Float
        get() = _offsetX.value

    val boundsX: Float
        get() = abs(_offsetX.lowerBound ?: 0f)

    val boundsY: Float
        get() = abs(_offsetY.lowerBound ?: 0f)

    private var _offsetY = Animatable(0f)
    /**
     * The vertical offset of the content.
     */
    val offsetY: Float
        get() = _offsetY.value

    private var layoutSize = Size.Zero

    private var _isDragInProgress by mutableStateOf(false)

    val isSettled: Boolean
        get() = !_isDragInProgress && !_scale.isRunning && !_offsetX.isRunning && !_offsetY.isRunning

    /**
     * Set composable layout size.
     *
     * Basically This function is called from [Modifier.zoomable] only.
     *
     * @param size The size of composable layout size.
     */
    fun setLayoutSize(size: Size) {
        layoutSize = size
        updateFitContentSize()
    }

    /**
     * Set the content size.
     *
     * @param size The content size, for example an image size in pixel.
     */
    fun setContentSize(size: Size) {
        contentSize = size
        updateFitContentSize()
    }

    private var fitContentSize = Size.Zero
    private fun updateFitContentSize() {
        if (layoutSize == Size.Zero) {
            fitContentSize = Size.Zero
            return
        }

        if (contentSize == Size.Zero) {
            fitContentSize = layoutSize
            return
        }

        val contentAspectRatio = contentSize.width / contentSize.height
        val layoutAspectRatio = layoutSize.width / layoutSize.height

        fitContentSize = if (contentAspectRatio > layoutAspectRatio) {
            contentSize * (layoutSize.width / contentSize.width)
        } else {
            contentSize * (layoutSize.height / contentSize.height)
        }
    }

    /**
     * Reset the scale and the offsets.
     */
    suspend fun reset(animate: Boolean = false) = coroutineScope {
        if (animate) {
            launch {
                _scale.animateTo(1f)
            }

            launch {
                _offsetX.animateTo(0f)
            }.invokeOnCompletion {
                _offsetX.updateBounds(0f, 0f)
            }

            launch {
                _offsetY.animateTo(0f)
            }.invokeOnCompletion {
                _offsetY.updateBounds(0f, 0f)
            }
        } else {
            launch { _scale.snapTo(1f) }
            _offsetX.updateBounds(0f, 0f)
            launch { _offsetX.snapTo(0f) }
            _offsetY.updateBounds(0f, 0f)
            launch { _offsetY.snapTo(0f) }
        }
    }

    private var shouldConsumeEvent: Boolean? = null

    internal suspend fun startGesture() = coroutineScope {
        shouldConsumeEvent = null
        velocityTracker.resetTracking()
        launch {
            _scale.stop()
            _offsetX.stop()
            _offsetY.stop()
        }
    }

    internal fun canConsumeGesture(pan: Offset, zoom: Float): Boolean {
        return shouldConsumeEvent ?: run {
            var consume = true
            if (zoom == 1f) { // One finger gesture
                if (scale == 1f) {  // Not zoomed
                    consume = false
                } else {
                    val ratio = (abs(pan.x) / abs(pan.y))
                    if (ratio > 3) {   // Horizontal drag
                        if ((pan.x < 0) && (_offsetX.value == _offsetX.lowerBound)) {
                            // Drag R to L when right edge of the content is shown.
                            consume = false
                        }
                        if ((pan.x > 0) && (_offsetX.value == _offsetX.upperBound)) {
                            // Drag L to R when left edge of the content is shown.
                            consume = false
                        }
                    }
                }
            }
            shouldConsumeEvent = consume
            consume
        }
    }

    private val velocityTracker = VelocityTracker()
    private var shouldFling = true

    internal suspend fun applyGesture(
        pan: Offset,
        zoom: Float,
        position: Offset,
        timeMillis: Long
    ) = coroutineScope {
        _isDragInProgress = true
        val size = fitContentSize * scale
        val newScale = (scale * zoom).coerceIn(0.9f, maxScale)
        val newSize = fitContentSize * newScale
        val deltaWidth = newSize.width - size.width
        val deltaHeight = newSize.height - size.height

        // Position with the origin at the left top corner of the content.
        val xInContent = position.x - offsetX + (size.width - layoutSize.width) * 0.5f
        val yInContent = position.y - offsetY + (size.height - layoutSize.height) * 0.5f
        // Offset to zoom the content around the pinch gesture position.
        val newOffsetX = (deltaWidth * 0.5f) - (deltaWidth * xInContent / size.width)
        val newOffsetY = (deltaHeight * 0.5f) - (deltaHeight * yInContent / size.height)

        val boundX = max((newSize.width - layoutSize.width), 0f) * 0.5f
        _offsetX.updateBounds(-boundX, boundX)
        launch {
            _offsetX.snapTo(offsetX + pan.x + newOffsetX)
        }

        val boundY = max((newSize.height - layoutSize.height), 0f) * 0.5f
        _offsetY.updateBounds(-boundY, boundY)
        launch {
            _offsetY.snapTo(offsetY + pan.y + newOffsetY)
        }

        launch {
            _scale.snapTo(newScale)
        }

        velocityTracker.addPosition(timeMillis, position)

        if (zoom != 1f) {
            shouldFling = false
        }
    }

    internal suspend fun endGesture() = coroutineScope {
        if (shouldConsumeEvent == true && shouldFling) {
            val velocity = velocityTracker.calculateVelocity()
            launch {
                _offsetX.animateDecay(velocity.x, exponentialDecay(absVelocityThreshold = absVelocityThreshold, frictionMultiplier = frictionMultiplier))
            }
            launch {
                _offsetY.animateDecay(velocity.y, exponentialDecay(absVelocityThreshold = absVelocityThreshold, frictionMultiplier = frictionMultiplier))
            }
            velocityTracker.resetTracking()
        }
        shouldFling = true

        if (_scale.value < 1f) {
            launch {
                _scale.animateTo(1f)
            }
        }
    }.apply {
        _isDragInProgress = false
    }

    internal suspend fun animateZoomTo(zoom: Float, offset: Offset) = coroutineScope {
        val size = fitContentSize * scale
        val newScale = (scale * zoom).coerceIn(1f, maxScale)
        val newSize = fitContentSize * newScale
        val deltaWidth = newSize.width - size.width
        val deltaHeight = newSize.height - size.height

        // Position with the origin at the left top corner of the content.
        val xInContent = offset.x - offsetX + (size.width - layoutSize.width) * 0.5f
        val yInContent = offset.y - offsetY + (size.height - layoutSize.height) * 0.5f
        // Offset to zoom the content around the pinch gesture position.
        val newOffsetX = (deltaWidth * 0.5f) - (deltaWidth * xInContent / size.width)
        val newOffsetY = (deltaHeight * 0.5f) - (deltaHeight * yInContent / size.height)

        val boundX = max((newSize.width - layoutSize.width), 0f) * 0.5f
        _offsetX.updateBounds(-boundX, boundX)
        launch {
            _offsetX.animateTo(newOffsetX)
        }

        val boundY = max((newSize.height - layoutSize.height), 0f) * 0.5f
        _offsetY.updateBounds(-boundY, boundY)
        launch {
            _offsetY.animateTo(newOffsetY)
        }

        launch {
            _scale.animateTo(newScale)
        }
    }

    fun getOffsetInContent(absoluteOffset: Offset): Offset {
        val size = fitContentSize * scale
        val xInContent = absoluteOffset.x - offsetX + (size.width - layoutSize.width) * 0.5f
        val yInContent = absoluteOffset.y - offsetY + (size.height - layoutSize.height) * 0.5f
        return Offset(xInContent, yInContent)
    }
}
