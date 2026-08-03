package com.annotatio.maximus.ui.components

import android.graphics.BitmapFactory
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.model.DrawingPath
import com.annotatio.maximus.model.PageTextContent
import com.annotatio.maximus.model.PathPoint
import com.annotatio.maximus.model.ShapeType
import com.annotatio.maximus.model.SmartGraphicType
import com.annotatio.maximus.model.TextBoxFont
import com.annotatio.maximus.util.RecognizedShape
import com.annotatio.maximus.util.ShapeRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
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
    onRequestComment: (Float, Float) -> Unit,
    onRequestTable: (Float, Float) -> Unit,
    onRequestImage: (Float, Float) -> Unit = { _, _ -> },
    onRequestLink: (Float, Float) -> Unit = { _, _ -> },
    onRequestTextBox: (Float, Float) -> Unit = { _, _ -> },
    onEditTextBox: (Annotation.TextBox) -> Unit = { _ -> },
    pendingSmartGraphicType: SmartGraphicType = SmartGraphicType.MIND_MAP,
    externalImageCache: Map<String, ImageBitmap?>? = null,
    pageTextContent: PageTextContent? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Stroke bounds cache to optimize hit testing during eraser drag
    val strokeBoundsCache = remember(annotations) {
        annotations.filterIsInstance<Annotation.Stroke>().associate { stroke ->
            val points = stroke.path.points
            val bounds = if (points.isEmpty()) {
                Rect(0f, 0f, 0f, 0f)
            } else {
                var minX = points.first().x
                var maxX = minX
                var minY = points.first().y
                var maxY = minY
                for (pt in points) {
                    if (pt.x < minX) minX = pt.x
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.y > maxY) maxY = pt.y
                }
                Rect(minX, minY, maxX, maxY)
            }
            stroke.id to bounds
        }
    }

    // Cache for decoded signature bitmaps: annotationId -> ImageBitmap
    val signatureBitmapCache = remember(annotations) {
        annotations.filterIsInstance<Annotation.Signature>().associate { sig ->
            sig.id to try {
                BitmapFactory.decodeByteArray(sig.bitmapData, 0, sig.bitmapData.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    
    var currentPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    
    // Shape recognition state
    var pendingStroke by remember { mutableStateOf<Annotation.Stroke?>(null) }
    var shapeRecognitionJob by remember { mutableStateOf<Job?>(null) }
    var shapeRecognitionCountdown by remember { mutableStateOf(0) }
    var recognizedShape by remember { mutableStateOf<RecognizedShape?>(null) }

    // Cache for loaded image bitmaps: uriString -> ImageBitmap
    val localImageBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val imageBitmapCache: Map<String, ImageBitmap?> = externalImageCache ?: localImageBitmapCache
    if (externalImageCache == null) {
        LaunchedEffect(annotations) {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                for (img in annotations.filterIsInstance<Annotation.Image>()) {
                    if (!localImageBitmapCache.containsKey(img.uriString)) {
                        val bitmap = try {
                            context.contentResolver.openInputStream(Uri.parse(img.uriString))?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        } catch (_: Exception) {
                            null
                        }
                        localImageBitmapCache[img.uriString] = bitmap
                    }
                }
            }
        }
    }

    val gestureModifier = when (activeTool) {
        AnnotationType.PEN, AnnotationType.HIGHLIGHTER -> {
            Modifier.pointerInput(activeTool, penColor, penStrokeWidth, pendingStroke) {
                var lastMoveTime = 0L
                var lastMovePos = Offset.Zero
                
                detectDragGestures(
                    onDragStart = { offset ->
                        shapeRecognitionJob?.cancel()
                        pendingStroke = null
                        shapeRecognitionCountdown = 0
                        recognizedShape = null
                        
                        currentPoints = listOf(screenToNormalized(offset, pageInfo))
                        dragStart = null
                        dragCurrent = null
                        
                        lastMoveTime = System.currentTimeMillis()
                        lastMovePos = offset
                        
                        if (activeTool == AnnotationType.PEN) {
                            shapeRecognitionJob = coroutineScope.launch {
                                var isHolding = false
                                while (true) {
                                    delay(100)
                                    val now = System.currentTimeMillis()
                                    if (now - lastMoveTime >= 1000 && !isHolding) {
                                        isHolding = true
                                        shapeRecognitionCountdown = 3
                                        pendingStroke = Annotation.Stroke(
                                            pageIndex = currentPageIndex,
                                            path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                        )
                                        
                                        while (shapeRecognitionCountdown > 0) {
                                            val screenPoints = currentPoints.map { normalizedToScreen(it, pageInfo) }
                                            recognizedShape = ShapeRecognizer.recognize(screenPoints)
                                            
                                            delay(1000)
                                            shapeRecognitionCountdown--
                                            
                                            pendingStroke = Annotation.Stroke(
                                                pageIndex = currentPageIndex,
                                                path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                            )
                                        }
                                        
                                        // Auto-shape recognition complete!
                                        val screenPoints = currentPoints.map { normalizedToScreen(it, pageInfo) }
                                        val shape = ShapeRecognizer.recognize(screenPoints)
                                        val shapeAnnotation = ShapeRecognizer.toAnnotation(
                                            shape = shape,
                                            pageIndex = currentPageIndex,
                                            color = penColor,
                                            strokeWidth = penStrokeWidth
                                        )
                                        
                                        if (shapeAnnotation != null) {
                                            onAddAnnotation(shapeAnnotation)
                                        } else {
                                            onAddAnnotation(
                                                Annotation.Stroke(
                                                    pageIndex = currentPageIndex,
                                                    path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                                )
                                            )
                                        }
                                        
                                        currentPoints = emptyList()
                                        pendingStroke = null
                                        shapeRecognitionCountdown = 0
                                        recognizedShape = null
                                        break
                                    }
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        val normalized = screenToNormalized(pos, pageInfo)
                        currentPoints = currentPoints + normalized
                        
                        val dist = (pos - lastMovePos).getDistance()
                        if (dist > 15f) {
                            lastMoveTime = System.currentTimeMillis()
                            lastMovePos = pos
                            
                            if (pendingStroke != null) {
                                pendingStroke = null
                                shapeRecognitionCountdown = 0
                                recognizedShape = null
                                
                                shapeRecognitionJob?.cancel()
                                if (activeTool == AnnotationType.PEN) {
                                    shapeRecognitionJob = coroutineScope.launch {
                                        var isHolding = false
                                        while (true) {
                                            delay(100)
                                            val now = System.currentTimeMillis()
                                            if (now - lastMoveTime >= 1000 && !isHolding) {
                                                isHolding = true
                                                shapeRecognitionCountdown = 3
                                                pendingStroke = Annotation.Stroke(
                                                    pageIndex = currentPageIndex,
                                                    path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                                )
                                                while (shapeRecognitionCountdown > 0) {
                                                    val screenPoints = currentPoints.map { normalizedToScreen(it, pageInfo) }
                                                    recognizedShape = ShapeRecognizer.recognize(screenPoints)
                                                    delay(1000)
                                                    shapeRecognitionCountdown--
                                                    pendingStroke = Annotation.Stroke(
                                                        pageIndex = currentPageIndex,
                                                        path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                                    )
                                                }
                                                val screenPoints = currentPoints.map { normalizedToScreen(it, pageInfo) }
                                                val shape = ShapeRecognizer.recognize(screenPoints)
                                                val shapeAnnotation = ShapeRecognizer.toAnnotation(
                                                    shape = shape,
                                                    pageIndex = currentPageIndex,
                                                    color = penColor,
                                                    strokeWidth = penStrokeWidth
                                                )
                                                if (shapeAnnotation != null) {
                                                    onAddAnnotation(shapeAnnotation)
                                                } else {
                                                    onAddAnnotation(
                                                        Annotation.Stroke(
                                                            pageIndex = currentPageIndex,
                                                            path = DrawingPath(currentPoints, penColor, penStrokeWidth, false)
                                                        )
                                                    )
                                                }
                                                currentPoints = emptyList()
                                                pendingStroke = null
                                                shapeRecognitionCountdown = 0
                                                recognizedShape = null
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        shapeRecognitionJob?.cancel()
                        if (currentPoints.size >= 2) {
                            val isHighlighter = activeTool == AnnotationType.HIGHLIGHTER
                            if (isHighlighter) {
                                val annotation = Annotation.Stroke(
                                    pageIndex = currentPageIndex,
                                    path = DrawingPath(
                                        points = currentPoints,
                                        color = penColor,
                                        strokeWidth = penStrokeWidth * 3f,
                                        isHighlighter = true
                                    )
                                )
                                onAddAnnotation(annotation)
                            } else {
                                val stroke = Annotation.Stroke(
                                    pageIndex = currentPageIndex,
                                    path = DrawingPath(
                                        points = currentPoints,
                                        color = penColor,
                                        strokeWidth = penStrokeWidth,
                                        isHighlighter = false
                                    )
                                )
                                onAddAnnotation(stroke)
                            }
                        }
                        currentPoints = emptyList()
                        pendingStroke = null
                        shapeRecognitionCountdown = 0
                        recognizedShape = null
                    },
                    onDragCancel = {
                        shapeRecognitionJob?.cancel()
                        currentPoints = emptyList()
                        pendingStroke = null
                        shapeRecognitionCountdown = 0
                        recognizedShape = null
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

        AnnotationType.UNDERLINE -> {
            Modifier.pointerInput(activeTool, penColor, penStrokeWidth, pageTextContent) {
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
                        val s = dragStart
                        val e = dragCurrent
                        if (s != null && e != null) {
                            val startNorm = screenToNormalized(s, pageInfo)
                            val endNorm = screenToNormalized(e, pageInfo)
                            val selectionStartX = minOf(startNorm.x, endNorm.x)
                            val selectionEndX = maxOf(startNorm.x, endNorm.x)
                            val selectionY = (startNorm.y + endNorm.y) / 2f
                            
                            if (selectionEndX - selectionStartX > 0.01f) {
                                // Try to snap to text line
                                val textLine = pageTextContent?.findTextLineForAnnotation(
                                    startX = selectionStartX,
                                    endX = selectionEndX,
                                    y = selectionY
                                )
                                
                                if (textLine != null) {
                                    // Snap to text line: underline goes below the text
                                    val snappedX = maxOf(textLine.x, selectionStartX)
                                    val snappedEndX = minOf(textLine.right, selectionEndX)
                                    onAddAnnotation(
                                        Annotation.Underline(
                                            pageIndex = currentPageIndex,
                                            x = snappedX,
                                            y = textLine.underlineY,
                                            width = snappedEndX - snappedX,
                                            color = penColor
                                        )
                                    )
                                } else {
                                    // Fallback to raw position if no text found
                                    onAddAnnotation(
                                        Annotation.Underline(
                                            pageIndex = currentPageIndex,
                                            x = selectionStartX,
                                            y = selectionY,
                                            width = selectionEndX - selectionStartX,
                                            color = penColor
                                        )
                                    )
                                }
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

        AnnotationType.STRIKETHROUGH -> {
            Modifier.pointerInput(activeTool, penColor, penStrokeWidth, pageTextContent) {
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
                        val s = dragStart
                        val e = dragCurrent
                        if (s != null && e != null) {
                            val startNorm = screenToNormalized(s, pageInfo)
                            val endNorm = screenToNormalized(e, pageInfo)
                            val selectionStartX = minOf(startNorm.x, endNorm.x)
                            val selectionEndX = maxOf(startNorm.x, endNorm.x)
                            val selectionY = (startNorm.y + endNorm.y) / 2f
                            
                            if (selectionEndX - selectionStartX > 0.01f) {
                                // Try to snap to text line
                                val textLine = pageTextContent?.findTextLineForAnnotation(
                                    startX = selectionStartX,
                                    endX = selectionEndX,
                                    y = selectionY
                                )
                                
                                if (textLine != null) {
                                    // Snap to text line: strikethrough goes through the middle
                                    val snappedX = maxOf(textLine.x, selectionStartX)
                                    val snappedEndX = minOf(textLine.right, selectionEndX)
                                    onAddAnnotation(
                                        Annotation.Strikethrough(
                                            pageIndex = currentPageIndex,
                                            x = snappedX,
                                            y = textLine.centerY,
                                            width = snappedEndX - snappedX,
                                            color = penColor
                                        )
                                    )
                                } else {
                                    // Fallback to raw position if no text found
                                    onAddAnnotation(
                                        Annotation.Strikethrough(
                                            pageIndex = currentPageIndex,
                                            x = selectionStartX,
                                            y = selectionY,
                                            width = selectionEndX - selectionStartX,
                                            color = penColor
                                        )
                                    )
                                }
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

        AnnotationType.COMMENT -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestComment(normalized.x, normalized.y)
                }
            }
        }

        AnnotationType.TABLE -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestTable(normalized.x, normalized.y)
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

        AnnotationType.IMAGE -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestImage(normalized.x, normalized.y)
                }
            }
        }

        AnnotationType.LINK -> {
            Modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    onRequestLink(normalized.x, normalized.y)
                }
            }
        }

        AnnotationType.TEXT_BOX -> {
            Modifier.pointerInput(annotations) {
                detectTapGestures { offset ->
                    val normalized = screenToNormalized(offset, pageInfo)
                    
                    // Check if there's an existing TextBox at this position
                    val hitTextBox = annotations.filterIsInstance<Annotation.TextBox>().findLast { textBox ->
                        normalized.x in textBox.x..(textBox.x + textBox.width) &&
                        normalized.y in textBox.y..(textBox.y + textBox.height)
                    }
                    
                    if (hitTextBox != null) {
                        // Edit existing text box
                        onEditTextBox(hitTextBox)
                    } else {
                        // Create new text box (optional - could be disabled)
                        onRequestTextBox(normalized.x, normalized.y)
                    }
                }
            }
        }

        AnnotationType.SELECT, AnnotationType.LASSO, AnnotationType.HAND -> {
            // Handled by SelectLassoOverlay or the PDF viewer itself
            Modifier
        }

        AnnotationType.SMART_GRAPHIC -> {
            Modifier.pointerInput(activeTool, penColor) {
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
                            val startNorm = screenToNormalized(dragStart!!, pageInfo)
                            val endNorm = screenToNormalized(dragCurrent!!, pageInfo)
                            val x = minOf(startNorm.x, endNorm.x)
                            val y = minOf(startNorm.y, endNorm.y)
                            val w = abs(endNorm.x - startNorm.x)
                            val h = abs(endNorm.y - startNorm.y)
                            if (w > 0.05f && h > 0.05f) {
                                // pendingSmartGraphicType is set by PdfViewerScreen before activating tool
                                onAddAnnotation(
                                    Annotation.SmartGraphic(
                                        pageIndex = currentPageIndex,
                                        type = pendingSmartGraphicType,
                                        x = x, y = y, width = w, height = h,
                                        color = penColor
                                    )
                                )
                            }
                        }
                        dragStart = null
                        dragCurrent = null
                    },
                    onDragCancel = { dragStart = null; dragCurrent = null }
                )
            }
        }

        AnnotationType.ERASER -> {
            Modifier.pointerInput(annotations, strokeBoundsCache) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragStart = offset
                        val normalized = screenToNormalized(offset, pageInfo)
                        val pad = 0.02f
                        val hit = annotations.findLast { annotation ->
                            annotationHitTest(annotation, normalized, pad, strokeBoundsCache)
                        }
                        hit?.let { onRemoveAnnotation(it.id) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragCurrent = change.position
                        
                        val currentAnnotations = annotations.toList()
                        coroutineScope.launch(Dispatchers.Default) {
                            val normalized = screenToNormalized(change.position, pageInfo)
                            val pad = 0.02f
                            val hit = currentAnnotations.findLast { annotation ->
                                annotationHitTest(annotation, normalized, pad, strokeBoundsCache)
                            }
                            if (hit != null) {
                                launch(Dispatchers.Main) {
                                    onRemoveAnnotation(hit.id)
                                }
                            }
                        }
                    },
                    onDragEnd = {
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
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(gestureModifier)
    ) {
        // Draw existing annotations
        for (annotation in annotations) {
            drawAnnotation(annotation, pageInfo, imageBitmapCache, signatureBitmapCache)
        }

        // Draw in-progress stroke
        if (currentPoints.size >= 2) {
            val isHighlighter = activeTool == AnnotationType.HIGHLIGHTER
            drawPathFromPoints(
                points = currentPoints,
                color = if (isHighlighter) penColor.copy(alpha = 0.6f) else penColor,
                strokeWidth = if (isHighlighter) penStrokeWidth * 3f else penStrokeWidth,
                pageInfo = pageInfo,
                blendMode = if (isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
            )
        }
        
        // Draw pending stroke (during shape recognition countdown)
        if (pendingStroke != null && shapeRecognitionCountdown > 0) {
            val stroke = pendingStroke!!
            drawPathFromPoints(
                points = stroke.path.points,
                color = stroke.path.color.copy(alpha = 0.5f),
                strokeWidth = stroke.path.strokeWidth,
                pageInfo = pageInfo
            )
            
            // Draw countdown indicator
            val centerX = size.width / 2
            val centerY = size.height * 0.15f
            
            // Background circle
            drawCircle(
                color = Color.Black.copy(alpha = 0.6f),
                radius = 30f,
                center = Offset(centerX, centerY)
            )
            
            // Countdown text - using native paint directly
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 64f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                shapeRecognitionCountdown.toString(),
                centerX,
                centerY + 20f,
                textPaint
            )
            
            // Draw recognized shape preview (if any)
            recognizedShape?.let { shape ->
                when (shape) {
                    is RecognizedShape.Circle -> {
                        val center = normalizedToScreen(
                            PathPoint(shape.center.x, shape.center.y), pageInfo
                        )
                        val radius = shape.radius * pageInfo.pageWidth
                        drawCircle(
                            color = penColor.copy(alpha = 0.3f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 4f)
                        )
                    }
                    is RecognizedShape.Rectangle -> {
                        val topLeft = normalizedToScreen(
                            PathPoint(shape.bounds.left, shape.bounds.top), pageInfo
                        )
                        drawRect(
                            color = penColor.copy(alpha = 0.3f),
                            topLeft = topLeft,
                            size = Size(
                                shape.bounds.width * pageInfo.pageWidth,
                                shape.bounds.height * pageInfo.pageHeight
                            ),
                            style = Stroke(width = 4f)
                        )
                    }
                    is RecognizedShape.Triangle -> {
                        if (shape.points.size == 3) {
                            val path = Path()
                            val p0 = normalizedToScreen(
                                PathPoint(shape.points[0].x, shape.points[0].y), pageInfo
                            )
                            val p1 = normalizedToScreen(
                                PathPoint(shape.points[1].x, shape.points[1].y), pageInfo
                            )
                            val p2 = normalizedToScreen(
                                PathPoint(shape.points[2].x, shape.points[2].y), pageInfo
                            )
                            path.moveTo(p0.x, p0.y)
                            path.lineTo(p1.x, p1.y)
                            path.lineTo(p2.x, p2.y)
                            path.close()
                            drawPath(
                                path = path,
                                color = penColor.copy(alpha = 0.3f),
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                    else -> { /* Other shapes not yet visualized */ }
                }
            }
        }

        // Draw in-progress underline / strikethrough preview with text snap
        if (activeTool == AnnotationType.UNDERLINE || activeTool == AnnotationType.STRIKETHROUGH) {
            val s = dragStart
            val e = dragCurrent
            if (s != null && e != null) {
                val startNorm = screenToNormalized(s, pageInfo)
                val endNorm = screenToNormalized(e, pageInfo)
                val selectionStartX = minOf(startNorm.x, endNorm.x)
                val selectionEndX = maxOf(startNorm.x, endNorm.x)
                val selectionY = (startNorm.y + endNorm.y) / 2f
                
                // Try to find text line for preview snap
                val textLine = pageTextContent?.findTextLineForAnnotation(
                    startX = selectionStartX,
                    endX = selectionEndX,
                    y = selectionY
                )
                
                val (previewY, isSnapped) = if (textLine != null) {
                    // Snap to text line
                    val snappedY = if (activeTool == AnnotationType.UNDERLINE) {
                        textLine.underlineY
                    } else {
                        textLine.centerY
                    }
                    Pair(snappedY, true)
                } else {
                    // Use raw position
                    Pair(selectionY, false)
                }
                
                // Convert back to screen coordinates for drawing
                val previewScreenY = normalizedToScreen(
                    PathPoint(0f, previewY), 
                    pageInfo
                ).y
                val startScreenX = minOf(s.x, e.x)
                val endScreenX = maxOf(s.x, e.x)
                
                // Draw the preview line
                drawLine(
                    color = if (isSnapped) penColor else penColor.copy(alpha = 0.6f),
                    start = Offset(startScreenX, previewScreenY),
                    end = Offset(endScreenX, previewScreenY),
                    strokeWidth = penStrokeWidth * 1.5f,
                    cap = StrokeCap.Round
                )
                
                // If snapped, draw a subtle indicator
                if (isSnapped && textLine != null) {
                    // Highlight the detected text line area faintly
                    val topScreen = normalizedToScreen(
                        PathPoint(0f, textLine.top), pageInfo
                    ).y
                    val bottomScreen = normalizedToScreen(
                        PathPoint(0f, textLine.bottom), pageInfo
                    ).y
                    val leftScreen = normalizedToScreen(
                        PathPoint(textLine.x, textLine.centerY), pageInfo
                    ).x
                    val rightScreen = normalizedToScreen(
                        PathPoint(textLine.right, textLine.centerY), pageInfo
                    ).x
                    
                    drawRect(
                        color = penColor.copy(alpha = 0.1f),
                        topLeft = Offset(leftScreen, topScreen),
                        size = Size(rightScreen - leftScreen, bottomScreen - topScreen)
                    )
                }
            }
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
                AnnotationType.SMART_GRAPHIC -> {
                    // Preview as dashed rectangle
                    drawRect(
                        color = previewColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(
                            width = penStrokeWidth,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(12f, 6f)
                            )
                        )
                    )
                }
                else -> {}
            }
        }
        
        // Draw eraser cursor
        if (activeTool == AnnotationType.ERASER && dragCurrent != null) {
            val eraserRadius = 20f
            // Outer circle (white border)
            drawCircle(
                color = Color.White,
                radius = eraserRadius + 4f,
                center = dragCurrent!!,
                style = Stroke(width = 2f)
            )
            // Inner circle (red with transparency)
            drawCircle(
                color = Color.Red.copy(alpha = 0.4f),
                radius = eraserRadius,
                center = dragCurrent!!
            )
            // Center dot
            drawCircle(
                color = Color.Red.copy(alpha = 0.8f),
                radius = 4f,
                center = dragCurrent!!
            )
        }
    }
}

@Composable
fun AnnotationDisplayOverlay(
    annotations: List<Annotation>,
    pageInfo: PdfPageInfo,
    onCommentTapped: ((Annotation.Comment) -> Unit)? = null,
    onTextBoxDoubleTapped: ((Annotation.TextBox) -> Unit)? = null,
    externalImageCache: Map<String, ImageBitmap?>? = null,
    modifier: Modifier = Modifier
) {
    if (annotations.isEmpty()) return

    val context = LocalContext.current
    val localImageBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val imageBitmapCache: Map<String, ImageBitmap?> = externalImageCache ?: localImageBitmapCache
    
    val signatureBitmapCache = remember(annotations) {
        annotations.filterIsInstance<Annotation.Signature>().associate { sig ->
            sig.id to try {
                BitmapFactory.decodeByteArray(sig.bitmapData, 0, sig.bitmapData.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    if (externalImageCache == null) {
        for (img in annotations.filterIsInstance<Annotation.Image>()) {
            if (!localImageBitmapCache.containsKey(img.uriString)) {
                localImageBitmapCache[img.uriString] = try {
                    context.contentResolver.openInputStream(Uri.parse(img.uriString))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    val hasCallbacks = onCommentTapped != null || onTextBoxDoubleTapped != null
    val tapModifier = if (hasCallbacks) {
        Modifier.pointerInput(annotations, pageInfo) {
            detectTapGestures(
                onTap = { offset ->
                    if (onCommentTapped != null) {
                        val normalized = screenToNormalized(offset, pageInfo)
                        val threshold = 0.04f
                        val hit = annotations.filterIsInstance<Annotation.Comment>().findLast { c ->
                            abs(c.x - normalized.x) < threshold && abs(c.y - normalized.y) < threshold
                        }
                        hit?.let { onCommentTapped(it) }
                    }
                },
                onDoubleTap = { offset ->
                    if (onTextBoxDoubleTapped != null) {
                        val normalized = screenToNormalized(offset, pageInfo)
                        val hit = annotations.filterIsInstance<Annotation.TextBox>().findLast { tb ->
                            normalized.x in tb.x..(tb.x + tb.width) &&
                                    normalized.y in tb.y..(tb.y + tb.height)
                        }
                        hit?.let { onTextBoxDoubleTapped(it) }
                    }
                }
            )
        }
    } else Modifier

    Canvas(modifier = modifier.fillMaxSize().then(tapModifier)) {
        for (annotation in annotations) {
            drawAnnotation(annotation, pageInfo, imageBitmapCache, signatureBitmapCache)
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
    externalImageCache: Map<String, ImageBitmap?>? = null,
    modifier: Modifier = Modifier
) {
    if (annotations.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val localImageBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val imageBitmapCache: Map<String, ImageBitmap?> = externalImageCache ?: localImageBitmapCache
    
    val signatureBitmapCache = remember(annotations) {
        annotations.filterIsInstance<Annotation.Signature>().associate { sig ->
            sig.id to try {
                BitmapFactory.decodeByteArray(sig.bitmapData, 0, sig.bitmapData.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    if (externalImageCache == null) {
        for (img in annotations.filterIsInstance<Annotation.Image>()) {
            if (!localImageBitmapCache.containsKey(img.uriString)) {
                localImageBitmapCache[img.uriString] = try {
                    context.contentResolver.openInputStream(Uri.parse(img.uriString))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
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
                drawAnnotation(annotation, pageInfo, imageBitmapCache, signatureBitmapCache)
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

/** Bounding-box hit test for eraser and selection tools. pad is in normalized coordinates. */
internal fun annotationHitTest(
    annotation: Annotation,
    norm: PathPoint,
    pad: Float,
    strokeBoundsCache: Map<String, Rect> = emptyMap()
): Boolean {
    return when (annotation) {
        is Annotation.Stroke -> {
            val cachedBounds = strokeBoundsCache[annotation.id]
            if (cachedBounds != null) {
                norm.x in (cachedBounds.left - pad)..(cachedBounds.right + pad) &&
                norm.y in (cachedBounds.top - pad)..(cachedBounds.bottom + pad)
            } else {
                if (annotation.path.points.isEmpty()) false
                else {
                    var minX = annotation.path.points.first().x
                    var maxX = minX
                    var minY = annotation.path.points.first().y
                    var maxY = minY
                    for (pt in annotation.path.points) {
                        if (pt.x < minX) minX = pt.x
                        if (pt.x > maxX) maxX = pt.x
                        if (pt.y < minY) minY = pt.y
                        if (pt.y > maxY) maxY = pt.y
                    }
                    norm.x in (minX - pad)..(maxX + pad) && norm.y in (minY - pad)..(maxY + pad)
                }
            }
        }
        is Annotation.HighlightRect ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.TextNote ->
            norm.x in (annotation.x - pad * 3)..(annotation.x + pad * 3) &&
            norm.y in (annotation.y - pad * 3)..(annotation.y + pad * 3)
        is Annotation.Shape ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.Underline ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad * 3)..(annotation.y + pad * 3)
        is Annotation.Strikethrough ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad * 3)..(annotation.y + pad * 3)
        is Annotation.Table ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.Image ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.Link ->
            norm.x in (annotation.x - pad)..(annotation.x + 0.15f + pad) &&
            norm.y in (annotation.y - pad * 4)..(annotation.y + pad)
        is Annotation.Comment ->
            norm.x in (annotation.x - pad * 3)..(annotation.x + pad * 3) &&
            norm.y in (annotation.y - pad * 3)..(annotation.y + pad * 3)
        is Annotation.Signature ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.SmartGraphic ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
        is Annotation.TextBox ->
            norm.x in (annotation.x - pad)..(annotation.x + annotation.width + pad) &&
            norm.y in (annotation.y - pad)..(annotation.y + annotation.height + pad)
    }
}

internal fun DrawScope.drawAnnotation(
    annotation: Annotation,
    pageInfo: PdfPageInfo,
    imageBitmapCache: Map<String, ImageBitmap?>,
    signatureBitmapCache: Map<String, ImageBitmap?> = emptyMap()
) {
    when (annotation) {
        is Annotation.Stroke -> {
            val path = annotation.path
            if (path.points.size < 2) return
            if (path.isHighlighter) {
                drawPathFromPoints(
                    points = path.points,
                    color = path.color.copy(alpha = 0.6f),
                    strokeWidth = path.strokeWidth,
                    pageInfo = pageInfo,
                    blendMode = BlendMode.Multiply
                )
            } else {
                drawPathFromPoints(
                    points = path.points,
                    color = path.color,
                    strokeWidth = path.strokeWidth,
                    pageInfo = pageInfo
                )
            }
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

        is Annotation.Underline -> {
            val start = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val end = normalizedToScreen(PathPoint(annotation.x + annotation.width, annotation.y), pageInfo)
            drawLine(
                color = annotation.color,
                start = start,
                end = end,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        is Annotation.Strikethrough -> {
            val start = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val end = normalizedToScreen(PathPoint(annotation.x + annotation.width, annotation.y), pageInfo)
            drawLine(
                color = annotation.color,
                start = start,
                end = end,
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }

        is Annotation.Table -> {
            val topLeft = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val bottomRight = normalizedToScreen(
                PathPoint(annotation.x + annotation.width, annotation.y + annotation.height), pageInfo
            )
            val tableW = bottomRight.x - topLeft.x
            val tableH = bottomRight.y - topLeft.y
            val cellW = tableW / annotation.cols
            val cellH = tableH / annotation.rows

            // Draw cell borders
            for (row in 0..annotation.rows) {
                val yPos = topLeft.y + row * cellH
                drawLine(
                    color = annotation.color,
                    start = Offset(topLeft.x, yPos),
                    end = Offset(bottomRight.x, yPos),
                    strokeWidth = 1.5f
                )
            }
            for (col in 0..annotation.cols) {
                val xPos = topLeft.x + col * cellW
                drawLine(
                    color = annotation.color,
                    start = Offset(xPos, topLeft.y),
                    end = Offset(xPos, bottomRight.y),
                    strokeWidth = 1.5f
                )
            }
        }

        is Annotation.Comment -> {
            val pos = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            // Gelbes Kommentar-Fähnchen: Kreis mit "C"
            drawCircle(
                color = Color(0xFFFFC107),
                radius = 14f,
                center = pos
            )
            drawCircle(
                color = Color(0xFFFF8F00),
                radius = 14f,
                center = pos,
                style = Stroke(width = 2f)
            )
            // Kleines weißes Rechteck als Zeilen-Symbol
            drawRect(
                color = Color.White,
                topLeft = Offset(pos.x - 6f, pos.y - 4f),
                size = Size(12f, 2.5f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(pos.x - 6f, pos.y - 0.5f),
                size = Size(9f, 2.5f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(pos.x - 6f, pos.y + 3f),
                size = Size(10f, 2.5f)
            )
        }

        is Annotation.Image -> {
            val topLeft = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val bottomRight = normalizedToScreen(
                PathPoint(annotation.x + annotation.width, annotation.y + annotation.height), pageInfo
            )
            val imgW = bottomRight.x - topLeft.x
            val imgH = bottomRight.y - topLeft.y
            val bitmap = imageBitmapCache[annotation.uriString]
            if (bitmap != null) {
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(imgW.toInt().coerceAtLeast(1), imgH.toInt().coerceAtLeast(1))
                )
            } else {
                // Placeholder: grey rect with "Bild" label border
                drawRect(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    topLeft = topLeft,
                    size = Size(imgW, imgH)
                )
                drawRect(
                    color = Color.Gray,
                    topLeft = topLeft,
                    size = Size(imgW, imgH),
                    style = Stroke(width = 2f)
                )
            }
        }

        is Annotation.Link -> {
            val pos = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val linkColor = annotation.color
            val textSize = 14f * density
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                color = linkColor.toArgb()
                this.textSize = textSize
                typeface = Typeface.DEFAULT
                isUnderlineText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                annotation.displayText,
                pos.x,
                pos.y,
                paint
            )
        }

        is Annotation.SmartGraphic -> {
            val tl = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val br = normalizedToScreen(
                PathPoint(annotation.x + annotation.width, annotation.y + annotation.height), pageInfo
            )
            val w = br.x - tl.x
            val h = br.y - tl.y
            when (annotation.type) {
                SmartGraphicType.MIND_MAP -> drawMindMap(tl, w, h, annotation.color)
                SmartGraphicType.ORG_CHART -> drawOrgChart(tl, w, h, annotation.color)
            }
        }

        is Annotation.Signature -> {
            val topLeft = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val bottomRight = normalizedToScreen(
                PathPoint(annotation.x + annotation.width, annotation.y + annotation.height), pageInfo
            )
            val imgW = bottomRight.x - topLeft.x
            val imgH = bottomRight.y - topLeft.y
            val bitmap = signatureBitmapCache[annotation.id] ?: try {
                BitmapFactory.decodeByteArray(annotation.bitmapData, 0, annotation.bitmapData.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
            if (bitmap != null) {
                drawImage(
                    image = bitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(imgW.toInt().coerceAtLeast(1), imgH.toInt().coerceAtLeast(1))
                )
            } else {
                drawRect(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    topLeft = topLeft,
                    size = Size(imgW, imgH)
                )
                drawRect(
                    color = Color.Gray,
                    topLeft = topLeft,
                    size = Size(imgW, imgH),
                    style = Stroke(width = 1f)
                )
            }
        }

        is Annotation.TextBox -> {
            val tl = normalizedToScreen(PathPoint(annotation.x, annotation.y), pageInfo)
            val textSize = annotation.fontSize * density
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                color = annotation.color.toArgb()
                this.textSize = textSize
                isFakeBoldText = annotation.isBold
                isUnderlineText = annotation.isUnderline
                isStrikeThruText = annotation.isStrikethrough
                typeface = when (annotation.fontFamily) {
                    TextBoxFont.SERIF -> if (annotation.isItalic) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.SERIF
                    TextBoxFont.MONOSPACE -> if (annotation.isItalic) Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC) else Typeface.MONOSPACE
                    TextBoxFont.DEFAULT -> if (annotation.isItalic) Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) else Typeface.DEFAULT
                }
            }
            val lineHeight = textSize * 1.3f
            val maxW = annotation.width * pageInfo.pageWidth
            val lines = wrapText(annotation.text, paint, maxW)
            var lineY = tl.y + textSize
            for (line in lines) {
                drawContext.canvas.nativeCanvas.drawText(line, tl.x, lineY, paint)
                lineY += lineHeight
            }
        }
    }
}

/** Simple word-wrap: splits text into lines that fit within [maxWidth] pixels. */
private fun wrapText(text: String, paint: AndroidPaint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(candidate) <= maxWidth) {
            current = StringBuilder(candidate)
        } else {
            if (current.isNotEmpty()) lines.add(current.toString())
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    // Also split on explicit newlines
    return lines.flatMap { it.split("\n") }
}

private fun DrawScope.drawMindMap(
    topLeft: Offset,
    w: Float,
    h: Float,
    color: Color
) {
    val cx = topLeft.x + w / 2f
    val cy = topLeft.y + h / 2f
    val nodeR = minOf(w, h) * 0.12f
    val strokeW = 2f

    // Center node
    drawCircle(color = color.copy(alpha = 0.15f), radius = nodeR, center = Offset(cx, cy))
    drawCircle(color = color, radius = nodeR, center = Offset(cx, cy), style = Stroke(width = strokeW))

    // 5 branch nodes evenly placed
    val branchR = nodeR * 0.7f
    val armLength = minOf(w, h) * 0.30f
    val angles = listOf(0f, 72f, 144f, 216f, 288f)
    for (angleDeg in angles) {
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val bx = cx + cos(rad) * armLength
        val by = cy + sin(rad) * armLength
        drawLine(color = color.copy(alpha = 0.7f), start = Offset(cx, cy), end = Offset(bx, by), strokeWidth = strokeW)
        drawCircle(color = color.copy(alpha = 0.1f), radius = branchR, center = Offset(bx, by))
        drawCircle(color = color, radius = branchR, center = Offset(bx, by), style = Stroke(width = strokeW))
    }
}

private fun DrawScope.drawOrgChart(
    topLeft: Offset,
    w: Float,
    h: Float,
    color: Color
) {
    val strokeW = 2f
    val boxW = w * 0.22f
    val boxH = h * 0.16f

    // Root box centered at top
    val rootCx = topLeft.x + w / 2f
    val rootCy = topLeft.y + boxH / 2f + h * 0.05f
    drawOrgBox(Offset(rootCx - boxW / 2f, rootCy - boxH / 2f), boxW, boxH, color, strokeW)

    // Second level: 3 boxes
    val level2Y = topLeft.y + h * 0.45f
    val level2Xs = listOf(topLeft.x + w * 0.12f, topLeft.x + w * 0.5f - boxW / 2f, topLeft.x + w * 0.88f - boxW)
    for (lx in level2Xs) {
        val nodeCx = lx + boxW / 2f
        val nodeCy = level2Y + boxH / 2f
        // Connector from root to this node
        drawLine(
            color = color.copy(alpha = 0.7f),
            start = Offset(rootCx, rootCy + boxH / 2f),
            end = Offset(nodeCx, nodeCy - boxH / 2f),
            strokeWidth = strokeW
        )
        drawOrgBox(Offset(lx, level2Y), boxW, boxH, color, strokeW)
    }

    // Third level: one child under the middle second-level box
    val midL2Cx = level2Xs[1] + boxW / 2f
    val midL2BottomY = level2Y + boxH
    val l3Y = topLeft.y + h * 0.78f
    val l3Cx = midL2Cx
    drawLine(
        color = color.copy(alpha = 0.7f),
        start = Offset(midL2Cx, midL2BottomY),
        end = Offset(l3Cx, l3Y),
        strokeWidth = strokeW
    )
    drawOrgBox(Offset(l3Cx - boxW / 2f, l3Y), boxW, boxH, color, strokeW)
}

private fun DrawScope.drawOrgBox(topLeft: Offset, w: Float, h: Float, color: Color, strokeW: Float) {
    drawRect(color = color.copy(alpha = 0.08f), topLeft = topLeft, size = Size(w, h))
    drawRect(color = color, topLeft = topLeft, size = Size(w, h), style = Stroke(width = strokeW))
}

private fun DrawScope.drawPathFromPoints(
    points: List<PathPoint>,
    color: Color,
    strokeWidth: Float,
    pageInfo: PdfPageInfo,
    blendMode: BlendMode = BlendMode.SrcOver
) {
    if (points.size < 2) return

    val path = Path()
    val first = normalizedToScreen(points.first(), pageInfo)
    path.moveTo(first.x, first.y)

    // Catmull-Rom spline for smooth curves
    for (i in 1 until points.size) {
        val pt = normalizedToScreen(points[i], pageInfo)
        if (i == 1 || points.size <= 3) {
            path.lineTo(pt.x, pt.y)
        } else {
            val prev = normalizedToScreen(points[i - 1], pageInfo)
            val cp1x = prev.x + (pt.x - normalizedToScreen(points[i - 2], pageInfo).x) / 6f
            val cp1y = prev.y + (pt.y - normalizedToScreen(points[i - 2], pageInfo).y) / 6f
            val next = if (i + 1 < points.size) normalizedToScreen(points[i + 1], pageInfo) else pt
            val cp2x = pt.x - (next.x - prev.x) / 6f
            val cp2y = pt.y - (next.y - prev.y) / 6f
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, pt.x, pt.y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blendMode
    )
}
