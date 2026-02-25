package com.annotatio.maximus.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.DrawingPath
import com.annotatio.maximus.model.PathPoint
import com.annotatio.maximus.ui.components.AnnotationCanvas
import com.annotatio.maximus.ui.components.AnnotationDisplayOverlay
import com.annotatio.maximus.ui.components.AnnotationLayerOverlay
import com.annotatio.maximus.ui.components.AnnotationToolbar
import com.annotatio.maximus.ui.components.GeminiSketchDialog
import com.annotatio.maximus.ui.components.SettingsDialog
import com.annotatio.maximus.ui.components.SignatureDialog
import com.annotatio.maximus.ui.components.PdfPageInfo
import com.annotatio.maximus.ui.components.PdfViewer
import com.annotatio.maximus.ui.components.ToolOptionsPanel
import com.annotatio.maximus.util.loadToolbarVisibility
import com.annotatio.maximus.util.saveToolbarVisibility
import com.annotatio.maximus.viewmodel.PdfViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun PdfViewerScreen(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Collect all state
    val pdfUri by viewModel.pdfUri.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val penColor by viewModel.penColor.collectAsState()
    val strokeWidth by viewModel.strokeWidth.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    // Gemini sketch dialog state
    var showGeminiSketch by remember { mutableStateOf(false) }

    // Signature dialog state
    var showSignatureDialog by remember { mutableStateOf(false) }

    // Page management dialogs
    var showDeletePagesDialog by remember { mutableStateOf(false) }
    var showReorderPagesDialog by remember { mutableStateOf(false) }

    // Toolbar-Button-Sichtbarkeit (aus Einstellungen, persistent)
    var toolbarVisibility by remember(context) {
        mutableStateOf(loadToolbarVisibility(context))
    }
    var showSettings by remember { mutableStateOf(false) }

    // Ausgewählte Ebene (Stroke) auf der aktuellen Seite
    var selectedAnnotationId by remember(currentPage) { mutableStateOf<String?>(null) }

    // Page info from PDF viewer
    var pageInfo by remember { mutableStateOf(PdfPageInfo()) }

    // Text note dialog state
    var showTextNoteDialog by remember { mutableStateOf(false) }
    var textNotePosition by remember { mutableStateOf(Pair(0f, 0f)) }
    var textNoteInput by remember { mutableStateOf("") }

    // Comment dialog state
    var showCommentDialog by remember { mutableStateOf(false) }
    var commentPosition by remember { mutableStateOf(Pair(0f, 0f)) }
    var commentInput by remember { mutableStateOf("") }
    var editingComment by remember { mutableStateOf<Annotation.Comment?>(null) }

    // File picker
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.openPdf(it)
        }
    }

    // Save picker
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.saveAnnotatedPdf(context, it) }
    }

    // Save error dialog
    if (saveError != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearSaveError,
            title = { Text("Speichern fehlgeschlagen") },
            text = { Text(saveError!!) },
            confirmButton = {
                TextButton(onClick = viewModel::clearSaveError) {
                    Text("OK")
                }
            }
        )
    }

    // Text note dialog
    if (showTextNoteDialog) {
        AlertDialog(
            onDismissRequest = {
                showTextNoteDialog = false
                textNoteInput = ""
            },
            title = { Text("Notiz hinzufügen") },
            text = {
                TextField(
                    value = textNoteInput,
                    onValueChange = { textNoteInput = it },
                    placeholder = { Text("Text eingeben...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textNoteInput.isNotBlank()) {
                            viewModel.addAnnotation(
                                Annotation.TextNote(
                                    pageIndex = currentPage,
                                    x = textNotePosition.first,
                                    y = textNotePosition.second,
                                    text = textNoteInput,
                                    color = penColor
                                )
                            )
                        }
                        showTextNoteDialog = false
                        textNoteInput = ""
                    }
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTextNoteDialog = false
                        textNoteInput = ""
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Comment dialog (neu anlegen oder bearbeiten)
    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = {
                showCommentDialog = false
                commentInput = ""
                editingComment = null
            },
            title = { Text(if (editingComment != null) "Kommentar bearbeiten" else "Kommentar hinzufügen") },
            text = {
                TextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text("Kommentar eingeben...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (commentInput.isNotBlank()) {
                            val existing = editingComment
                            if (existing != null) {
                                viewModel.updateAnnotation(existing.copy(text = commentInput))
                            } else {
                                viewModel.addAnnotation(
                                    Annotation.Comment(
                                        pageIndex = currentPage,
                                        x = commentPosition.first,
                                        y = commentPosition.second,
                                        text = commentInput,
                                        color = penColor
                                    )
                                )
                            }
                        }
                        showCommentDialog = false
                        commentInput = ""
                        editingComment = null
                    }
                ) {
                    Text(if (editingComment != null) "Speichern" else "Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCommentDialog = false
                    commentInput = ""
                    editingComment = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (isLandscape) {
        // Landscape layout: Toolbar on the left, content on the right
        Row(modifier = Modifier.fillMaxSize()) {
            // Left panel: Toolbar
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                AnnotationToolbar(
                    activeTool = activeTool,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    hasDocument = pdfUri != null,
                    toolbarVisibility = toolbarVisibility,
                    onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                    onSettingsClick = { showSettings = true },
                    onToolSelected = viewModel::selectTool,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSave = { saveFileLauncher.launch("annotated_document.pdf") },
                    onOpenGeminiSketch = { showGeminiSketch = true }
                )
                ToolOptionsPanel(
                    activeTool = activeTool,
                    selectedColor = penColor,
                    strokeWidth = strokeWidth,
                    onColorSelected = viewModel::setColor,
                    onStrokeWidthChanged = viewModel::setStrokeWidth
                )
            }
            // Right panel: PDF content
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (pdfUri != null) {
                    // PDF Viewer (bottom layer)
                    PdfViewer(
                        pdfUri = pdfUri,
                        modifier = Modifier.fillMaxSize(),
                        onPageChanged = viewModel::onPageChanged,
                        onPageInfoUpdated = { pageInfo = it }
                    )

                    // Annotation layer
                    val currentPageAnnotations = annotations[currentPage] ?: emptyList()

                    if (activeTool != null) {
                        // Active tool: canvas intercepts touches
                        AnnotationCanvas(
                            annotations = currentPageAnnotations,
                            currentPageIndex = currentPage,
                            activeTool = activeTool!!,
                            penColor = penColor,
                            penStrokeWidth = strokeWidth,
                            pageInfo = pageInfo,
                            onAddAnnotation = viewModel::addAnnotation,
                            onRemoveAnnotation = viewModel::removeAnnotation,
                            onRequestTextNote = { x, y ->
                                textNotePosition = Pair(x, y)
                                showTextNoteDialog = true
                            },
                            onRequestSignature = { x, y ->
                                showSignatureDialog = true
                            },
                            onRequestComment = { x, y ->
                                commentPosition = Pair(x, y)
                                commentInput = ""
                                editingComment = null
                                showCommentDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Kein Werkzeug aktiv: Ebenen-Modus mit auswählbaren/movebaren Strokes
                        AnnotationLayerOverlay(
                            annotations = currentPageAnnotations,
                            pageInfo = pageInfo,
                            selectedId = selectedAnnotationId,
                            onSelectedChange = { selectedAnnotationId = it },
                            onUpdateAnnotation = viewModel::updateAnnotation,
                            onRemoveAnnotation = { id ->
                                viewModel.removeAnnotation(id)
                                if (selectedAnnotationId == id) {
                                    selectedAnnotationId = null
                                }
                            },
                            onCopyAnnotation = { annotation ->
                                copyStrokeToClipboard(context, annotation, pageInfo)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        // Kommentar-Tap im Landscape-Modus
                        AnnotationDisplayOverlay(
                            annotations = currentPageAnnotations.filterIsInstance<Annotation.Comment>(),
                            pageInfo = pageInfo,
                            onCommentTapped = { comment ->
                                editingComment = comment
                                commentInput = comment.text
                                showCommentDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Saving indicator
                    if (isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PDF öffnen, um zu beginnen",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        // Portrait layout: Original Scaffold layout
        Scaffold(
            topBar = {
                Column {
                    AnnotationToolbar(
                        activeTool = activeTool,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        hasDocument = pdfUri != null,
                        toolbarVisibility = toolbarVisibility,
                        onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                        onSettingsClick = { showSettings = true },
                        onToolSelected = viewModel::selectTool,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onSave = { saveFileLauncher.launch("annotated_document.pdf") },
                        onOpenGeminiSketch = { showGeminiSketch = true }
                    )
                    ToolOptionsPanel(
                        activeTool = activeTool,
                        selectedColor = penColor,
                        strokeWidth = strokeWidth,
                        onColorSelected = viewModel::setColor,
                        onStrokeWidthChanged = viewModel::setStrokeWidth
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (pdfUri != null) {
                    // PDF Viewer (bottom layer)
                    PdfViewer(
                        pdfUri = pdfUri,
                        modifier = Modifier.fillMaxSize(),
                        onPageChanged = viewModel::onPageChanged,
                        onPageInfoUpdated = { pageInfo = it }
                    )

                    // Annotation layer
                    val currentPageAnnotations = annotations[currentPage] ?: emptyList()

                    if (activeTool != null) {
                        // Active tool: canvas intercepts touches
                        AnnotationCanvas(
                            annotations = currentPageAnnotations,
                            currentPageIndex = currentPage,
                            activeTool = activeTool!!,
                            penColor = penColor,
                            penStrokeWidth = strokeWidth,
                            pageInfo = pageInfo,
                            onAddAnnotation = viewModel::addAnnotation,
                            onRemoveAnnotation = viewModel::removeAnnotation,
                            onRequestTextNote = { x, y ->
                                textNotePosition = Pair(x, y)
                                showTextNoteDialog = true
                            },
                            onRequestSignature = { x, y ->
                                showSignatureDialog = true
                            },
                            onRequestComment = { x, y ->
                                commentPosition = Pair(x, y)
                                commentInput = ""
                                editingComment = null
                                showCommentDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // No tool: read-only display + comment tap
                        AnnotationDisplayOverlay(
                            annotations = currentPageAnnotations,
                            pageInfo = pageInfo,
                            onCommentTapped = { comment ->
                                editingComment = comment
                                commentInput = comment.text
                                showCommentDialog = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Saving indicator
                    if (isSaving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PDF öffnen, um zu beginnen",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showGeminiSketch) {
        GeminiSketchDialog(
            initialColor = penColor,
            strokeWidth = strokeWidth,
            onDismiss = { showGeminiSketch = false },
            onSend = { bitmap ->
                // Persist bitmap to a cache file
                val cacheDir = File(context.cacheDir, "gemini_sketches").apply { mkdirs() }
                val file = File(cacheDir, "sketch_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Copy image URI to clipboard
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val clip = ClipData.newUri(context.contentResolver, "Gemini Skizze", uri)
                clipboard?.setPrimaryClip(clip)

                // Share image via chooser so Gemini (oder Browser) ausgewählt werden kann
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(sendIntent, "Skizze an Gemini senden")
                context.startActivity(chooser)

                showGeminiSketch = false
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            toolbarVisibility = toolbarVisibility,
            onToolbarVisibilityChange = { newMap ->
                toolbarVisibility = newMap
                saveToolbarVisibility(context, newMap)
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onSignatureCaptured = { bitmap ->
                // TODO: Handle signature bitmap
                showSignatureDialog = false
            },
            onDismiss = { showSignatureDialog = false }
        )
    }
}

private fun copyStrokeToClipboard(
    context: android.content.Context,
    annotation: Annotation,
    pageInfo: PdfPageInfo
) {
    val stroke = annotation as? Annotation.Stroke ?: return
    if (pageInfo.pageWidth <= 0f || pageInfo.pageHeight <= 0f) return
    if (stroke.path.points.size < 2) return

    // Bounds im Seitenraum (Pixel)
    var minX = stroke.path.points.first().x
    var maxX = stroke.path.points.first().x
    var minY = stroke.path.points.first().y
    var maxY = stroke.path.points.first().y
    for (pt in stroke.path.points) {
        if (pt.x < minX) minX = pt.x
        if (pt.x > maxX) maxX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.y > maxY) maxY = pt.y
    }
    val normBounds = Rect(minX, minY, maxX, maxY)

    val leftPx = normBounds.left * pageInfo.pageWidth
    val topPx = normBounds.top * pageInfo.pageHeight
    val widthPx = (normBounds.width * pageInfo.pageWidth).coerceAtLeast(1f)
    val heightPx = (normBounds.height * pageInfo.pageHeight).coerceAtLeast(1f)

    val bitmap = Bitmap.createBitmap(
        widthPx.toInt(),
        heightPx.toInt(),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = stroke.path.strokeWidth
        color = stroke.path.color.toArgb()
    }

    var last: PathPoint? = null
    for (pt in stroke.path.points) {
        val x = pt.x * pageInfo.pageWidth - leftPx
        val y = pt.y * pageInfo.pageHeight - topPx
        if (last != null) {
            val lx = last.x * pageInfo.pageWidth - leftPx
            val ly = last.y * pageInfo.pageHeight - topPx
            canvas.drawLine(lx, ly, x, y, paint)
        }
        last = pt
    }

    // Als Datei persistieren und URI in die Zwischenablage legen
    val cacheDir = File(context.cacheDir, "annotation_layers").apply { mkdirs() }
    val file = File(cacheDir, "layer_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newUri(context.contentResolver, "Annotation-Ebene", uri)
    clipboard?.setPrimaryClip(clip)
}
