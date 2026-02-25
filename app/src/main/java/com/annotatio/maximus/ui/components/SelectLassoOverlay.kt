package com.annotatio.maximus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.model.PathPoint
import kotlin.math.abs

@Composable
fun SelectLassoOverlay(
    annotations: List<Annotation>,
    pageInfo: PdfPageInfo,
    activeTool: AnnotationType,
    onUpdateAnnotation: (Annotation) -> Unit,
    onRemoveAnnotations: (List<String>) -> Unit,
    onDuplicateAnnotations: (List<Annotation>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var moveStart by remember { mutableStateOf<Offset?>(null) }

    // When tool changes, clear selection
    val toolKey = activeTool
    remember(toolKey) { selectedIds = emptySet() }

    val density = LocalDensity.current

    val selectedAnnotations = annotations.filter { it.id in selectedIds }

    // Compute bounding box of all selected annotations in screen coords
    val selectionBounds: Rect? = if (selectedIds.isNotEmpty()) {
        computeSelectionBoundsScreen(selectedAnnotations, pageInfo)
    } else null

    val gestureModifier = when (activeTool) {
        AnnotationType.SELECT -> Modifier
            .pointerInput(annotations, pageInfo) {
                detectTapGestures { offset ->
                    val normalized = screenToNorm(offset, pageInfo)
                    val hit = annotations.findLast { annotation ->
                        annotationContains(annotation, normalized, pageInfo)
                    }
                    selectedIds = if (hit != null) setOf(hit.id) else emptySet()
                }
            }
            .pointerInput(selectedIds, annotations, pageInfo) {
                // Drag selected annotations
                detectDragGestures(
                    onDragStart = { offset ->
                        val normalized = screenToNorm(offset, pageInfo)
                        val hit = annotations.findLast { annotationContains(it, normalized, pageInfo) }
                        if (hit != null && hit.id in selectedIds) {
                            moveStart = offset
                        } else {
                            moveStart = null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (moveStart != null && selectedIds.isNotEmpty()) {
                            val dxNorm = dragAmount.x / pageInfo.pageWidth
                            val dyNorm = dragAmount.y / pageInfo.pageHeight
                            selectedAnnotations.forEach { annotation ->
                                onUpdateAnnotation(moveAnnotation(annotation, dxNorm, dyNorm))
                            }
                        }
                    },
                    onDragEnd = { moveStart = null }
                )
            }

        AnnotationType.LASSO -> Modifier
            .pointerInput(annotations, pageInfo) {
                detectDragGestures(
                    onDragStart = { offset ->
                        lassoPoints = listOf(offset)
                        dragStart = offset
                        selectedIds = emptySet()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        lassoPoints = lassoPoints + change.position
                    },
                    onDragEnd = {
                        // Hit-test all annotations against the lasso polygon
                        val hitIds = annotations.filter { annotation ->
                            val center = annotationCenterScreen(annotation, pageInfo)
                            center != null && pointInPolygon(center, lassoPoints)
                        }.map { it.id }.toSet()
                        selectedIds = hitIds
                        lassoPoints = emptyList()
                    },
                    onDragCancel = {
                        lassoPoints = emptyList()
                    }
                )
            }

        else -> Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // Draw all annotations
            for (annotation in annotations) {
                drawAnnotation(annotation, pageInfo, emptyMap())
            }

            // Draw selection highlight
            for (id in selectedIds) {
                val annotation = annotations.find { it.id == id } ?: continue
                val bounds = annotationBoundsScreen(annotation, pageInfo) ?: continue
                drawRect(
                    color = Color(0x441565C0),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height)
                )
                drawRect(
                    color = Color(0xFF1565C0),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    )
                )
            }

            // Draw lasso path
            if (lassoPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(lassoPoints.first().x, lassoPoints.first().y)
                    for (pt in lassoPoints.drop(1)) lineTo(pt.x, pt.y)
                    close()
                }
                drawPath(
                    path = path,
                    color = Color(0x441565C0),
                )
                drawPath(
                    path = path,
                    color = Color(0xFF1565C0),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                    )
                )
            }
        }

        // Context menu above selection
        if (selectedIds.isNotEmpty() && selectionBounds != null) {
            val menuX = with(density) { selectionBounds.center.x.toDp() - 80.dp }
            val menuY = with(density) { (selectionBounds.top - 48.dp.toPx()).coerceAtLeast(0f).toDp() }

            Surface(
                modifier = Modifier
                    .offset(x = menuX, y = menuY),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onRemoveAnnotations(selectedIds.toList())
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = {
                        onDuplicateAnnotations(selectedAnnotations)
                        selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplizieren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedIds.size == 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        // Move hint icon
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Verschieben (ziehen)",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── helpers ──────────────────────────────────────────────────────────────────

private fun screenToNorm(offset: Offset, pageInfo: PdfPageInfo): PathPoint {
    if (pageInfo.pageWidth <= 0f || pageInfo.pageHeight <= 0f) return PathPoint(0f, 0f)
    return PathPoint(
        ((offset.x - pageInfo.offsetX) / pageInfo.pageWidth).coerceIn(0f, 1f),
        ((offset.y - pageInfo.offsetY) / pageInfo.pageHeight).coerceIn(0f, 1f)
    )
}

private fun normToScreen(pt: PathPoint, pageInfo: PdfPageInfo) =
    Offset(pt.x * pageInfo.pageWidth + pageInfo.offsetX, pt.y * pageInfo.pageHeight + pageInfo.offsetY)

private fun annotationContains(annotation: Annotation, norm: PathPoint, pageInfo: PdfPageInfo): Boolean {
    val threshold = 0.04f
    return when (annotation) {
        is Annotation.Stroke -> annotation.path.points.any {
            abs(it.x - norm.x) < threshold && abs(it.y - norm.y) < threshold
        }
        is Annotation.Shape -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.HighlightRect -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.TextNote -> abs(annotation.x - norm.x) < threshold && abs(annotation.y - norm.y) < threshold
        is Annotation.Underline -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                abs(annotation.y - norm.y) < threshold * 2
        is Annotation.Strikethrough -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                abs(annotation.y - norm.y) < threshold * 2
        is Annotation.Table -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.Image -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.Link -> abs(annotation.x - norm.x) < threshold * 3 &&
                abs(annotation.y - norm.y) < threshold * 2
        is Annotation.Comment -> abs(annotation.x - norm.x) < threshold && abs(annotation.y - norm.y) < threshold
        is Annotation.Signature -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.SmartGraphic -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
        is Annotation.TextBox -> norm.x in annotation.x..(annotation.x + annotation.width) &&
                norm.y in annotation.y..(annotation.y + annotation.height)
    }
}

private fun annotationCenterScreen(annotation: Annotation, pageInfo: PdfPageInfo): Offset? {
    return when (annotation) {
        is Annotation.Stroke -> {
            if (annotation.path.points.isEmpty()) null
            else {
                val cx = annotation.path.points.map { it.x }.average().toFloat()
                val cy = annotation.path.points.map { it.y }.average().toFloat()
                normToScreen(PathPoint(cx, cy), pageInfo)
            }
        }
        is Annotation.Shape -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.HighlightRect -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.TextNote -> normToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
        is Annotation.Underline -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y), pageInfo)
        is Annotation.Strikethrough -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y), pageInfo)
        is Annotation.Table -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.Image -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.Link -> normToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
        is Annotation.Comment -> normToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
        is Annotation.Signature -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.SmartGraphic -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
        is Annotation.TextBox -> normToScreen(PathPoint(annotation.x + annotation.width / 2, annotation.y + annotation.height / 2), pageInfo)
    }
}

private fun annotationBoundsScreen(annotation: Annotation, pageInfo: PdfPageInfo): Rect? {
    fun n2s(x: Float, y: Float) = normToScreen(PathPoint(x, y), pageInfo)
    val pad = 6f
    return when (annotation) {
        is Annotation.Stroke -> {
            if (annotation.path.points.isEmpty()) return null
            var minX = annotation.path.points.first().x; var maxX = minX
            var minY = annotation.path.points.first().y; var maxY = minY
            for (p in annotation.path.points) {
                if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
                if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
            }
            val tl = n2s(minX, minY); val br = n2s(maxX, maxY)
            Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad)
        }
        is Annotation.Shape -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.HighlightRect -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.TextNote -> { val c = n2s(annotation.x, annotation.y); Rect(c.x - 14f - pad, c.y - 14f - pad, c.x + 14f + pad, c.y + 14f + pad) }
        is Annotation.Underline -> { val s = n2s(annotation.x, annotation.y); val e = n2s(annotation.x + annotation.width, annotation.y); Rect(s.x - pad, s.y - pad, e.x + pad, e.y + pad) }
        is Annotation.Strikethrough -> { val s = n2s(annotation.x, annotation.y); val e = n2s(annotation.x + annotation.width, annotation.y); Rect(s.x - pad, s.y - pad, e.x + pad, e.y + pad) }
        is Annotation.Table -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.Image -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.Link -> { val c = n2s(annotation.x, annotation.y); Rect(c.x - pad, c.y - 16f - pad, c.x + 80f + pad, c.y + pad) }
        is Annotation.Comment -> { val c = n2s(annotation.x, annotation.y); Rect(c.x - 14f - pad, c.y - 14f - pad, c.x + 14f + pad, c.y + 14f + pad) }
        is Annotation.Signature -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.SmartGraphic -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
        is Annotation.TextBox -> { val tl = n2s(annotation.x, annotation.y); val br = n2s(annotation.x + annotation.width, annotation.y + annotation.height); Rect(tl.x - pad, tl.y - pad, br.x + pad, br.y + pad) }
    }
}

private fun computeSelectionBoundsScreen(annotations: List<Annotation>, pageInfo: PdfPageInfo): Rect? {
    val rects = annotations.mapNotNull { annotationBoundsScreen(it, pageInfo) }
    if (rects.isEmpty()) return null
    return Rect(
        left = rects.minOf { it.left },
        top = rects.minOf { it.top },
        right = rects.maxOf { it.right },
        bottom = rects.maxOf { it.bottom }
    )
}

private fun moveAnnotation(annotation: Annotation, dxNorm: Float, dyNorm: Float): Annotation = when (annotation) {
    is Annotation.Stroke -> annotation.copy(path = annotation.path.copy(points = annotation.path.points.map { PathPoint(it.x + dxNorm, it.y + dyNorm) }))
    is Annotation.Shape -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.HighlightRect -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.TextNote -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Underline -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Strikethrough -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Table -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Image -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Link -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Comment -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.Signature -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.SmartGraphic -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
    is Annotation.TextBox -> annotation.copy(x = annotation.x + dxNorm, y = annotation.y + dyNorm)
}

/** Ray-casting algorithm: is point inside polygon? */
private fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x; val yi = polygon[i].y
        val xj = polygon[j].x; val yj = polygon[j].y
        if ((yi > point.y) != (yj > point.y) &&
            point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}
