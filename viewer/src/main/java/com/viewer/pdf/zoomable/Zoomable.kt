package com.viewer.pdf.zoomable

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Customized transform gesture detector.
 *
 * A caller of this function can choose if the pointer events will be consumed.
 * And the caller can implement [onGestureStart] and [onGestureEnd] event.
 *
 * @param panZoomLock This parameter is same as the original detectTransformGestures().
 * @param onGesture If this lambda returns true, the pointer events will be consumed. If it returns
 * false, the pointer events will not be consumed.
 * @param onGestureStart This lambda is called when a gesture starts.
 * @param onGestureEnd This lambda is called when a gesture ends.
 */
private suspend fun PointerInputScope.detectTransformGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, timeMillis: Long) -> Boolean,
    onGestureStart: () -> Unit = {},
    onGestureEnd: () -> Unit = {},
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        onGestureStart()
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        val isConsumed = onGesture(
                            centroid,
                            panChange,
                            zoomChange,
                            event.changes[0].uptimeMillis
                        )
                        if (isConsumed) {
                            event.changes.fastForEach {
                                if (it.positionChanged()) {
                                    it.consume()
                                }
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
        onGestureEnd()
    }
}

/**
 * Modifier function that make the content zoomable.
 *
 * @param zoomState A [ZoomState] object.
 */
fun Modifier.zoomable(
    zoomState: ZoomState,
    onTap: (Offset) -> Unit = {}
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "zoomable"
        properties["zoomState"] = zoomState
    }
) {
    val scope = rememberCoroutineScope()
    Modifier
//        .onSizeChanged { size ->
//            zoomState.setLayoutSize(size.toSize())
//        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = onTap,
                onDoubleTap = { tapOffset ->
                    if (zoomState.scale > 1f) {
                        scope.launch {
                            zoomState.reset(animate = true)
                        }
                    } else {
                        scope.launch {
                            zoomState.animateZoomTo(2f, tapOffset)
                        }
                    }
                }
            )
        }
        .pointerInput(Unit) {
            detectTransformGestures(
                onGestureStart = {
                    scope.launch {
                        zoomState.startGesture()
                    }
                },
                onGesture = { centroid, pan, zoom, timeMillis ->
                    val canConsume = zoomState.canConsumeGesture(pan = pan, zoom = zoom)
                    if (canConsume) {
                        scope.launch {
                            zoomState.applyGesture(
                                pan = pan,
                                zoom = zoom,
                                position = centroid,
                                timeMillis = timeMillis,
                            )
                        }
                    }
                    canConsume
                },
                onGestureEnd = {
                    scope.launch {
                        zoomState.endGesture()
                    }
                }
            )
        }
        .graphicsLayer {
            scaleX = zoomState.scale
            scaleY = zoomState.scale
            translationX = zoomState.offsetX
            translationY = zoomState.offsetY
        }
}
