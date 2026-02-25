package com.annotatio.maximus.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.annotatio.maximus.model.Annotation
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.util.PdfAnnotationSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfViewModel : ViewModel() {

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

    // Annotations: pageIndex -> list of annotations
    private val _annotations = MutableStateFlow<Map<Int, List<Annotation>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<Annotation>>> = _annotations.asStateFlow()

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

    fun clearSaveError() { _saveError.value = null }

    fun openPdf(uri: Uri) {
        _pdfUri.value = uri
        _annotations.value = emptyMap()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoState()
    }

    fun openSamplePdf(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val assetManager = context.assets
            val file = File(context.cacheDir, "sample.pdf")
            assetManager.open("sample.pdf").use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val uri = Uri.fromFile(file)
            launch(Dispatchers.Main) {
                openPdf(uri)
            }
        }
    }

    fun onPageChanged(page: Int, count: Int) {
        _currentPage.value = page
        _pageCount.value = count
    }

    fun selectTool(tool: AnnotationType?) {
        _activeTool.value = tool
    }

    fun setColor(color: Color) {
        _penColor.value = color
    }

    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }

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

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is UndoAction.Add -> {
                removeAnnotationInternal(action.annotation.id)
                redoStack.add(action)
            }
            is UndoAction.Remove -> {
                addAnnotationInternal(action.annotation)
                redoStack.add(action)
            }
            is UndoAction.Update -> {
                updateAnnotationInternal(action.old)
                redoStack.add(action)
            }
        }
        updateUndoRedoState()
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is UndoAction.Add -> {
                addAnnotationInternal(action.annotation)
                undoStack.add(action)
            }
            is UndoAction.Remove -> {
                removeAnnotationInternal(action.annotation.id)
                undoStack.add(action)
            }
            is UndoAction.Update -> {
                updateAnnotationInternal(action.new)
                undoStack.add(action)
            }
        }
        updateUndoRedoState()
    }

    fun saveAnnotatedPdf(context: Context, outputUri: Uri) {
        val sourceUri = _pdfUri.value ?: return
        _saveError.value = null
        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PdfAnnotationSaver.save(context, sourceUri, outputUri, _annotations.value)
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Speichern fehlgeschlagen"
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    _isSaving.value = false
                }
            }
        }
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
    }
}
