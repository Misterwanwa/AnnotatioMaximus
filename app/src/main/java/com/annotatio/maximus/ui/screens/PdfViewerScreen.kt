package com.annotatio.maximus.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.DrawingPath
import com.annotatio.maximus.model.PageTextContent
import com.annotatio.maximus.model.PathPoint
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.ui.components.AnnotationCanvas
import com.annotatio.maximus.ui.components.AnnotationToolbar
import com.annotatio.maximus.ui.components.GeminiSketchDialog
import com.annotatio.maximus.ui.components.ConverterDialog
import com.annotatio.maximus.ui.components.ScannerDialog
import com.annotatio.maximus.ui.components.TextBoxEditDialog
import com.annotatio.maximus.ui.components.SmartGraphicPickerDialog
import com.annotatio.maximus.ui.components.TranslatorDialog
import com.annotatio.maximus.ui.components.loadTargetLanguage
import com.annotatio.maximus.ui.components.saveTargetLanguage
import com.annotatio.maximus.ui.components.AnnotationDisplayOverlay
import com.annotatio.maximus.ui.components.AnnotationLayerOverlay
import com.annotatio.maximus.ui.components.BookmarkPanel
import com.annotatio.maximus.ui.components.BugReportDialog
import com.annotatio.maximus.ui.components.CloudStorageDialog
import com.annotatio.maximus.ui.components.CreatePdfDialog
import com.annotatio.maximus.ui.components.LinkDialog
import com.annotatio.maximus.ui.components.PasswordDialog
import com.annotatio.maximus.ui.components.SearchDialog
import com.annotatio.maximus.ui.components.TtsDialog
import com.annotatio.maximus.ui.components.WifiShareDialog
import com.annotatio.maximus.ui.components.SelectLassoOverlay
import com.annotatio.maximus.ui.components.SettingsDialog
import com.annotatio.maximus.ui.components.SignatureDialog
import com.annotatio.maximus.ui.components.TableEditorDialog
import com.annotatio.maximus.ui.components.TablePickerDialog
import com.annotatio.maximus.ui.components.PdfPageInfo
import com.annotatio.maximus.ui.components.PdfViewer
import android.widget.Toast
import com.annotatio.maximus.ui.components.ToolOptionsPanel
import com.annotatio.maximus.ui.theme.AppTheme
import com.annotatio.maximus.ui.theme.LocalAppTheme
import com.annotatio.maximus.util.GitHubApi
import com.annotatio.maximus.util.loadToolbarVisibility
import com.annotatio.maximus.util.saveToolbarVisibility
import com.annotatio.maximus.viewmodel.PdfViewModel
import java.io.File
import java.io.FileOutputStream

@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel,
    appTheme: AppTheme = AppTheme.CUPERTINO_MINIMALIST,
    onAppThemeChange: (AppTheme) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val coroutineScope = rememberCoroutineScope()
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
    val bookmarks by viewModel.bookmarks.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val pageTextContents by viewModel.pageTextContents.collectAsState()
    
    // Get current page text content
    val currentPageTextContent = remember(currentPage, pageTextContents) {
        pageTextContents[currentPage]
    }

    // Gemini sketch dialog state
    var showGeminiSketch by remember { mutableStateOf(false) }

    // Signature dialog state
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signaturePendingPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showImageOrSignatureDialog by remember { mutableStateOf(false) }

    // Page management dialogs
    var showDeletePagesDialog by remember { mutableStateOf(false) }
    var showReorderPagesDialog by remember { mutableStateOf(false) }

    // Toolbar-Button-Sichtbarkeit (aus Einstellungen, persistent)
    var toolbarVisibility by remember(context) {
        mutableStateOf(loadToolbarVisibility(context))
    }
    var showSettings by remember { mutableStateOf(false) }
    var showBugReportDialog by remember { mutableStateOf(false) }
    var isSubmittingBugReport by remember { mutableStateOf(false) }
    var bugReportResult by remember { mutableStateOf<Result<String>?>(null) }

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

    // Table dialog state (two-step: picker → editor)
    var showTablePicker by remember { mutableStateOf(false) }
    var showTableEditor by remember { mutableStateOf(false) }
    var tablePosition by remember { mutableStateOf(Pair(0f, 0f)) }
    var tablePendingRows by remember { mutableStateOf(3) }
    var tablePendingCols by remember { mutableStateOf(3) }
    var editingTable by remember { mutableStateOf<Annotation.Table?>(null) }

    // Hoisted image bitmap cache — persists across overlay switches so images remain visible
    val imageBitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
    val currentPageAnnotations0 = annotations[currentPage] ?: emptyList()
    LaunchedEffect(currentPageAnnotations0) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            for (img in currentPageAnnotations0.filterIsInstance<Annotation.Image>()) {
                if (!imageBitmapCache.containsKey(img.uriString)) {
                    val bitmap = try {
                        context.contentResolver.openInputStream(Uri.parse(img.uriString))?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    } catch (_: Exception) {
                        null
                    }
                    imageBitmapCache[img.uriString] = bitmap
                }
            }
        }
    }

    // Lazy page text extraction when page changes or PDF loads
    LaunchedEffect(currentPage, pdfUri) {
        if (pdfUri != null) {
            viewModel.loadPageTextContent(context, currentPage)
        }
    }

    // Image position pending placement (after picker returns)
    var imagePendingPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val pos = imagePendingPosition ?: Pair(0.1f, 0.1f)
            viewModel.addAnnotation(
                Annotation.Image(
                    pageIndex = currentPage,
                    x = pos.first,
                    y = pos.second,
                    width = 0.4f,
                    height = 0.3f,
                    uriString = it.toString()
                )
            )
            imagePendingPosition = null
        }
    }

    // Link dialog state
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkPosition by remember { mutableStateOf(Pair(0f, 0f)) }

    // Smart graphic state
    var pendingSmartGraphicType by remember {
        mutableStateOf(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP)
    }

    // Converter dialog state
    var showConverterDialog by remember { mutableStateOf(false) }
    // Temp file waiting to be saved after conversion
    var pendingConvertedFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingConvertedMime by remember { mutableStateOf("application/octet-stream") }
    val converterSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { dest ->
            val src = pendingConvertedFile ?: return@let
            try {
                context.contentResolver.openOutputStream(dest)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Fehler beim Speichern der Datei: ${e.localizedMessage ?: "Unbekannter Fehler"}. Bitte wähle einen anderen Speicherort.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            pendingConvertedFile = null
        }
    }

    // Translator state
    var showTranslatorDialog by remember { mutableStateOf(false) }
    var translatorLanguageCode by remember(context) {
        mutableStateOf(loadTargetLanguage(context))
    }

    // Scanner state
    var showScannerDialog by remember { mutableStateOf(false) }

    // TextBox state
    var showTextBoxDialog by remember { mutableStateOf(false) }
    var textBoxPosition by remember { mutableStateOf(Pair(0.1f, 0.1f)) }
    var editingTextBox by remember { mutableStateOf<Annotation.TextBox?>(null) }

    // New feature dialog states
    var showBookmarkPanel by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var showWifiShareDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var showCreatePdfDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    // File picker (local storage and cloud providers)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.openPdf(it, context)
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
        val errorMessage = when {
            saveError!!.contains("Permission denied", ignoreCase = true) || 
            saveError!!.contains("EACCES", ignoreCase = true) -> 
                "Kein Zugriff auf den gewählten Speicherort. Bitte wähle einen anderen Ordner oder erteile der App die nötigen Dateiberechtigungen."
            saveError!!.contains("No space left on device", ignoreCase = true) || 
            saveError!!.contains("ENOSPC", ignoreCase = true) ->
                "Nicht genügend Speicherplatz auf dem Gerät verfügbar. Bitte gib etwas Speicherplatz frei und versuche es erneut."
            else -> 
                "Die PDF-Datei konnte nicht gespeichert werden ($saveError). Bitte überprüfe den Speicherort und versuche es noch einmal."
        }
        AlertDialog(
            onDismissRequest = viewModel::clearSaveError,
            title = { Text("Speichern fehlgeschlagen") },
            text = { Text(errorMessage) },
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

    // Table picker dialog (matrix selection)
    if (showTablePicker) {
        TablePickerDialog(
            onDismiss = { showTablePicker = false },
            onSizeSelected = { rows, cols ->
                tablePendingRows = rows
                tablePendingCols = cols
                showTablePicker = false
                showTableEditor = true
            }
        )
    }

    // Table editor dialog (fill in cell texts)
    if (showTableEditor) {
        val existing = editingTable
        TableEditorDialog(
            rows = if (existing != null) existing.rows else tablePendingRows,
            cols = if (existing != null) existing.cols else tablePendingCols,
            initialCells = existing?.cells,
            onDismiss = {
                showTableEditor = false
                editingTable = null
            },
            onConfirm = { cells ->
                if (existing != null) {
                    viewModel.updateAnnotation(existing.copy(cells = cells))
                } else {
                    viewModel.addAnnotation(
                        Annotation.Table(
                            pageIndex = currentPage,
                            x = tablePosition.first,
                            y = tablePosition.second,
                            width = 0.5f,
                            height = 0.25f,
                            rows = tablePendingRows,
                            cols = tablePendingCols,
                            cells = cells,
                            color = penColor
                        )
                    )
                }
                showTableEditor = false
                editingTable = null
            }
        )
    }

    // Link dialog
    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { url, displayText ->
                viewModel.addAnnotation(
                    Annotation.Link(
                        pageIndex = currentPage,
                        x = linkPosition.first,
                        y = linkPosition.second,
                        displayText = displayText.ifBlank { url },
                        url = url,
                        color = penColor
                    )
                )
                showLinkDialog = false
            }
        )
    }

    // Converter dialog
    if (showConverterDialog && pdfUri != null) {
        ConverterDialog(
            pdfUri = pdfUri!!,
            onDismiss = { showConverterDialog = false },
            onRequestSave = { file, mime ->
                pendingConvertedFile = file
                pendingConvertedMime = mime
                val ext = file.extension
                converterSaveLauncher.launch("converted_document.$ext")
                showConverterDialog = false
            }
        )
    }

    // Translator dialog
    if (showTranslatorDialog) {
        TranslatorDialog(
            targetLangCode = translatorLanguageCode,
            onDismiss = { showTranslatorDialog = false }
        )
    }

    // Scanner dialog
    if (showScannerDialog) {
        ScannerDialog(onDismiss = { showScannerDialog = false })
    }

    // TextBox create/edit dialog
    if (showTextBoxDialog) {
        TextBoxEditDialog(
            initial = editingTextBox,
            pageIndex = currentPage,
            posX = textBoxPosition.first,
            posY = textBoxPosition.second,
            onDismiss = {
                showTextBoxDialog = false
                editingTextBox = null
            },
            onConfirm = { textBox ->
                val existing = editingTextBox
                if (existing != null) {
                    viewModel.updateAnnotation(textBox)
                } else {
                    viewModel.addAnnotation(textBox)
                }
                showTextBoxDialog = false
                editingTextBox = null
            }
        )
    }

    // ── New feature dialogs ──────────────────────────────────────────────────

    // Bookmarks panel
    if (showBookmarkPanel) {
        BookmarkPanel(
            bookmarks = bookmarks,
            currentPage = currentPage,
            onNavigate = { page -> viewModel.navigateToPage(page) },
            onAdd = { bm -> viewModel.addBookmark(context, bm) },
            onDelete = { bm -> viewModel.removeBookmark(context, bm) },
            onRename = { bm, label -> viewModel.renameBookmark(context, bm, label) },
            onDismiss = { showBookmarkPanel = false }
        )
    }

    // TTS dialog
    if (showTtsDialog) {
        val pdfFile = pdfUri?.let { uri ->
            try {
                val tmp = File(context.cacheDir, "tts_tmp.pdf")
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                tmp
            } catch (_: Exception) { null }
        }
        TtsDialog(
            pdfFile = pdfFile,
            currentPage = currentPage,
            pageCount = pageCount,
            onDismiss = { showTtsDialog = false }
        )
    }

    // WiFi share dialog
    if (showWifiShareDialog) {
        WifiShareDialog(
            pdfUri = pdfUri,
            onDismiss = { showWifiShareDialog = false }
        )
    }

    // Cloud storage dialog
    if (showCloudDialog) {
        CloudStorageDialog(
            currentPdfUri = pdfUri,
            onOpenFromCloud = { openFileLauncher.launch(arrayOf("application/pdf")) },
            onDismiss = { showCloudDialog = false }
        )
    }

    // Create PDF dialog
    if (showCreatePdfDialog) {
        CreatePdfDialog(
            onDismiss = { showCreatePdfDialog = false },
            onPdfCreated = { uri ->
                viewModel.openPdf(uri, context)
                showCreatePdfDialog = false
            }
        )
    }

    // Password protection dialog
    if (showPasswordDialog) {
        PasswordDialog(
            pdfUri = pdfUri,
            onDismiss = { showPasswordDialog = false }
        )
    }

    // Search dialog
    if (showSearchDialog) {
        SearchDialog(
            pdfUri = pdfUri,
            onNavigateToPage = { page -> viewModel.navigateToPage(page) },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (isLandscape) {
        // Landscape layout: Toolbar on the left, content on the right
        Row(modifier = Modifier.fillMaxSize()) {
            // Left panel: Toolbar — background and padding handled per-theme
            val leftPanelModifier = when (appTheme) {
                AppTheme.LONGHORN_SLATE ->
                    Modifier.weight(0.25f).fillMaxHeight()
                else ->
                    Modifier.weight(0.3f).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
            }
            Column(modifier = leftPanelModifier) {
                AnnotationToolbar(
                    activeTool = activeTool,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    hasDocument = pdfUri != null,
                    toolbarVisibility = toolbarVisibility,
                    onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                    onSettingsClick = { showSettings = true },
                    onBugReportClick = { showBugReportDialog = true },
                    onBookmarksClick = { showBookmarkPanel = true },
                    onToolSelected = viewModel::selectTool,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSave = { saveFileLauncher.launch("annotated_document.pdf") },
                    onOpenGeminiSketch = { showGeminiSketch = true },
                    onOpenConverter = { showConverterDialog = true },
                    onOpenTranslator = { showTranslatorDialog = true },
                    onOpenScanner = { showScannerDialog = true },
                    onOpenTts = { showTtsDialog = true },
                    onOpenWifiShare = { showWifiShareDialog = true },
                    onOpenCloud = { showCloudDialog = true },
                    onOpenCreatePdf = { showCreatePdfDialog = true },
                    onOpenPassword = { showPasswordDialog = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenLink = {
                        linkPosition = Pair(0.1f, 0.1f)
                        showLinkDialog = true
                    },
                    onSmartGraphicSelected = { type -> pendingSmartGraphicType = type }
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

                    if (activeTool != null && activeTool != AnnotationType.SELECT && activeTool != AnnotationType.LASSO) {
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
                                signaturePendingPosition = Pair(x, y)
                                showSignatureDialog = true
                            },
                            onRequestComment = { x, y ->
                                commentPosition = Pair(x, y)
                                commentInput = ""
                                editingComment = null
                                showCommentDialog = true
                            },
                            onRequestTable = { x, y ->
                                tablePosition = Pair(x, y)
                                editingTable = null
                                showTablePicker = true
                            },
                            onRequestImage = { x, y ->
                                imagePendingPosition = Pair(x, y)
                                signaturePendingPosition = Pair(x, y)
                                showImageOrSignatureDialog = true
                            },
                            onRequestLink = { x, y ->
                                linkPosition = Pair(x, y)
                                showLinkDialog = true
                            },
                            onRequestTextBox = { x, y ->
                                textBoxPosition = Pair(x, y)
                                editingTextBox = null
                                showTextBoxDialog = true
                            },
                            onEditTextBox = { textBox ->
                                editingTextBox = textBox
                                textBoxPosition = Pair(textBox.x, textBox.y)
                                showTextBoxDialog = true
                            },
                            pendingSmartGraphicType = pendingSmartGraphicType,
                            externalImageCache = imageBitmapCache,
                            pageTextContent = currentPageTextContent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (activeTool == AnnotationType.SELECT || activeTool == AnnotationType.LASSO) {
                        // SELECT / LASSO: show overlay for multi-selection
                        SelectLassoOverlay(
                            annotations = currentPageAnnotations,
                            pageInfo = pageInfo,
                            activeTool = activeTool!!,
                            onUpdateAnnotation = viewModel::updateAnnotation,
                            onRemoveAnnotations = { ids -> ids.forEach { viewModel.removeAnnotation(it) } },
                            onDuplicateAnnotations = { copies -> copies.forEach { viewModel.addAnnotation(it) } },
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
                            externalImageCache = imageBitmapCache,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Kommentar-Tap + TextBox-Doppeltipp im Landscape-Modus
                        AnnotationDisplayOverlay(
                            annotations = currentPageAnnotations.filter { it is Annotation.Comment || it is Annotation.TextBox },
                            pageInfo = pageInfo,
                            onCommentTapped = { comment ->
                                editingComment = comment
                                commentInput = comment.text
                                showCommentDialog = true
                            },
                            onTextBoxDoubleTapped = { textBox ->
                                editingTextBox = textBox
                                textBoxPosition = Pair(textBox.x, textBox.y)
                                showTextBoxDialog = true
                            },
                            externalImageCache = imageBitmapCache,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { openFileLauncher.launch(arrayOf("application/pdf")) },
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
        // Portrait layout — Cupertino: bottom floating bar; Aero/Slate: top bar
        val toolbarComposable: @Composable () -> Unit = {
            Column {
                AnnotationToolbar(
                    activeTool = activeTool,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    hasDocument = pdfUri != null,
                    toolbarVisibility = toolbarVisibility,
                    onOpenFile = { openFileLauncher.launch(arrayOf("application/pdf")) },
                    onSettingsClick = { showSettings = true },
                    onBugReportClick = { showBugReportDialog = true },
                    onBookmarksClick = { showBookmarkPanel = true },
                    onToolSelected = viewModel::selectTool,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSave = { saveFileLauncher.launch("annotated_document.pdf") },
                    onOpenGeminiSketch = { showGeminiSketch = true },
                    onOpenConverter = { showConverterDialog = true },
                    onOpenTranslator = { showTranslatorDialog = true },
                    onOpenScanner = { showScannerDialog = true },
                    onOpenTts = { showTtsDialog = true },
                    onOpenWifiShare = { showWifiShareDialog = true },
                    onOpenCloud = { showCloudDialog = true },
                    onOpenCreatePdf = { showCreatePdfDialog = true },
                    onOpenPassword = { showPasswordDialog = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenLink = {
                        linkPosition = Pair(0.1f, 0.1f)
                        showLinkDialog = true
                    },
                    onSmartGraphicSelected = { type -> pendingSmartGraphicType = type }
                )
                if (appTheme != AppTheme.CUPERTINO_MINIMALIST) {
                    ToolOptionsPanel(
                        activeTool = activeTool,
                        selectedColor = penColor,
                        strokeWidth = strokeWidth,
                        onColorSelected = viewModel::setColor,
                        onStrokeWidthChanged = viewModel::setStrokeWidth
                    )
                }
            }
        }
        Scaffold(
            topBar    = if (appTheme != AppTheme.CUPERTINO_MINIMALIST) toolbarComposable else { {} },
            bottomBar = if (appTheme == AppTheme.CUPERTINO_MINIMALIST) ({
                Column(modifier = Modifier.navigationBarsPadding()) {
                    ToolOptionsPanel(
                        activeTool = activeTool,
                        selectedColor = penColor,
                        strokeWidth = strokeWidth,
                        onColorSelected = viewModel::setColor,
                        onStrokeWidthChanged = viewModel::setStrokeWidth
                    )
                    toolbarComposable()
                }
            }) else { {} }
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

                    if (activeTool != null && activeTool != AnnotationType.SELECT && activeTool != AnnotationType.LASSO) {
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
                                signaturePendingPosition = Pair(x, y)
                                showSignatureDialog = true
                            },
                            onRequestComment = { x, y ->
                                commentPosition = Pair(x, y)
                                commentInput = ""
                                editingComment = null
                                showCommentDialog = true
                            },
                            onRequestTable = { x, y ->
                                tablePosition = Pair(x, y)
                                editingTable = null
                                showTablePicker = true
                            },
                            onRequestImage = { x, y ->
                                imagePendingPosition = Pair(x, y)
                                signaturePendingPosition = Pair(x, y)
                                showImageOrSignatureDialog = true
                            },
                            onRequestLink = { x, y ->
                                linkPosition = Pair(x, y)
                                showLinkDialog = true
                            },
                            onRequestTextBox = { x, y ->
                                textBoxPosition = Pair(x, y)
                                editingTextBox = null
                                showTextBoxDialog = true
                            },
                            onEditTextBox = { textBox ->
                                editingTextBox = textBox
                                textBoxPosition = Pair(textBox.x, textBox.y)
                                showTextBoxDialog = true
                            },
                            pendingSmartGraphicType = pendingSmartGraphicType,
                            externalImageCache = imageBitmapCache,
                            pageTextContent = currentPageTextContent,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (activeTool == AnnotationType.SELECT || activeTool == AnnotationType.LASSO) {
                        // SELECT / LASSO: show overlay for multi-selection
                        SelectLassoOverlay(
                            annotations = currentPageAnnotations,
                            pageInfo = pageInfo,
                            activeTool = activeTool!!,
                            onUpdateAnnotation = viewModel::updateAnnotation,
                            onRemoveAnnotations = { ids -> ids.forEach { viewModel.removeAnnotation(it) } },
                            onDuplicateAnnotations = { copies -> copies.forEach { viewModel.addAnnotation(it) } },
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
                            onTextBoxDoubleTapped = { textBox ->
                                editingTextBox = textBox
                                textBoxPosition = Pair(textBox.x, textBox.y)
                                showTextBoxDialog = true
                            },
                            externalImageCache = imageBitmapCache,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { openFileLauncher.launch(arrayOf("application/pdf")) },
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
            onSend = { bitmap, prompt ->
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

                // Copy image URI and prompt to clipboard
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                val clip = ClipData("Gemini Skizze", arrayOf("image/png", "text/plain"), ClipData.Item(uri)).apply {
                    addItem(ClipData.Item(prompt))
                }
                clipboard?.setPrimaryClip(clip)

                // Share image and prompt text via chooser so Gemini (or other apps) can receive it
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, prompt)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                Toast.makeText(
                    context,
                    "Der Prompt wurde in die Zwischenablage kopiert!\nBitte fügen Sie ihn in der geöffneten App (z.B. Gemini) ein.",
                    Toast.LENGTH_LONG
                ).show()

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
            translatorLanguageCode = translatorLanguageCode,
            onTranslatorLanguageChange = { code ->
                translatorLanguageCode = code
                saveTargetLanguage(context, code)
            },
            appTheme = appTheme,
            onAppThemeChange = onAppThemeChange,
            onDismiss = { showSettings = false }
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            onSignatureCaptured = { bitmap ->
                // Save signature as annotation
                val tempFile = File(context.cacheDir, "signature_${System.currentTimeMillis()}.png")
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                val pos = signaturePendingPosition ?: Pair(0.2f, 0.4f)
                viewModel.addAnnotation(
                    Annotation.Signature(
                        pageIndex = currentPage,
                        x = pos.first,
                        y = pos.second,
                        width = 0.3f,
                        height = 0.15f,
                        bitmapData = tempFile.readBytes()
                    )
                )
                showSignatureDialog = false
                signaturePendingPosition = null
            },
            onDismiss = {
                showSignatureDialog = false
                signaturePendingPosition = null
            }
        )
    }

    if (showImageOrSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showImageOrSignatureDialog = false },
            title = { Text("Bild einfügen") },
            text = { Text("Möchten Sie ein Bild aus der Galerie auswählen oder eine Unterschrift einfügen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImageOrSignatureDialog = false
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Text("Bild aus Galerie")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImageOrSignatureDialog = false
                        showSignatureDialog = true
                    }
                ) {
                    Text("Unterschrift")
                }
            }
        )
    }

    if (showBugReportDialog) {
        BugReportDialog(
            onSubmit = { description ->
                coroutineScope.launch {
                    isSubmittingBugReport = true
                    val result = GitHubApi.submitBugReport(description)
                    bugReportResult = result
                    isSubmittingBugReport = false
                }
                showBugReportDialog = false
            },
            onDismiss = { showBugReportDialog = false }
        )
    }

    bugReportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { bugReportResult = null },
            title = { Text(if (result.isSuccess) "Fehlerbericht gesendet" else "Fehler beim Senden") },
            text = {
                val msg = if (result.isSuccess) {
                    "Der Fehlerbericht wurde erfolgreich übermittelt. Vielen Dank für deine Mithilfe zur Verbesserung der App!"
                } else {
                    val ex = result.exceptionOrNull()
                    val errorDetail = ex?.message ?: "Unbekannter Fehler"
                    if (errorDetail.contains("Unable to resolve host", ignoreCase = true) || 
                        errorDetail.contains("timeout", ignoreCase = true) || 
                        errorDetail.contains("Connect", ignoreCase = true)) {
                        "Der Fehlerbericht konnte nicht gesendet werden. Dies liegt wahrscheinlich an einer fehlenden oder instabilen Internetverbindung.\n\nBitte überprüfe deine Netzwerkverbindung und versuche es erneut."
                    } else {
                        "Der Fehlerbericht konnte nicht gesendet werden.\n\nFehlerdetails: $errorDetail\n\nBitte versuche es später noch einmal oder wende dich an den Support."
                    }
                }
                Text(msg)
            },
            confirmButton = {
                TextButton(onClick = { bugReportResult = null }) {
                    Text("OK")
                }
            }
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
