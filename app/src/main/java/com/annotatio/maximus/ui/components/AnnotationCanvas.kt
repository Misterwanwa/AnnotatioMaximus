package com.annotatio.maximus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.model.DrawingPath
import com.annotatio.maximus.model.PathPoint
import com.annotatio.maximus.model.ShapeType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun AnnotationCanvas(
    annotations: List<Annotation>,
    currentPageIndex: Int,
    activeTool: AnnotationType,
    penColor: Color,
    penStrokeWidth: Float,
    pageInfo: PdfPageInfo,
    onAddAnnotation: (Annotation) -> Unit,
    onRemoveAnnotation: (String) -> Unit,
    onRequestTextNote: (Float, Float) -> Unit,
    onRequestSignature: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val gestureModifier = when (activeTool) {
        AnnotationType.PEN, AnnotationType.HIGHLIGHTER -> {
            Modifier.pointerInput(activeTool, penColor, penStrokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPoints = listOf(screenToNormalized(offset, pageInfo))
                        dragStart = null
                        dragCurrent = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val normalized = screenToNormalized(change.position, pageInfo)
                        currentPoints = currentPoints + normalized
                    },
                    onDragEnd = {
                        if (currentPoints.size >= 2) {
                            val isHighlighter = activeTool == AnnotationType.HIGHLIGHTER
                            val annotation = Annotation.Stroke(
                                pageIndex = currentPageIndex,
                                path = DrawingPath(
                                    points = currentPoints,
                                    color = penColor,
                                    strokeWidth = if (isHighlighter) penStrokeWidth * 3f
                                    else penStrokeWidth,
                                    isHighlighter = isHighlighter
                                )
                            )
                            onAddAnnotation(annotation)
                        }
                        currentPoints = emptyList()
                    },
                    onDragCancel = {
                        currentPoints = emptyList()
                    }
                )
            }
        }

        AnnotationType.CIRCLE, AnnotationType.SQUARE, AnnotationType.RECTANGLE, AnnotationType.TRIANGLE -> {
            Modifier.pointerInput(activeTool, penColor, penStrokeWidth) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        dragCurrent = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragCurrent = change.position
                    },
                    onDragEnd = {
                        if (dragStart != null && dragCurrent != null) {
                            val startNormalized = screenToNormalized(dragStart!!, pageInfo)
                            val endNormalized = screenToNormalized(dragCurrent!!, pageInfo)
                            val x = minOf(startNormalized.x, endNormalized.x)
                            val y = minOf(startNormalized.y, endNormalized.y)
                            val width = abs(endNormalized.x - startNormalized.x)
                            val height = abs(endNormalized.y - startNormalized.y)
                            if (width > 0.01f && height > 0.01f) {
                                val shapeType = when (activeTool) {
                                    AnnotationType.CIRCLE -> ShapeType.CIRCLE
                                    AnnotationType.SQUARE -> ShapeType.SQUARE
                                    AnnotationType.RECTANGLE -> ShapeType.RECTANGLE
                                    AnnotationType.TRIANGLE -> ShapeType.TRIANGLE
                                    else -> ShapeType.RECTANGLE
                                }
                                val annotation = Annotation.Shape(
                                    pageIndex = currentPageIndex,
                                    type = shapeType,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    color = penColor,
                                    strokeWidth = penStrokeWidth
                                )
                                onAddAnnotation(annotation)
                            }
                        }
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragCurrent = null
                    }
                )
            }
        }

        AnnotationType.TEXT_NOTE -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestTextNote(normalized.x, normalized.y)
                }
            }
        }

        AnnotationType.SIGNATURE -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestSignature(normalized.x, normalized.y)
                }
            }
        }

        AnnotationType.ERASER -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    val threshold = 0.03f
                    val hit = annotations.findLast { annotation ->
                        when (annotation) {
                            is Annotation.Stroke -> {
                                annotation.path.points.any { pt ->
                                    abs(pt.x - normalized.x) < threshold &&
                                            abs(pt.y - normalized.y) < threshold
                                }
                            }

                            is Annotation.HighlightRect -> {
                                normalized.x in annotation.x..(annotation.x + annotation.width) &&
                                        normalized.y in annotation.y..(annotation.y + annotation.height)
                            }

                            is Annotation.TextNote -> {
                                abs(annotation.x - normalized.x) < threshold * 2 &&
                                        abs(annotation.y - normalized.y) < threshold * 2
                            }

                            is Annotation.Shape -> {
                                normalized.x in annotation.x..(annotation.x + annotation.width) &&
                                        normalized.y in annotation.y..(annotation.y + annotation.height)
                            }

                            is Annotation.Signature -> {
                                normalized.x in annotation.x..(annotation.x + annotation.width) &&
                                        normalized.y in annotation.y..(annotation.y + annotation.height)
                            }
                        }
                    }
                    hit?.let { onRemoveAnnotation(it.id) }
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(gestureModifier)
    ) {
        // Draw existing annotations
        for (annotation in annotations) {
            drawAnnotation(annotation, pageInfo)
        }

        // Draw in-progress stroke
        if (currentPoints.size >= 2) {
            val isHighlighter = activeTool == AnnotationType.HIGHLIGHTER
            drawPathFromPoints(
                points = currentPoints,
                color = if (isHighlighter) penColor.copy(alpha = 0.35f) else penColor,
                strokeWidth = if (isHighlighter) penStrokeWidth * 3f else penStrokeWidth,
                pageInfo = pageInfo
            )
        }

        // Draw in-progress shape preview
        val start = dragStart
        val current = dragCurrent
        if (start != null && current != null) {
            val left = minOf(start.x, current.x)
            val top = minOf(start.y, current.y)
            val width = abs(current.x - start.x)
            val height = abs(current.y - start.y)
            val previewColor = penColor.copy(alpha = 0.5f)
            when (activeTool) {
                AnnotationType.CIRCLE -> {
                    val radius = min(width, height) / 2f
                    val center = Offset(left + width / 2f, top + height / 2f)
                    drawCircle(
                        color = previewColor,
                        radius = radius,
                        center = center,
                        style = Stroke(width = penStrokeWidth)
                    )
                }
                AnnotationType.SQUARE -> {
                    val side = min(width, height)
                    drawRect(
                        color = previewColor,
                        topLeft = Offset(left, top),
                        size = Size(side, side),
                        style = Stroke(width = penStrokeWidth)
                    )
                }
                AnnotationType.RECTANGLE -> {
                    drawRect(
                        color = previewColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = penStrokeWidth)
                    )
                }
                AnnotationType.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(left + width / 2f, top)
                        lineTo(left, top + height)
                        lineTo(left + width, top + height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = previewColor,
                        style = Stroke(width = penStrokeWidth)
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun AnnotationDisplayOverlay(
    annotations: List<Annotation>,
    pageInfo: PdfPageInfo,
    modifier: Modifier = Modifier
) {
    if (annotations.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        for (annotation in annotations) {
            drawAnnotation(annotation, pageInfo)
        }
    }
}

@Composable
fun AnnotationLayerOverlay(
    annotations: List<Annotation>,
    pageInfo: PdfPageInfo,
    selectedId: String?,
    onSelectedChange: (String?) -> Unit,
    onUpdateAnnotation: (Annotation) -> Unit,
    onRemoveAnnotation: (String) -> Unit,
    onCopyAnnotation: (Annotation) -> Unit,
    modifier: Modifier = Modifier
) {
    if (annotations.isEmpty()) return

    val density = LocalDensity.current
    val strokeAnnotations = annotations.filterIsInstance<Annotation.Stroke>()
    val selected = strokeAnnotations.find { it.id == selectedId }
    val selectedBounds = selected?.let { computeStrokeBoundsScreen(it, pageInfo) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(strokeAnnotations, pageInfo) {
                    detectTapGestures { offset ->
                        // Hit test: last stroke whose bounds contain the tap
                        val hit = strokeAnnotations.findLast { stroke ->
                            val bounds = computeStrokeBoundsScreen(stroke, pageInfo)
                            bounds.contains(offset)
                        }
                        onSelectedChange(hit?.id)
                    }
                }
        ) {
            // Draw all annotations as gewohnt
            for (annotation in annotations) {
                drawAnnotation(annotation, pageInfo)
            }

            // Draw selection rectangle for the selected stroke
            selectedBounds?.let { rect ->
                drawRect(
                    color = Color.Cyan.copy(alpha = 0.7f),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Square,
                        join = StrokeJoin.Miter
                    )
                )
            }
        }

        // Control buttons around the selected layer
        if (selected != null && selectedBounds != null) {
            val rect = selectedBounds
            val stepPx = 10f

            fun moveStroke(dx: Float, dy: Float) {
                if (pageInfo.pageWidth <= 0f || pageInfo.pageHeight <= 0f) return
                val dxNorm = dx / pageInfo.pageWidth
                val dyNorm = dy / pageInfo.pageHeight
                val movedPoints = selected.path.points.map { pt ->
                    PathPoint(pt.x + dxNorm, pt.y + dyNorm)
                }
                val updated = selected.copy(
                    path = selected.path.copy(points = movedPoints)
                )
                onUpdateAnnotation(updated)
            }

            fun rotateStroke(angleDegrees: Float) {
                if (selected.path.points.isEmpty()) return
                // Rotate in normalized space um das Zentrum der Bounds
                val normBounds = computeStrokeBoundsNormalized(selected)
                val cx = normBounds.center.x
                val cy = normBounds.center.y
                val rad = angleDegrees / 180f * PI.toFloat()
                val cosA = cos(rad)
                val sinA = sin(rad)

                val rotatedPoints = selected.path.points.map { pt ->
                    val x = pt.x - cx
                    val y = pt.y - cy
                    val rx = x * cosA - y * sinA
                    val ry = x * sinA + y * cosA
                    PathPoint(
                        x = cx + rx,
                        y = cy + ry
                    )
                }
                val updated = selected.copy(
                    path = selected.path.copy(points = rotatedPoints)
                )
                onUpdateAnnotation(updated)
            }

            val upOffset = with(density) {
                DpOffset(
                    x = rect.center.x.toDp(),
                    y = (rect.top - 32.dp.toPx()).coerceAtLeast(0f).toDp()
                )
            }
            val downOffset = with(density) {
                DpOffset(
                    x = rect.center.x.toDp(),
                    y = (rect.bottom + 8.dp.toPx()).toDp()
                )
            }
            val leftOffset = with(density) {
                DpOffset(
                    x = (rect.left - 32.dp.toPx()).coerceAtLeast(0f).toDp(),
                    y = rect.center.y.toDp()
                )
            }
            val rightOffset = with(density) {
                DpOffset(
                    x = (rect.right + 8.dp.toPx()).toDp(),
                    y = rect.center.y.toDp()
                )
            }
            val deleteOffset = with(density) {
                DpOffset(
                    x = (rect.right + 8.dp.toPx()).toDp(),
                    y = (rect.top - 32.dp.toPx()).coerceAtLeast(0f).toDp()
                )
            }
            val copyOffset = with(density) {
                DpOffset(
                    x = (rect.right + 8.dp.toPx()).toDp(),
                    y = (rect.bottom + 8.dp.toPx()).toDp()
                )
            }
            val rotateOffset = with(density) {
                DpOffset(
                    x = (rect.left - 32.dp.toPx()).coerceAtLeast(0f).toDp(),
                    y = (rect.bottom + 8.dp.toPx()).toDp()
                )
            }

            // Pfeil-Buttons (Bewegen)
            IconButton(
                onClick = { moveStroke(0f, -stepPx) },
                modifier = Modifier
                    .offset(x = upOffset.x - 16.dp, y = upOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "Ebene nach oben bewegen",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { moveStroke(0f, stepPx) },
                modifier = Modifier
                    .offset(x = downOffset.x - 16.dp, y = downOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Ebene nach unten bewegen",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { moveStroke(-stepPx, 0f) },
                modifier = Modifier
                    .offset(x = leftOffset.x - 16.dp, y = leftOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Ebene nach links bewegen",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = { moveStroke(stepPx, 0f) },
                modifier = Modifier
                    .offset(x = rightOffset.x - 16.dp, y = rightOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Ebene nach rechts bewegen",
                    tint = Color.White
                )
            }

            // Löschen
            IconButton(
                onClick = { onRemoveAnnotation(selected.id) },
                modifier = Modifier
                    .offset(x = deleteOffset.x - 16.dp, y = deleteOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Ebene löschen",
                    tint = Color.White
                )
            }

            // Kopieren (Zwischenablage)
            IconButton(
                onClick = { onCopyAnnotation(selected) },
                modifier = Modifier
                    .offset(x = copyOffset.x - 16.dp, y = copyOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Ebene in Zwischenablage kopieren",
                    tint = Color.White
                )
            }

            // Drehen
            IconButton(
                onClick = { rotateStroke(15f) },
                modifier = Modifier
                    .offset(x = rotateOffset.x - 16.dp, y = rotateOffset.y - 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Ebene drehen",
                    tint = Color.White
                )
            }
        }
    }
}

private fun screenToNormalized(offset: Offset, pageInfo: PdfPageInfo): PathPoint {
    if (pageInfo.pageWidth <= 0f || pageInfo.pageHeight <= 0f) {
        return PathPoint(0f, 0f)
    }
    val nx = (offset.x - pageInfo.offsetX) / pageInfo.pageWidth
    val ny = (offset.y - pageInfo.offsetY) / pageInfo.pageHeight
    return PathPoint(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
}

private fun normalizedToScreen(point: PathPoint, pageInfo: PdfPageInfo): Offset {
    return Offset(
        x = point.x * pageInfo.pageWidth + pageInfo.offsetX,
        y = point.y * pageInfo.pageHeight + pageInfo.offsetY
    )
}

private fun computeStrokeBoundsScreen(
    stroke: Annotation.Stroke,
    pageInfo: PdfPageInfo
): Rect {
    val normBounds = computeStrokeBoundsNormalized(stroke)
    val topLeft = normalizedToScreen(
        PathPoint(normBounds.left, normBounds.top),
        pageInfo
    )
    val bottomRight = normalizedToScreen(
        PathPoint(normBounds.right, normBounds.bottom),
        pageInfo
    )
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = bottomRight.x,
        bottom = bottomRight.y
    )
}

private fun computeStrokeBoundsNormalized(stroke: Annotation.Stroke): Rect {
    val points = stroke.path.points
    if (points.isEmpty()) return Rect.Zero
    var minX = points.first().x
    var maxX = points.first().x
    var minY = points.first().y
    var maxY = points.first().y
    for (pt in points) {
        if (pt.x < minX) minX = pt.x
        if (pt.x > maxX) maxX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.y > maxY) maxY = pt.y
    }
    return Rect(minX, minY, maxX, maxY)
}

private fun DrawScope.drawAnnotation(annotation: Annotation, pageInfo: PdfPageInfo) {
    when (annotation) {
        is Annotation.Stroke -> {
            val path = annotation.path
            if (path.points.size < 2) return
            val alpha = if (path.isHighlighter) 0.35f else 1f
            drawPathFromPoints(
                points = path.points,
                color = path.color.copy(alpha = alpha),
                strokeWidth = path.strokeWidth,
                pageInfo = pageInfo
            )
        }

        is Annotation.HighlightRect -> {
            val topLeft = normalizedToScreen(
                PathPoint(annotation.x, annotation.y), pageInfo
            )
            val bottomRight = normalizedToScreen(
                PathPoint(
                    annotation.x + annotation.width,
                    annotation.y + annotation.height
                ), pageInfo
            )
            drawRect(
                color = annotation.color.copy(alpha = 0.35f),
                topLeft = topLeft,
                size = Size(
                    width = bottomRight.x - topLeft.x,
                    height = bottomRight.y - topLeft.y
                ),
                blendMode = BlendMode.Multiply
            )
        }

        is Annotation.TextNote -> {
            val pos = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            // Draw a note marker (small filled circle with border)
            drawCircle(
                color = annotation.color,
                radius = 12f,
                center = pos
            )
            drawCircle(
                color = Color.White,
                radius = 8f,
                center = pos
            )
            drawCircle(
                color = annotation.color,
                radius = 6f,
                center = pos
            )
        }

        is Annotation.Shape -> {
            val topLeft = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val size = Size(
                width = annotation.width * pageInfo.pageWidth,
                height = annotation.height * pageInfo.pageHeight
            )
            when (annotation.type) {
                ShapeType.CIRCLE -> {
                    val radius = min(size.width, size.height) / 2
                    val center = Offset(topLeft.x + size.width / 2, topLeft.y + size.height / 2)
                    drawCircle(
                        color = annotation.color,
                        radius = radius,
                        center = center,
                        style = Stroke(width = annotation.strokeWidth)
                    )
                }
                ShapeType.SQUARE -> {
                    val side = min(size.width, size.height)
                    drawRect(
                        color = annotation.color,
                        topLeft = topLeft,
                        size = Size(side, side),
                        style = Stroke(width = annotation.strokeWidth)
                    )
                }
                ShapeType.RECTANGLE -> {
                    drawRect(
                        color = annotation.color,
                        topLeft = topLeft,
                        size = size,
                        style = Stroke(width = annotation.strokeWidth)
                    )
                }
                ShapeType.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(topLeft.x + size.width / 2, topLeft.y)
                        lineTo(topLeft.x, topLeft.y + size.height)
                        lineTo(topLeft.x + size.width, topLeft.y + size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = annotation.color,
                        style = Stroke(width = annotation.strokeWidth)
                    )
                }
            }
        }

        is Annotation.Signature -> {
            // TODO: Implement signature drawing from bitmap
        }
    }
}

private fun DrawScope.drawPathFromPoints(
    points: List<PathPoint>,
    color: Color,
    strokeWidth: Float,
    pageInfo: PdfPageInfo
) {
    if (points.size < 2) return

    val path = Path()
    val first = normalizedToScreen(points.first(), pageInfo)
    path.moveTo(first.x, first.y)

    for (i in 1 until points.size) {
        val pt = normalizedToScreen(points[i], pageInfo)
        path.lineTo(pt.x, pt.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
