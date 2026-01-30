package com.example.fanboxviewer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

data class TutorialStep(
    val id: String,
    val title: String,
    val body: String,
)

fun Modifier.tutorialTarget(
    key: String,
    targets: MutableMap<String, Rect>,
): Modifier = onGloballyPositioned { coords ->
    targets[key] = coords.boundsInRoot()
}

@Composable
fun SpotlightTutorialOverlay(
    steps: List<TutorialStep>,
    targetRects: Map<String, Rect>,
    visible: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    spotCornerRadius: Dp = 12.dp,
) {
    if (!visible || steps.isEmpty()) return

    val stepIds = steps.map { it.id }
    var index by rememberSaveable(stepIds) { mutableStateOf(0) }

    LaunchedEffect(steps.size) {
        if (index >= steps.size) index = 0
    }

    val current = steps.getOrNull(index) ?: return
    val rect = targetRects[current.id] ?: return
    val isLast = index == steps.lastIndex

    fun goNext() {
        if (isLast) {
            onFinish()
        } else {
            index += 1
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val gapPx = with(density) { 12.dp.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val radiusPx = with(density) { spotCornerRadius.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .pointerInput(index, steps.size) {
                    detectTapGestures { goNext() }
                }
        ) {
            drawRect(Color.Black.copy(alpha = 0.65f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(radiusPx, radiusPx),
                style = Stroke(width = with(density) { 2.dp.toPx() })
            )
        }

        var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
        val desiredX = rect.center.x - (tooltipSize.width / 2f)
        val clampedX = desiredX.coerceIn(marginPx, widthPx - tooltipSize.width - marginPx)
        val placeBelow = rect.bottom + gapPx + tooltipSize.height <= heightPx - marginPx
        val desiredY = if (placeBelow) rect.bottom + gapPx else rect.top - gapPx - tooltipSize.height
        val clampedY = desiredY.coerceIn(marginPx, heightPx - tooltipSize.height - marginPx)

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .widthIn(max = 320.dp)
                .onGloballyPositioned { coords -> tooltipSize = coords.size },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Text(current.title, style = MaterialTheme.typography.titleMedium)
                Text(current.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { goNext() }) { Text(if (isLast) "終了" else "次へ") }
                }
            }
        }
    }
}
