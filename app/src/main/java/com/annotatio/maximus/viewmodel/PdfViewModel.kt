package com.annotatio.maximus.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.model.DrawingScale
import com.annotatio.maximus.model.MeasurementItem
import com.annotatio.maximus.model.PageTextContent
import com.annotatio.maximus.model.PathPoint
import com.annotatio.maximus.ui.components.Bookmark
import com.annotatio.maximus.ui.components.saveBookmarks
import com.annotatio.maximus.util.PdfAnnotationSaver
import com.annotatio.maximus.util.PdfTextExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private val DEFAULT_HIGHLIGHTER_COLOR = Color(0xFFFFEB3B)
        private val HIGHLIGHTER_COLORS = setOf(
            Color(0xFFFFEB3B),
            Color(0xFF76FF03),
            Color(0xFF40C4FF),
            Color(0xFFFF80AB),
            Color(0xFFE040FB)
        )
    }

    // PDF state
    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageCount = MutableStateFlow(0)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    // Tool state
    private val _activeTool = MutableStateFlow<AnnotationType?>(null)
    val activeTool: StateFlow<AnnotationType?> = _activeTool.asStateFlow()

    private val _penColor = MutableStateFlow(Color.Black)
    val penColor: StateFlow<Color> = _penColor.asStateFlow()

    private val _strokeWidth = MutableStateFlow(4f)
    val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

    private var savedPenColor: Color = Color.Black
    private var savedHighlighterColor: Color = DEFAULT_HIGHLIGHTER_COLOR

    // AEC & Measurement state
    private val _drawingScale = MutableStateFlow(DrawingScale())
    val drawingScale: StateFlow<DrawingScale> = _drawingScale.asStateFlow()

    private val _takeoffItems = MutableStateFlow<List<MeasurementItem>>(emptyList())
    val takeoffItems: StateFlow<List<MeasurementItem>> = _takeoffItems.asStateFlow()

    private val _lastMeasuredPixelLength = MutableStateFlow(100f)
    val lastMeasuredPixelLength: StateFlow<Float> = _lastMeasuredPixelLength.asStateFlow()

    init {
        val prefs = getApplication<Application>().getSharedPreferences("pdf_settings", Context.MODE_PRIVATE)

        // Active Tool
        val toolName = prefs.getString("active_tool", null)
        _activeTool.value = toolName?.let {
            try { AnnotationType.valueOf(it) } catch (_: Exception) { null }
        }

        // Pen Color
        val colorArgb = prefs.getInt("pen_color", android.graphics.Color.BLACK)
        val loadedColor = Color(colorArgb)
        if (loadedColor in HIGHLIGHTER_COLORS) {
            savedHighlighterColor = loadedColor
        } else {
            savedPenColor = loadedColor
        }
        _penColor.value = if (_activeTool.value == AnnotationType.HIGHLIGHTER && isDarkColor(loadedColor)) {
            savedHighlighterColor
        } else {
            loadedColor
        }

        // Stroke Width
        val width = prefs.getFloat("stroke_width", 4f)
        _strokeWidth.value = width
    }

    // Annotations: pageIndex -> list of annotations
    private val _annotations = MutableStateFlow<Map<Int, List<Annotation>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<Annotation>>> = _annotations.asStateFlow()

    // Bookmarks
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    // Page text content for smart underline/strikethrough
    private val _pageTextContents = MutableStateFlow<Map<Int, PageTextContent>>(emptyMap())
    val pageTextContents: StateFlow<Map<Int, PageTextContent>> = _pageTextContents.asStateFlow()

    // Undo / Redo
    private val undoStack = mutableListOf<UndoAction>()
    private val redoStack = mutableListOf<UndoAction>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Save state
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun clearSaveError() { _saveError.value = null }
    fun clearSaveSuccess() { _saveSuccess.value = false }

    private var textExtractionJob: Job? = null
    private val extractingPages = ConcurrentHashMap.newKeySet<Int>()

    private fun isDarkColor(color: Color): Boolean {
        val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
        return luminance < 0.35f
    }

    fun openPdf(uri: Uri, context: Context) {
        textExtractionJob?.cancel()
        extractingPages.clear()
        _pdfUri.value = uri
        _currentPage.value = 0
        _pageCount.value = 0
        _annotations.value = emptyMap()
        _pageTextContents.value = emptyMap()
        _takeoffItems.value = emptyList()
        _lastMeasuredPixelLength.value = 100f
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()
        loadBookmarks(context, uri.toString())

        // Load text content for current page lazily
        loadPageTextContent(context, 0)
    }

    fun openSamplePdf(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val file = File(context.cacheDir, "sample.pdf")
                assetManager.open("sample.pdf").use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                val uri = Uri.fromFile(file)
                withContext(Dispatchers.Main) {
                    openPdf(uri, context)
                }
            } catch (_: Exception) {
                // Ignore if sample.pdf cannot be read
            }
        }
    }

    fun onPageChanged(page: Int, count: Int, context: Context? = null) {
        _currentPage.value = page
        _pageCount.value = count
        context?.let { loadPageTextContent(it, page) }
    }

    fun navigateToPage(page: Int) {
        val maxPage = (_pageCount.value - 1).coerceAtLeast(0)
        _currentPage.value = page.coerceIn(0, maxPage)
    }

    /**
     * Load text content for a specific page lazily
     */
    fun loadPageTextContent(context: Context, pageIndex: Int) {
        val uri = _pdfUri.value ?: return
        if (_pageTextContents.value.containsKey(pageIndex)) return
        if (!extractingPages.add(pageIndex)) return

        textExtractionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = PdfTextExtractor.extractTextFromPage(context, uri, pageIndex)
                if (content != null && _pdfUri.value == uri) {
                    withContext(Dispatchers.Main) {
                        val updated = _pageTextContents.value.toMutableMap()
                        updated[pageIndex] = content
                        _pageTextContents.value = updated
                    }
                }
            } finally {
                extractingPages.remove(pageIndex)
            }
        }
    }

    /**
     * Get text content for a specific page
     */
    fun getPageTextContent(pageIndex: Int): PageTextContent? {
        return _pageTextContents.value[pageIndex]
    }

    //region Bookmarks
    private fun loadBookmarks(context: Context, pdfUriString: String) {
        _bookmarks.value = com.annotatio.maximus.ui.components.loadBookmarks(context, pdfUriString)
    }

    fun addBookmark(context: Context, bookmark: Bookmark) {
        val updatedBookmarks = (_bookmarks.value + bookmark).sortedBy { it.pageIndex }
        _bookmarks.value = updatedBookmarks
        _pdfUri.value?.toString()?.let {
            saveBookmarks(context, it, updatedBookmarks)
        }
    }

    fun removeBookmark(context: Context, bookmark: Bookmark) {
        val updatedBookmarks = _bookmarks.value.filter { it != bookmark }
        _bookmarks.value = updatedBookmarks
        _pdfUri.value?.toString()?.let {
            saveBookmarks(context, it, updatedBookmarks)
        }
    }

    fun renameBookmark(context: Context, bookmark: Bookmark, newLabel: String) {
        val updatedBookmarks = _bookmarks.value.map {
            if (it == bookmark) it.copy(label = newLabel) else it
        }
        _bookmarks.value = updatedBookmarks
        _pdfUri.value?.toString()?.let {
            saveBookmarks(context, it, updatedBookmarks)
        }
    }
    //endregion

    fun selectTool(tool: AnnotationType?) {
        val previousTool = _activeTool.value
        _activeTool.value = tool

        if (tool == AnnotationType.HIGHLIGHTER && previousTool != AnnotationType.HIGHLIGHTER) {
            if (_penColor.value !in HIGHLIGHTER_COLORS) {
                savedPenColor = _penColor.value
            }
            if (_penColor.value == Color.Black || isDarkColor(_penColor.value) || _penColor.value !in HIGHLIGHTER_COLORS) {
                _penColor.value = savedHighlighterColor
            }
        } else if (previousTool == AnnotationType.HIGHLIGHTER && tool != AnnotationType.HIGHLIGHTER) {
            if (_penColor.value in HIGHLIGHTER_COLORS) {
                savedHighlighterColor = _penColor.value
                _penColor.value = savedPenColor
            }
        }

        val prefs = getApplication<Application>().getSharedPreferences("pdf_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("active_tool", tool?.name)
            .putInt("pen_color", _penColor.value.toArgb())
            .apply()
    }

    fun setColor(color: Color) {
        _penColor.value = color
        if (_activeTool.value == AnnotationType.HIGHLIGHTER) {
            savedHighlighterColor = color
        } else {
            savedPenColor = color
        }
        val prefs = getApplication<Application>().getSharedPreferences("pdf_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("pen_color", color.toArgb()).apply()
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
        val prefs = getApplication<Application>().getSharedPreferences("pdf_settings", Context.MODE_PRIVATE)
        prefs.edit().putFloat("stroke_width", width).apply()
    }

    //region AEC Measurements
    fun setDrawingScale(scale: DrawingScale) {
        _drawingScale.value = scale
    }

    fun setLastMeasuredPixelLength(length: Float) {
        if (length > 0f) {
            _lastMeasuredPixelLength.value = length
        }
    }

    fun addTakeoffItem(item: MeasurementItem) {
        _takeoffItems.value = _takeoffItems.value + item
        undoStack.add(UndoAction.AddMeasurement(item))
        redoStack.clear()
        updateUndoRedoState()
    }

    fun removeTakeoffItem(id: String) {
        val target = _takeoffItems.value.find { it.id == id } ?: return
        _takeoffItems.value = _takeoffItems.value.filterNot { it.id == id }
        undoStack.add(UndoAction.RemoveMeasurement(target))
        redoStack.clear()
        updateUndoRedoState()
    }

    fun clearTakeoffItems() {
        val existing = _takeoffItems.value
        if (existing.isEmpty()) return
        _takeoffItems.value = emptyList()
        undoStack.add(UndoAction.ClearMeasurements(existing))
        redoStack.clear()
        updateUndoRedoState()
    }
    //endregion

    fun addAnnotation(annotation: Annotation) {
        val page = annotation.pageIndex
        val current = _annotations.value.toMutableMap()
        val pageAnnotations = current.getOrDefault(page, emptyList()) + annotation
        current[page] = pageAnnotations
        _annotations.value = current

        undoStack.add(UndoAction.Add(annotation))
        redoStack.clear()
        updateUndoRedoState()
    }

    fun removeAnnotation(annotationId: String) {
        val current = _annotations.value.toMutableMap()
        for ((page, list) in current) {
            val target = list.find { it.id == annotationId }
            if (target != null) {
                current[page] = list.filter { it.id != annotationId }
                _annotations.value = current
                undoStack.add(UndoAction.Remove(target))
                redoStack.clear()
                updateUndoRedoState()
                return
            }
        }
    }

    fun removeAnnotations(ids: List<String>) {
        val idSet = ids.toSet()
        if (idSet.isEmpty()) return

        val current = _annotations.value.toMutableMap()
        val removedActions = mutableListOf<UndoAction>()

        for ((page, list) in current.toMap()) {
            val matched = list.filter { it.id in idSet }
            if (matched.isNotEmpty()) {
                val remaining = list.filterNot { it.id in idSet }
                if (remaining.isEmpty()) {
                    current.remove(page)
                } else {
                    current[page] = remaining
                }
                matched.forEach { removedActions.add(UndoAction.Remove(it)) }
            }
        }

        if (removedActions.isNotEmpty()) {
            _annotations.value = current
            undoStack.add(if (removedActions.size == 1) removedActions.first() else UndoAction.Batch(removedActions))
            redoStack.clear()
            updateUndoRedoState()
        }
    }

    fun updateAnnotation(updated: Annotation) {
        val current = _annotations.value.toMutableMap()
        val page = updated.pageIndex
        val list = current[page] ?: return
        val index = list.indexOfFirst { it.id == updated.id }
        if (index == -1) return

        val old = list[index]
        val newList = list.toMutableList().also { it[index] = updated }
        current[page] = newList
        _annotations.value = current

        undoStack.add(UndoAction.Update(old = old, new = updated))
        redoStack.clear()
        updateUndoRedoState()
    }

    fun updateAnnotations(updatedList: List<Annotation>) {
        if (updatedList.isEmpty()) return
        val current = _annotations.value.toMutableMap()
        val actions = mutableListOf<UndoAction>()

        for (updated in updatedList) {
            val page = updated.pageIndex
            val list = current[page] ?: continue
            val index = list.indexOfFirst { it.id == updated.id }
            if (index == -1) continue

            val old = list[index]
            if (old != updated) {
                val newList = list.toMutableList().also { it[index] = updated }
                current[page] = newList
                actions.add(UndoAction.Update(old = old, new = updated))
            }
        }

        if (actions.isNotEmpty()) {
            _annotations.value = current
            undoStack.add(if (actions.size == 1) actions.first() else UndoAction.Batch(actions))
            redoStack.clear()
            updateUndoRedoState()
        }
    }

    fun duplicateAnnotations(originals: List<Annotation>): List<Annotation> {
        if (originals.isEmpty()) return emptyList()
        val current = _annotations.value.toMutableMap()
        val copies = mutableListOf<Annotation>()
        val actions = mutableListOf<UndoAction>()

        for (orig in originals) {
            val copy = duplicateWithOffset(orig)
            val page = copy.pageIndex
            current[page] = current.getOrDefault(page, emptyList()) + copy
            copies.add(copy)
            actions.add(UndoAction.Add(copy))
        }

        _annotations.value = current
        undoStack.add(if (actions.size == 1) actions.first() else UndoAction.Batch(actions))
        redoStack.clear()
        updateUndoRedoState()
        return copies
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        applyUndo(action)
        redoStack.add(action)
        updateUndoRedoState()
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        applyRedo(action)
        undoStack.add(action)
        updateUndoRedoState()
    }

    private fun applyUndo(action: UndoAction) {
        when (action) {
            is UndoAction.Add -> removeAnnotationInternal(action.annotation.id)
            is UndoAction.Remove -> addAnnotationInternal(action.annotation)
            is UndoAction.Update -> updateAnnotationInternal(action.old)
            is UndoAction.AddMeasurement -> {
                _takeoffItems.value = _takeoffItems.value.filterNot { it.id == action.item.id }
            }
            is UndoAction.RemoveMeasurement -> {
                _takeoffItems.value = _takeoffItems.value + action.item
            }
            is UndoAction.ClearMeasurements -> {
                _takeoffItems.value = action.items
            }
            is UndoAction.Batch -> {
                for (sub in action.actions.asReversed()) {
                    applyUndo(sub)
                }
            }
        }
    }

    private fun applyRedo(action: UndoAction) {
        when (action) {
            is UndoAction.Add -> addAnnotationInternal(action.annotation)
            is UndoAction.Remove -> removeAnnotationInternal(action.annotation.id)
            is UndoAction.Update -> updateAnnotationInternal(action.new)
            is UndoAction.AddMeasurement -> {
                _takeoffItems.value = _takeoffItems.value + action.item
            }
            is UndoAction.RemoveMeasurement -> {
                _takeoffItems.value = _takeoffItems.value.filterNot { it.id == action.item.id }
            }
            is UndoAction.ClearMeasurements -> {
                _takeoffItems.value = emptyList()
            }
            is UndoAction.Batch -> {
                for (sub in action.actions) {
                    applyRedo(sub)
                }
            }
        }
    }

    fun saveAnnotatedPdf(context: Context, outputUri: Uri) {
        val sourceUri = _pdfUri.value ?: return
        _saveError.value = null
        _saveSuccess.value = false
        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PdfAnnotationSaver.save(
                    context = context,
                    sourceUri = sourceUri,
                    outputUri = outputUri,
                    annotations = _annotations.value,
                    takeoffItems = _takeoffItems.value
                )
                withContext(Dispatchers.Main.immediate) {
                    _saveSuccess.value = true
                }
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Speichern fehlgeschlagen"
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    _isSaving.value = false
                }
            }
        }
    }

    fun deletePages(
        context: Context,
        pageIndicesToDelete: Set<Int>,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val sourceUri = _pdfUri.value ?: run {
            onResult(false, "Kein Dokument geöffnet.")
            return
        }
        if (pageIndicesToDelete.isEmpty()) {
            onResult(false, "Keine Seiten zum Löschen ausgewählt.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val outFile = File(context.cacheDir, "modified_${System.currentTimeMillis()}.pdf")
                val oldToNewIndex = mutableMapOf<Int, Int>()
                var newPageCount = 0

                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        val totalPages = document.numberOfPages
                        val validDeletes = pageIndicesToDelete.filter { it in 0 until totalPages }.toSet()
                        val remainingOldIndices = (0 until totalPages).filter { it !in validDeletes }
                        if (remainingOldIndices.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                onResult(false, "Mindestens eine Seite muss erhalten bleiben.")
                            }
                            return@launch
                        }

                        remainingOldIndices.forEachIndexed { newIdx, oldIdx ->
                            oldToNewIndex[oldIdx] = newIdx
                        }

                        for (idx in validDeletes.sortedDescending()) {
                            document.removePage(idx)
                        }
                        newPageCount = document.numberOfPages
                        FileOutputStream(outFile).use { fos ->
                            document.save(fos)
                        }
                    }
                } ?: throw IllegalStateException("PDF konnte nicht geöffnet werden.")

                val newUri = Uri.fromFile(outFile)
                withContext(Dispatchers.Main) {
                    applyPageRemapping(context, newUri, newPageCount, oldToNewIndex)
                    onResult(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Seiten konnten nicht gelöscht werden.")
                }
            }
        }
    }

    fun reorderPages(
        context: Context,
        newPageOrder: List<Int>,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val sourceUri = _pdfUri.value ?: run {
            onResult(false, "Kein Dokument geöffnet.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val outFile = File(context.cacheDir, "modified_${System.currentTimeMillis()}.pdf")
                val oldToNewIndex = mutableMapOf<Int, Int>()
                var newPageCount = 0

                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        val totalPages = document.numberOfPages
                        if (newPageOrder.size != totalPages || newPageOrder.toSet() != (0 until totalPages).toSet()) {
                            withContext(Dispatchers.Main) {
                                onResult(false, "Ungültige Seitenreihenfolge.")
                            }
                            return@launch
                        }

                        newPageOrder.forEachIndexed { newIdx, oldIdx ->
                            oldToNewIndex[oldIdx] = newIdx
                        }

                        val orderedPages = newPageOrder.map { document.getPage(it) }
                        while (document.numberOfPages > 0) {
                            document.removePage(0)
                        }
                        orderedPages.forEach { page ->
                            document.addPage(page)
                        }

                        newPageCount = document.numberOfPages
                        FileOutputStream(outFile).use { fos ->
                            document.save(fos)
                        }
                    }
                } ?: throw IllegalStateException("PDF konnte nicht geöffnet werden.")

                val newUri = Uri.fromFile(outFile)
                withContext(Dispatchers.Main) {
                    applyPageRemapping(context, newUri, newPageCount, oldToNewIndex)
                    onResult(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Seiten konnten nicht umgeordnet werden.")
                }
            }
        }
    }

    private fun applyPageRemapping(
        context: Context,
        newUri: Uri,
        newPageCount: Int,
        oldToNewIndex: Map<Int, Int>
    ) {
        textExtractionJob?.cancel()
        extractingPages.clear()

        val remappedAnnotations = mutableMapOf<Int, List<Annotation>>()
        for ((oldPage, list) in _annotations.value) {
            val newPage = oldToNewIndex[oldPage] ?: continue
            remappedAnnotations[newPage] = list.map { it.withPageIndex(newPage) }
        }
        _annotations.value = remappedAnnotations

        val remappedBookmarks = _bookmarks.value.mapNotNull { bm ->
            val newPage = oldToNewIndex[bm.pageIndex] ?: return@mapNotNull null
            bm.copy(pageIndex = newPage)
        }.sortedBy { it.pageIndex }
        _bookmarks.value = remappedBookmarks
        saveBookmarks(context, newUri.toString(), remappedBookmarks)

        val remappedTexts = mutableMapOf<Int, PageTextContent>()
        for ((oldPage, content) in _pageTextContents.value) {
            val newPage = oldToNewIndex[oldPage] ?: continue
            remappedTexts[newPage] = content.copy(pageIndex = newPage)
        }
        _pageTextContents.value = remappedTexts

        _takeoffItems.value = _takeoffItems.value.mapNotNull { item ->
            val newPage = oldToNewIndex[item.pageIndex] ?: return@mapNotNull null
            item.copy(pageIndex = newPage)
        }

        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()

        _pdfUri.value = newUri
        _pageCount.value = newPageCount
        val safePage = _currentPage.value.coerceIn(0, (newPageCount - 1).coerceAtLeast(0))
        _currentPage.value = safePage
        loadPageTextContent(context, safePage)
    }

    private fun duplicateWithOffset(annotation: Annotation): Annotation {
        val delta = 0.025f
        val newId = UUID.randomUUID().toString()
        return when (annotation) {
            is Annotation.Stroke -> annotation.copy(
                id = newId,
                path = annotation.path.copy(
                    points = annotation.path.points.map { pt ->
                        PathPoint(
                            x = (pt.x + delta).coerceIn(0f, 1f),
                            y = (pt.y + delta).coerceIn(0f, 1f)
                        )
                    }
                )
            )
            is Annotation.HighlightRect -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.TextNote -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Shape -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Underline -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Strikethrough -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Table -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Image -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Link -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Comment -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.Signature -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.SmartGraphic -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
            is Annotation.TextBox -> annotation.copy(
                id = newId,
                x = (annotation.x + delta).coerceIn(0f, 0.95f),
                y = (annotation.y + delta).coerceIn(0f, 0.95f)
            )
        }
    }

    private fun Annotation.withPageIndex(newPageIndex: Int): Annotation = when (this) {
        is Annotation.Stroke -> copy(pageIndex = newPageIndex)
        is Annotation.HighlightRect -> copy(pageIndex = newPageIndex)
        is Annotation.TextNote -> copy(pageIndex = newPageIndex)
        is Annotation.Shape -> copy(pageIndex = newPageIndex)
        is Annotation.Underline -> copy(pageIndex = newPageIndex)
        is Annotation.Strikethrough -> copy(pageIndex = newPageIndex)
        is Annotation.Table -> copy(pageIndex = newPageIndex)
        is Annotation.Image -> copy(pageIndex = newPageIndex)
        is Annotation.Link -> copy(pageIndex = newPageIndex)
        is Annotation.Comment -> copy(pageIndex = newPageIndex)
        is Annotation.Signature -> copy(pageIndex = newPageIndex)
        is Annotation.SmartGraphic -> copy(pageIndex = newPageIndex)
        is Annotation.TextBox -> copy(pageIndex = newPageIndex)
    }

    private fun addAnnotationInternal(annotation: Annotation) {
        val page = annotation.pageIndex
        val current = _annotations.value.toMutableMap()
        val pageAnnotations = current.getOrDefault(page, emptyList()) + annotation
        current[page] = pageAnnotations
        _annotations.value = current
    }

    private fun removeAnnotationInternal(annotationId: String) {
        val current = _annotations.value.toMutableMap()
        for ((page, list) in current) {
            if (list.any { it.id == annotationId }) {
                current[page] = list.filter { it.id != annotationId }
                _annotations.value = current
                return
            }
        }
    }

    private fun updateAnnotationInternal(annotation: Annotation) {
        val current = _annotations.value.toMutableMap()
        val page = annotation.pageIndex
        val list = current[page] ?: return
        val index = list.indexOfFirst { it.id == annotation.id }
        if (index == -1) return
        val newList = list.toMutableList().also { it[index] = annotation }
        current[page] = newList
        _annotations.value = current
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private sealed class UndoAction {
        data class Add(val annotation: Annotation) : UndoAction()
        data class Remove(val annotation: Annotation) : UndoAction()
        data class Update(val old: Annotation, val new: Annotation) : UndoAction()
        data class AddMeasurement(val item: MeasurementItem) : UndoAction()
        data class RemoveMeasurement(val item: MeasurementItem) : UndoAction()
        data class ClearMeasurements(val items: List<MeasurementItem>) : UndoAction()
        data class Batch(val actions: List<UndoAction>) : UndoAction()
    }
}
