package com.annotatio.maximus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.AnnotationType
import kotlin.math.min

private val presetColors = listOf(
    Color.Black,
    Color.Red,
    Color(0xFF2962FF),
    Color(0xFF00C853),
    Color(0xFFFFAB00)
)

@Composable
fun ToolOptionsPanel(
    activeTool: AnnotationType?,
    selectedColor: Color,
    strokeWidth: Float,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeTool == null || activeTool == AnnotationType.ERASER) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        var customColor by remember { mutableStateOf<Color?>(null) }
        var showColorPicker by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(40.dp)
        ) {
            // Color presets
            presetColors.forEach { color ->
                ColorDot(
                    color = color,
                    isSelected = color == selectedColor,
                    onClick = {
                        onColorSelected(color)
                        customColor = null
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Color wheel (benutzerdefinierte Farbe)
            ColorWheelDot(
                isActive = customColor != null && selectedColor == customColor,
                previewColor = customColor,
                onClick = { showColorPicker = true }
            )

            // Stroke width slider (for pen and highlighter)
            if (activeTool == AnnotationType.PEN || activeTool == AnnotationType.HIGHLIGHTER) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Stärke:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChanged,
                    valueRange = 1f..20f,
                    modifier = Modifier.width(160.dp)
                )
            }
        }

        if (showColorPicker) {
            AlertDialog(
                onDismissRequest = { showColorPicker = false },
                title = { Text("Benutzerdefinierte Farbe") },
                text = {
                    ColorWheel(
                        onColorPicked = { color ->
                            customColor = color
                            onColorSelected(color)
                            showColorPicker = false
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showColorPicker = false }) {
                        Text("Schließen")
                    }
                }
            )
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) else Modifier
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun ColorWheelDot(
    isActive: Boolean,
    previewColor: Color?,
    onClick: () -> Unit
) {
    val borderColor =
        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Transparent, CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (previewColor != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(previewColor)
            )
        } else {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Red,
                            Color.Magenta,
                            Color.Blue,
                            Color.Cyan,
                            Color.Green,
                            Color.Yellow,
                            Color.Red
                        )
                    ),
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

@Composable
private fun ColorWheel(
    onColorPicked: (Color) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(200.dp)
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val size = this.size
                        val center = androidx.compose.ui.geometry.Offset(
                            x = size.width / 2f,
                            y = size.height / 2f
                        )
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val distance =
                            kotlin.math.sqrt(dx * dx + dy * dy)
                        val radius = min(size.width, size.height).toFloat() / 2f
                        val innerRadius = radius * 0.6f

                        if (distance in innerRadius..radius) {
                            val angle =
                                (kotlin.math.atan2(dy, dx) * 180f / kotlin.math.PI.toFloat() + 360f) % 360f
                            val color = Color.hsv(angle, 1f, 1f)
                            onColorPicked(color)
                        }
                    }
                }
        ) {
            val radius = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Red,
                        Color.Magenta,
                        Color.Blue,
                        Color.Cyan,
                        Color.Green,
                        Color.Yellow,
                        Color.Red
                    )
                ),
                radius = radius
            )
            // Innerer Kreis für Ring-Effekt
            drawCircle(
                color = Color.White,
                radius = radius * 0.6f
            )
        }
    }
}
