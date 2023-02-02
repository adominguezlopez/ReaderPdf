/*
 * Copyright 2022 usuiat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viewer.presenter.pager.zoomable

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
    private val absVelocityThreshold: Float = 20f
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

    internal var isDragInProgress by mutableStateOf(false)
        private set
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
                _offsetX.updateBounds(0f, 0f)
            }

            launch {
                _offsetY.animateTo(0f)
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

    internal fun startGesture() {
        isDragInProgress = true
        shouldConsumeEvent = null
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
                    else if (ratio < 0.33) { // Vertical drag
                        if ((pan.y < 0) && (_offsetY.value == _offsetY.lowerBound)) {
                            // Drag bottom to top when bottom edge of the content is shown.
                            consume = false
                        }
                        if ((pan.y > 0) && (_offsetY.value == _offsetY.upperBound)) {
                            // Drag top to bottom when top edge of the content is shown.
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
        launch {
            _scale.snapTo(_scale.value * zoom)
        }

        val boundX = java.lang.Float.max((fitContentSize.width * _scale.value - layoutSize.width), 0f) / 2f
        _offsetX.updateBounds(-boundX, boundX)
        launch {
            _offsetX.snapTo(_offsetX.value + pan.x)
        }

        val boundY = java.lang.Float.max((fitContentSize.height * _scale.value - layoutSize.height), 0f) / 2f
        _offsetY.updateBounds(-boundY, boundY)
        launch {
            _offsetY.snapTo(_offsetY.value + pan.y)
        }

        velocityTracker.addPosition(timeMillis, position)

        if (zoom != 1f) {
            shouldFling = false
        }
    }

    internal suspend fun endGesture() = coroutineScope {
        if (shouldFling) {
            val velocity = velocityTracker.calculateVelocity()
            launch {
                _offsetX.animateDecay(velocity.x, exponentialDecay(absVelocityThreshold = absVelocityThreshold))
            }
            launch {
                _offsetY.animateDecay(velocity.y, exponentialDecay(absVelocityThreshold = absVelocityThreshold))
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
        isDragInProgress = false
    }

    internal suspend fun animateZoomTo(zoom: Float, offset: Offset) = coroutineScope {
        isDragInProgress = true
        launch {
            _scale.animateTo(zoom)
        }

        val boundX = java.lang.Float.max((fitContentSize.width * zoom - layoutSize.width), 0f) / 2f
        _offsetX.updateBounds(-boundX, boundX)
        launch {
            val positionX = -(offset.x - boundX) - (fitContentSize.width - layoutSize.width)
            _offsetX.animateTo(positionX)
        }

        val boundY =
            java.lang.Float.max((fitContentSize.height * zoom - layoutSize.height), 0f) / 2f
        _offsetY.updateBounds(-boundY, boundY)
        launch {
            val positionY = -(offset.y - boundY) - (fitContentSize.height - layoutSize.height)
            _offsetY.animateTo(positionY)
        }
    }.apply {
        isDragInProgress = false
    }
}

/**
 * Creates a [ZoomState] that is remembered across compositions.
 *
 * @param maxScale The maximum scale of the content.
 * @param contentSize Size of content (i.e. image size.) If Zero, the composable layout size will
 * be used as content size.
 * @param velocityDecay The decay animation spec for fling behaviour.
 */
@Composable
fun rememberZoomState(
    @FloatRange(from = 1.0) maxScale: Float = 6f,
    contentSize: Size = Size.Zero,
) = remember {
    ZoomState(maxScale, contentSize)
}
