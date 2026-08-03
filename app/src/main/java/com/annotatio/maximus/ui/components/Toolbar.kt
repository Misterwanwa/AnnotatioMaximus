@file:OptIn(ExperimentalMaterial3Api::class)

package com.annotatio.maximus.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.AnnotationType
import com.annotatio.maximus.ui.theme.AeroPrimary
import com.annotatio.maximus.ui.theme.AeroPrimaryContainer
import com.annotatio.maximus.ui.theme.AeroSilver
import com.annotatio.maximus.ui.theme.AeroSurface
import com.annotatio.maximus.ui.theme.AppTheme
import com.annotatio.maximus.ui.theme.LocalAppTheme
import com.annotatio.maximus.ui.theme.SlateDeep
import com.annotatio.maximus.ui.theme.SlateEdge

// ──────────────────────────────────────────────────────────────────────────────
// Public entry point — dispatches to the correct themed toolbar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun AnnotationToolbar(
    activeTool: AnnotationType?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasDocument: Boolean,
    toolbarVisibility: Map<String, Boolean>,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToolSelected: (AnnotationType?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpenGeminiSketch: () -> Unit,
    onOpenConverter: () -> Unit = {},
    onOpenTranslator: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenTts: () -> Unit = {},
    onOpenWifiShare: () -> Unit = {},
    onOpenCloud: () -> Unit = {},
    onOpenCreatePdf: () -> Unit = {},
    onOpenPassword: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onSmartGraphicSelected: (com.annotatio.maximus.model.SmartGraphicType) -> Unit = {},
    onToolbarTextBoxSelected: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (LocalAppTheme.current) {
        AppTheme.CUPERTINO_MINIMALIST -> CupertinoToolbar(
            activeTool, canUndo, canRedo, hasDocument, toolbarVisibility,
            onOpenFile, onSettingsClick, onBugReportClick, onBookmarksClick,
            onToolSelected, onUndo, onRedo, onSave, onOpenGeminiSketch,
            onOpenConverter, onOpenTranslator, onOpenScanner, onOpenTts,
            onOpenWifiShare, onOpenCloud, onOpenCreatePdf, onOpenPassword,
            onOpenSearch, onSmartGraphicSelected, onToolbarTextBoxSelected, onOpenLink, modifier
        )
        AppTheme.FRUTIGER_AERO -> AeroToolbar(
            activeTool, canUndo, canRedo, hasDocument, toolbarVisibility,
            onOpenFile, onSettingsClick, onBugReportClick, onBookmarksClick,
            onToolSelected, onUndo, onRedo, onSave, onOpenGeminiSketch,
            onOpenConverter, onOpenTranslator, onOpenScanner, onOpenTts,
            onOpenWifiShare, onOpenCloud, onOpenCreatePdf, onOpenPassword,
            onOpenSearch, onSmartGraphicSelected, onToolbarTextBoxSelected, onOpenLink, modifier
        )
        AppTheme.LONGHORN_SLATE -> SlateToolbar(
            activeTool, canUndo, canRedo, hasDocument, toolbarVisibility,
            onOpenFile, onSettingsClick, onBugReportClick, onBookmarksClick,
            onToolSelected, onUndo, onRedo, onSave, onOpenGeminiSketch,
            onOpenConverter, onOpenTranslator, onOpenScanner, onOpenTts,
            onOpenWifiShare, onOpenCloud, onOpenCreatePdf, onOpenPassword,
            onOpenSearch, onSmartGraphicSelected, onToolbarTextBoxSelected, onOpenLink, modifier
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Cupertino Minimalist — floating tab bar (portrait = bottom, landscape = top)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun CupertinoToolbar(
    activeTool: AnnotationType?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasDocument: Boolean,
    toolbarVisibility: Map<String, Boolean>,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToolSelected: (AnnotationType?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpenGeminiSketch: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenWifiShare: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenCreatePdf: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenSearch: () -> Unit,
    onSmartGraphicSelected: (com.annotatio.maximus.model.SmartGraphicType) -> Unit,
    onToolbarTextBoxSelected: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Frosted-glass-style background (semi-transparent white)
    val frostedBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(frostedBg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val show = { id: String -> toolbarVisibility[id] != false }

        CupertinoIconBtn(Icons.Default.FolderOpen, "PDF öffnen", onClick = onOpenFile)
        if (show("bookmarks"))
            CupertinoIconBtn(Icons.Default.Bookmark, "Lesezeichen", enabled = hasDocument, onClick = onBookmarksClick)
        CupertinoIconBtn(Icons.Default.Settings, "Einstellungen", onClick = onSettingsClick)

        CupertinoSeparator()

        if (show("hand"))
            CupertinoToolToggle(Icons.Default.PanTool, "Hand-Tool", activeTool == AnnotationType.HAND, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.HAND) null else AnnotationType.HAND)
            }
        if (show("pen"))
            CupertinoToolToggle(Icons.Default.Brush, "Pinsel", activeTool == AnnotationType.PEN, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.PEN) null else AnnotationType.PEN)
            }
        if (show("marker"))
            CupertinoToolToggle(Icons.Default.BorderColor, "Marker", activeTool == AnnotationType.HIGHLIGHTER, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.HIGHLIGHTER) null else AnnotationType.HIGHLIGHTER)
            }
        if (show("underline"))
            CupertinoToolToggle(Icons.Default.FormatUnderlined, "Unterstreichen", activeTool == AnnotationType.UNDERLINE, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.UNDERLINE) null else AnnotationType.UNDERLINE)
            }
        if (show("strikethrough"))
            CupertinoToolToggle(Icons.Default.FormatStrikethrough, "Durchstreichen", activeTool == AnnotationType.STRIKETHROUGH, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.STRIKETHROUGH) null else AnnotationType.STRIKETHROUGH)
            }

        if (show("textbox"))
            CupertinoToolToggle(Icons.Default.TextFields, "Schriftgröße ändern", activeTool == AnnotationType.TEXT_BOX, hasDocument) {
                if (activeTool != AnnotationType.TEXT_BOX) onToolbarTextBoxSelected()
                onToolSelected(if (activeTool == AnnotationType.TEXT_BOX) null else AnnotationType.TEXT_BOX)
            }
        if (show("comment"))
            CupertinoToolToggle(Icons.Default.ModeComment, "Kommentar", activeTool == AnnotationType.COMMENT, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.COMMENT) null else AnnotationType.COMMENT)
            }
        if (show("eraser"))
            CupertinoToolToggle(Icons.Default.CleaningServices, "Radierer", activeTool == AnnotationType.ERASER, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.ERASER) null else AnnotationType.ERASER)
            }
        if (show("shapes")) {
            var showShapeMenu by remember { mutableStateOf(false) }
            Box {
                CupertinoToolToggle(Icons.Outlined.Interests, "Formen",
                    activeTool in listOf(AnnotationType.CIRCLE, AnnotationType.SQUARE, AnnotationType.RECTANGLE, AnnotationType.TRIANGLE),
                    hasDocument) { showShapeMenu = true }
                DropdownMenu(expanded = showShapeMenu, onDismissRequest = { showShapeMenu = false }) {
                    listOf(AnnotationType.CIRCLE to "Kreis", AnnotationType.SQUARE to "Quadrat",
                        AnnotationType.RECTANGLE to "Rechteck", AnnotationType.TRIANGLE to "Dreieck"
                    ).forEach { (type, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            showShapeMenu = false
                            onToolSelected(if (activeTool == type) null else type)
                        })
                    }
                }
            }
        }
        if (show("signature"))
            CupertinoToolToggle(Icons.Default.Gesture, "Unterschrift", activeTool == AnnotationType.SIGNATURE, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.SIGNATURE) null else AnnotationType.SIGNATURE)
            }
        if (show("image"))
            CupertinoToolToggle(Icons.Default.AddPhotoAlternate, "Bild", activeTool == AnnotationType.IMAGE, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.IMAGE) null else AnnotationType.IMAGE)
            }
        if (show("link"))
            CupertinoIconBtn(Icons.Default.Link, "Link", enabled = hasDocument, onClick = onOpenLink)
        if (show("select"))
            CupertinoToolToggle(Icons.Default.NearMe, "Auswählen", activeTool == AnnotationType.SELECT, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.SELECT) null else AnnotationType.SELECT)
            }
        if (show("lasso"))
            CupertinoToolToggle(Icons.Default.SelectAll, "Lasso", activeTool == AnnotationType.LASSO, hasDocument) {
                onToolSelected(if (activeTool == AnnotationType.LASSO) null else AnnotationType.LASSO)
            }
        if (show("smartgraphic")) {
            var showSmartMenu by remember { mutableStateOf(false) }
            Box {
                CupertinoToolToggle(Icons.Default.BubbleChart, "Smarte Grafik", activeTool == AnnotationType.SMART_GRAPHIC, hasDocument) { showSmartMenu = true }
                DropdownMenu(expanded = showSmartMenu, onDismissRequest = { showSmartMenu = false }) {
                    DropdownMenuItem(text = { Text("MindMap") }, onClick = {
                        showSmartMenu = false
                        onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP)
                        onToolSelected(AnnotationType.SMART_GRAPHIC)
                    })
                    DropdownMenuItem(text = { Text("Organigramm") }, onClick = {
                        showSmartMenu = false
                        onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.ORG_CHART)
                        onToolSelected(AnnotationType.SMART_GRAPHIC)
                    })
                }
            }
        }
        if (show("translator")) CupertinoIconBtn(Icons.Default.GTranslate, "Übersetzer", enabled = hasDocument, onClick = onOpenTranslator)
        if (show("converter"))  CupertinoIconBtn(Icons.Default.ImportExport, "Konvertieren", enabled = hasDocument, onClick = onOpenConverter)
        if (show("gemini"))     CupertinoIconBtn(Icons.Default.SmartToy, "KI-Skizze", onClick = onOpenGeminiSketch)
        if (show("scanner"))    CupertinoIconBtn(Icons.Default.DocumentScanner, "Scanner", onClick = onOpenScanner)
        if (show("search"))     CupertinoIconBtn(Icons.Default.Search, "Suchen", enabled = hasDocument, onClick = onOpenSearch)
        if (show("tts"))        CupertinoIconBtn(Icons.Default.RecordVoiceOver, "Vorlesen", enabled = hasDocument, onClick = onOpenTts)
        if (show("wifi"))       CupertinoIconBtn(Icons.Default.Wifi, "WLAN", enabled = hasDocument, onClick = onOpenWifiShare)
        if (show("cloud"))      CupertinoIconBtn(Icons.Default.Cloud, "Cloud", onClick = onOpenCloud)
        if (show("createpdf"))  CupertinoIconBtn(Icons.Default.NoteAdd, "PDF erstellen", onClick = onOpenCreatePdf)
        if (show("password"))   CupertinoIconBtn(Icons.Default.Lock, "Passwort", enabled = hasDocument, onClick = onOpenPassword)

        CupertinoSeparator()

        if (show("undo"))
            CupertinoIconBtn(Icons.Default.Undo, "Rückgängig", enabled = canUndo, onClick = onUndo)
        if (show("redo"))
            CupertinoIconBtn(Icons.Default.Redo, "Wiederholen", enabled = canRedo, onClick = onRedo)

        CupertinoSeparator()

        CupertinoIconBtn(Icons.Default.BugReport, "Bug melden", onClick = onBugReportClick)
        if (show("save"))
            CupertinoIconBtn(Icons.Default.Save, "Speichern", enabled = hasDocument, onClick = onSave)
    }
}

@Composable
private fun CupertinoToolToggle(
    icon: ImageVector, label: String, isActive: Boolean, enabled: Boolean, onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isActive -> MaterialTheme.colorScheme.primary
        else     -> MaterialTheme.colorScheme.onSurface
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CupertinoIconBtn(
    icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CupertinoSeparator() {
    Spacer(Modifier.width(4.dp))
    Box(
        Modifier
            .height(20.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
    )
    Spacer(Modifier.width(4.dp))
}

// ──────────────────────────────────────────────────────────────────────────────
// Frutiger Aero — skeuomorphic Windows XP Luna Blue style
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun AeroToolbar(
    activeTool: AnnotationType?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasDocument: Boolean,
    toolbarVisibility: Map<String, Boolean>,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToolSelected: (AnnotationType?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpenGeminiSketch: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenWifiShare: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenCreatePdf: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenSearch: () -> Unit,
    onSmartGraphicSelected: (com.annotatio.maximus.model.SmartGraphicType) -> Unit,
    onToolbarTextBoxSelected: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    // XP title-bar gradient: deep blue at top to mid blue
    val titleBarGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A4FAD), Color(0xFF003399), Color(0xFF2060C0))
    )
    // Toolbar body: classic silver/tan
    val toolbarGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFECE9D8), Color(0xFFD4D0C8))
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Title bar stripe
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(titleBarGradient)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "Annotatio Maximus",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
        // Tool row with XP button style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(toolbarGradient)
                .border(BorderStroke(1.dp, Color(0xFF8C8C8C)))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val show = { id: String -> toolbarVisibility[id] != false }

            AeroBtn(Icons.Default.FolderOpen, "Öffnen", onClick = onOpenFile)
            if (show("bookmarks"))
                AeroBtn(Icons.Default.Bookmark, "Lesezeichen", enabled = hasDocument, onClick = onBookmarksClick)
            AeroBtn(Icons.Default.Settings, "Einstellungen", onClick = onSettingsClick)

            AeroSeparator()

            if (show("hand"))
                AeroToggleBtn(Icons.Default.PanTool, "Hand-Tool", activeTool == AnnotationType.HAND, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.HAND) null else AnnotationType.HAND)
                }
            if (show("pen"))
                AeroToggleBtn(Icons.Default.Brush, "Pinsel", activeTool == AnnotationType.PEN, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.PEN) null else AnnotationType.PEN)
                }
            if (show("marker"))
                AeroToggleBtn(Icons.Default.BorderColor, "Marker", activeTool == AnnotationType.HIGHLIGHTER, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.HIGHLIGHTER) null else AnnotationType.HIGHLIGHTER)
                }
            if (show("underline"))
                AeroToggleBtn(Icons.Default.FormatUnderlined, "Unterstreichen", activeTool == AnnotationType.UNDERLINE, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.UNDERLINE) null else AnnotationType.UNDERLINE)
                }
            if (show("strikethrough"))
                AeroToggleBtn(Icons.Default.FormatStrikethrough, "Durchstreichen", activeTool == AnnotationType.STRIKETHROUGH, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.STRIKETHROUGH) null else AnnotationType.STRIKETHROUGH)
                }

            if (show("textbox"))
                AeroToggleBtn(Icons.Default.TextFields, "Schriftgröße ändern", activeTool == AnnotationType.TEXT_BOX, hasDocument) {
                    if (activeTool != AnnotationType.TEXT_BOX) onToolbarTextBoxSelected()
                    onToolSelected(if (activeTool == AnnotationType.TEXT_BOX) null else AnnotationType.TEXT_BOX)
                }
            if (show("comment"))
                AeroToggleBtn(Icons.Default.ModeComment, "Kommentar", activeTool == AnnotationType.COMMENT, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.COMMENT) null else AnnotationType.COMMENT)
                }
            if (show("eraser"))
                AeroToggleBtn(Icons.Default.CleaningServices, "Radierer", activeTool == AnnotationType.ERASER, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.ERASER) null else AnnotationType.ERASER)
                }
            if (show("shapes")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    AeroToggleBtn(Icons.Outlined.Interests, "Formen",
                        activeTool in listOf(AnnotationType.CIRCLE, AnnotationType.SQUARE, AnnotationType.RECTANGLE, AnnotationType.TRIANGLE),
                        hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf(AnnotationType.CIRCLE to "Kreis", AnnotationType.SQUARE to "Quadrat",
                            AnnotationType.RECTANGLE to "Rechteck", AnnotationType.TRIANGLE to "Dreieck"
                        ).forEach { (type, lbl) ->
                            DropdownMenuItem(text = { Text(lbl) }, onClick = {
                                showMenu = false; onToolSelected(if (activeTool == type) null else type)
                            })
                        }
                    }
                }
            }
            if (show("signature"))
                AeroToggleBtn(Icons.Default.Gesture, "Unterschrift", activeTool == AnnotationType.SIGNATURE, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.SIGNATURE) null else AnnotationType.SIGNATURE)
                }
            if (show("image"))
                AeroToggleBtn(Icons.Default.AddPhotoAlternate, "Bild", activeTool == AnnotationType.IMAGE, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.IMAGE) null else AnnotationType.IMAGE)
                }
            if (show("link"))     AeroBtn(Icons.Default.Link, "Link", enabled = hasDocument, onClick = onOpenLink)
            if (show("select"))
                AeroToggleBtn(Icons.Default.NearMe, "Auswählen", activeTool == AnnotationType.SELECT, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.SELECT) null else AnnotationType.SELECT)
                }
            if (show("lasso"))
                AeroToggleBtn(Icons.Default.SelectAll, "Lasso", activeTool == AnnotationType.LASSO, hasDocument) {
                    onToolSelected(if (activeTool == AnnotationType.LASSO) null else AnnotationType.LASSO)
                }
            if (show("smartgraphic")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    AeroToggleBtn(Icons.Default.BubbleChart, "Smarte Grafik", activeTool == AnnotationType.SMART_GRAPHIC, hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("MindMap") }, onClick = {
                            showMenu = false
                            onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP)
                            onToolSelected(AnnotationType.SMART_GRAPHIC)
                        })
                        DropdownMenuItem(text = { Text("Organigramm") }, onClick = {
                            showMenu = false
                            onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.ORG_CHART)
                            onToolSelected(AnnotationType.SMART_GRAPHIC)
                        })
                    }
                }
            }
            if (show("translator")) AeroBtn(Icons.Default.GTranslate, "Übersetzer", enabled = hasDocument, onClick = onOpenTranslator)
            if (show("converter"))  AeroBtn(Icons.Default.ImportExport, "Konverter", enabled = hasDocument, onClick = onOpenConverter)
            if (show("gemini"))     AeroBtn(Icons.Default.SmartToy, "KI-Skizze", onClick = onOpenGeminiSketch)
            if (show("scanner"))    AeroBtn(Icons.Default.DocumentScanner, "Scanner", onClick = onOpenScanner)
            if (show("search"))     AeroBtn(Icons.Default.Search, "Suchen", enabled = hasDocument, onClick = onOpenSearch)
            if (show("tts"))        AeroBtn(Icons.Default.RecordVoiceOver, "Vorlesen", enabled = hasDocument, onClick = onOpenTts)
            if (show("wifi"))       AeroBtn(Icons.Default.Wifi, "WLAN", enabled = hasDocument, onClick = onOpenWifiShare)
            if (show("cloud"))      AeroBtn(Icons.Default.Cloud, "Cloud", onClick = onOpenCloud)
            if (show("createpdf"))  AeroBtn(Icons.Default.NoteAdd, "PDF erstellen", onClick = onOpenCreatePdf)
            if (show("password"))   AeroBtn(Icons.Default.Lock, "Passwort", enabled = hasDocument, onClick = onOpenPassword)

            AeroSeparator()

            if (show("undo")) AeroBtn(Icons.Default.Undo, "Rückgängig", enabled = canUndo, onClick = onUndo)
            if (show("redo")) AeroBtn(Icons.Default.Redo, "Wiederholen", enabled = canRedo, onClick = onRedo)

            AeroSeparator()

            AeroBtn(Icons.Default.BugReport, "Bug melden", onClick = onBugReportClick)
            if (show("save")) AeroBtn(Icons.Default.Save, "Speichern", enabled = hasDocument, onClick = onSave)
        }
    }
}

/** Skeuomorphic XP-style raised button */
@Composable
private fun AeroBtn(
    icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit
) {
    val bg = Brush.verticalGradient(
        colors = if (enabled)
            listOf(Color(0xFFF5F4EE), Color(0xFFDDD9CA))
        else
            listOf(Color(0xFFD8D5C8), Color(0xFFC8C5BA))
    )
    val tint = if (enabled) Color(0xFF1A1A1A) else Color(0xFF888888)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(36.dp)
                .background(bg, RoundedCornerShape(3.dp))
                .border(
                    BorderStroke(1.dp, Color(0xFF888880)),
                    RoundedCornerShape(3.dp)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** XP-style pressed/active toggle button */
@Composable
private fun AeroToggleBtn(
    icon: ImageVector, label: String, isActive: Boolean, enabled: Boolean, onClick: () -> Unit
) {
    val bg = if (isActive)
        Brush.verticalGradient(listOf(Color(0xFFC0C8D8), Color(0xFF8FA8C0)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF5F4EE), Color(0xFFDDD9CA)))
    val border = if (isActive) Color(0xFF4466AA) else Color(0xFF888880)
    val tint = if (!enabled) Color(0xFF888888)
               else if (isActive) Color(0xFF003399)
               else Color(0xFF1A1A1A)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(36.dp)
                .background(bg, RoundedCornerShape(3.dp))
                .border(BorderStroke(1.dp, border), RoundedCornerShape(3.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AeroSeparator() {
    Spacer(Modifier.width(2.dp))
    Box(
        Modifier
            .height(24.dp)
            .width(1.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFFB0ADA0), Color(0xFF8C8880), Color(0xFFB0ADA0)))
            )
    )
    Spacer(Modifier.width(2.dp))
}

// ──────────────────────────────────────────────────────────────────────────────
// Longhorn Slate — dark sidebar (landscape) / dark top bar (portrait)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun SlateToolbar(
    activeTool: AnnotationType?,
    canUndo: Boolean,
    canRedo: Boolean,
    hasDocument: Boolean,
    toolbarVisibility: Map<String, Boolean>,
    onOpenFile: () -> Unit,
    onSettingsClick: () -> Unit,
    onBugReportClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onToolSelected: (AnnotationType?) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onOpenGeminiSketch: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenTts: () -> Unit,
    onOpenWifiShare: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenCreatePdf: () -> Unit,
    onOpenPassword: () -> Unit,
    onOpenSearch: () -> Unit,
    onSmartGraphicSelected: (com.annotatio.maximus.model.SmartGraphicType) -> Unit,
    onToolbarTextBoxSelected: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Gradient: dark gray → near-black
    val slateBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF2E2E2E), SlateDeep)
    )
    val show = { id: String -> toolbarVisibility[id] != false }

    if (isLandscape) {
        // Sidebar mode: this composable is rendered inside PdfViewerScreen's Column
        // We just fill vertically with same gradient
        Column(
            modifier = modifier
                .background(slateBg)
                .border(BorderStroke(1.dp, SlateEdge))
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SlateIconBtn(Icons.Default.FolderOpen, "Öffnen", onClick = onOpenFile)
            if (show("bookmarks")) SlateIconBtn(Icons.Default.Bookmark, "Lesezeichen", enabled = hasDocument, onClick = onBookmarksClick)
            SlateIconBtn(Icons.Default.Settings, "Einstellungen", onClick = onSettingsClick)
            SlateSeparatorH()
            if (show("hand"))         SlateToggle(Icons.Default.PanTool, "Hand-Tool", activeTool == AnnotationType.HAND, hasDocument) { onToolSelected(if (activeTool == AnnotationType.HAND) null else AnnotationType.HAND) }
            if (show("pen"))          SlateToggle(Icons.Default.Brush, "Pinsel", activeTool == AnnotationType.PEN, hasDocument) { onToolSelected(if (activeTool == AnnotationType.PEN) null else AnnotationType.PEN) }
            if (show("marker"))       SlateToggle(Icons.Default.BorderColor, "Marker", activeTool == AnnotationType.HIGHLIGHTER, hasDocument) { onToolSelected(if (activeTool == AnnotationType.HIGHLIGHTER) null else AnnotationType.HIGHLIGHTER) }
            if (show("underline"))    SlateToggle(Icons.Default.FormatUnderlined, "Unterstreichen", activeTool == AnnotationType.UNDERLINE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.UNDERLINE) null else AnnotationType.UNDERLINE) }
            if (show("strikethrough"))SlateToggle(Icons.Default.FormatStrikethrough, "Durchstreichen", activeTool == AnnotationType.STRIKETHROUGH, hasDocument) { onToolSelected(if (activeTool == AnnotationType.STRIKETHROUGH) null else AnnotationType.STRIKETHROUGH) }

            if (show("textbox"))      SlateToggle(Icons.Default.TextFields, "Textfeld", activeTool == AnnotationType.TEXT_BOX, hasDocument) { if (activeTool != AnnotationType.TEXT_BOX) onToolbarTextBoxSelected(); onToolSelected(if (activeTool == AnnotationType.TEXT_BOX) null else AnnotationType.TEXT_BOX) }
            if (show("comment"))      SlateToggle(Icons.Default.ModeComment, "Kommentar", activeTool == AnnotationType.COMMENT, hasDocument) { onToolSelected(if (activeTool == AnnotationType.COMMENT) null else AnnotationType.COMMENT) }
            if (show("eraser"))       SlateToggle(Icons.Default.CleaningServices, "Radierer", activeTool == AnnotationType.ERASER, hasDocument) { onToolSelected(if (activeTool == AnnotationType.ERASER) null else AnnotationType.ERASER) }
            if (show("shapes")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    SlateToggle(Icons.Outlined.Interests, "Formen",
                        activeTool in listOf(AnnotationType.CIRCLE, AnnotationType.SQUARE, AnnotationType.RECTANGLE, AnnotationType.TRIANGLE),
                        hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf(AnnotationType.CIRCLE to "Kreis", AnnotationType.SQUARE to "Quadrat",
                            AnnotationType.RECTANGLE to "Rechteck", AnnotationType.TRIANGLE to "Dreieck"
                        ).forEach { (type, lbl) ->
                            DropdownMenuItem(text = { Text(lbl) }, onClick = { showMenu = false; onToolSelected(if (activeTool == type) null else type) })
                        }
                    }
                }
            }
            if (show("signature"))    SlateToggle(Icons.Default.Gesture, "Unterschrift", activeTool == AnnotationType.SIGNATURE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.SIGNATURE) null else AnnotationType.SIGNATURE) }
            if (show("image"))        SlateToggle(Icons.Default.AddPhotoAlternate, "Bild", activeTool == AnnotationType.IMAGE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.IMAGE) null else AnnotationType.IMAGE) }
            if (show("link"))         SlateIconBtn(Icons.Default.Link, "Link", enabled = hasDocument, onClick = onOpenLink)
            if (show("select"))       SlateToggle(Icons.Default.NearMe, "Auswählen", activeTool == AnnotationType.SELECT, hasDocument) { onToolSelected(if (activeTool == AnnotationType.SELECT) null else AnnotationType.SELECT) }
            if (show("lasso"))        SlateToggle(Icons.Default.SelectAll, "Lasso", activeTool == AnnotationType.LASSO, hasDocument) { onToolSelected(if (activeTool == AnnotationType.LASSO) null else AnnotationType.LASSO) }
            if (show("smartgraphic")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    SlateToggle(Icons.Default.BubbleChart, "Smarte Grafik", activeTool == AnnotationType.SMART_GRAPHIC, hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("MindMap") }, onClick = { showMenu = false; onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP); onToolSelected(AnnotationType.SMART_GRAPHIC) })
                        DropdownMenuItem(text = { Text("Organigramm") }, onClick = { showMenu = false; onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.ORG_CHART); onToolSelected(AnnotationType.SMART_GRAPHIC) })
                    }
                }
            }
            if (show("translator")) SlateIconBtn(Icons.Default.GTranslate, "Übersetzer", enabled = hasDocument, onClick = onOpenTranslator)
            if (show("converter"))  SlateIconBtn(Icons.Default.ImportExport, "Konverter", enabled = hasDocument, onClick = onOpenConverter)
            if (show("gemini"))     SlateIconBtn(Icons.Default.SmartToy, "KI-Skizze", onClick = onOpenGeminiSketch)
            if (show("scanner"))    SlateIconBtn(Icons.Default.DocumentScanner, "Scanner", onClick = onOpenScanner)
            if (show("search"))     SlateIconBtn(Icons.Default.Search, "Suchen", enabled = hasDocument, onClick = onOpenSearch)
            if (show("tts"))        SlateIconBtn(Icons.Default.RecordVoiceOver, "Vorlesen", enabled = hasDocument, onClick = onOpenTts)
            if (show("wifi"))       SlateIconBtn(Icons.Default.Wifi, "WLAN", enabled = hasDocument, onClick = onOpenWifiShare)
            if (show("cloud"))      SlateIconBtn(Icons.Default.Cloud, "Cloud", onClick = onOpenCloud)
            if (show("createpdf"))  SlateIconBtn(Icons.Default.NoteAdd, "PDF erstellen", onClick = onOpenCreatePdf)
            if (show("password"))   SlateIconBtn(Icons.Default.Lock, "Passwort", enabled = hasDocument, onClick = onOpenPassword)
            SlateSeparatorH()
            if (show("undo")) SlateIconBtn(Icons.Default.Undo, "Rückgängig", enabled = canUndo, onClick = onUndo)
            if (show("redo")) SlateIconBtn(Icons.Default.Redo, "Wiederholen", enabled = canRedo, onClick = onRedo)
            SlateSeparatorH()
            SlateIconBtn(Icons.Default.BugReport, "Bug melden", onClick = onBugReportClick)
            if (show("save")) SlateIconBtn(Icons.Default.Save, "Speichern", enabled = hasDocument, onClick = onSave)
        }
    } else {
        // Portrait: horizontal scrolling top bar
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(slateBg)
                .border(BorderStroke(1.dp, SlateEdge))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlateIconBtn(Icons.Default.FolderOpen, "Öffnen", onClick = onOpenFile)
            if (show("bookmarks")) SlateIconBtn(Icons.Default.Bookmark, "Lesezeichen", enabled = hasDocument, onClick = onBookmarksClick)
            SlateIconBtn(Icons.Default.Settings, "Einstellungen", onClick = onSettingsClick)
            SlateSeparatorV()
            if (show("hand"))         SlateToggle(Icons.Default.PanTool, "Hand-Tool", activeTool == AnnotationType.HAND, hasDocument) { onToolSelected(if (activeTool == AnnotationType.HAND) null else AnnotationType.HAND) }
            if (show("pen"))          SlateToggle(Icons.Default.Brush, "Pinsel", activeTool == AnnotationType.PEN, hasDocument) { onToolSelected(if (activeTool == AnnotationType.PEN) null else AnnotationType.PEN) }
            if (show("marker"))       SlateToggle(Icons.Default.BorderColor, "Marker", activeTool == AnnotationType.HIGHLIGHTER, hasDocument) { onToolSelected(if (activeTool == AnnotationType.HIGHLIGHTER) null else AnnotationType.HIGHLIGHTER) }
            if (show("underline"))    SlateToggle(Icons.Default.FormatUnderlined, "Unterstreichen", activeTool == AnnotationType.UNDERLINE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.UNDERLINE) null else AnnotationType.UNDERLINE) }
            if (show("strikethrough"))SlateToggle(Icons.Default.FormatStrikethrough, "Durchstreichen", activeTool == AnnotationType.STRIKETHROUGH, hasDocument) { onToolSelected(if (activeTool == AnnotationType.STRIKETHROUGH) null else AnnotationType.STRIKETHROUGH) }

            if (show("textbox"))      SlateToggle(Icons.Default.TextFields, "Textfeld", activeTool == AnnotationType.TEXT_BOX, hasDocument) { if (activeTool != AnnotationType.TEXT_BOX) onToolbarTextBoxSelected(); onToolSelected(if (activeTool == AnnotationType.TEXT_BOX) null else AnnotationType.TEXT_BOX) }
            if (show("comment"))      SlateToggle(Icons.Default.ModeComment, "Kommentar", activeTool == AnnotationType.COMMENT, hasDocument) { onToolSelected(if (activeTool == AnnotationType.COMMENT) null else AnnotationType.COMMENT) }
            if (show("eraser"))       SlateToggle(Icons.Default.CleaningServices, "Radierer", activeTool == AnnotationType.ERASER, hasDocument) { onToolSelected(if (activeTool == AnnotationType.ERASER) null else AnnotationType.ERASER) }
            if (show("shapes")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    SlateToggle(Icons.Outlined.Interests, "Formen",
                        activeTool in listOf(AnnotationType.CIRCLE, AnnotationType.SQUARE, AnnotationType.RECTANGLE, AnnotationType.TRIANGLE),
                        hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf(AnnotationType.CIRCLE to "Kreis", AnnotationType.SQUARE to "Quadrat",
                            AnnotationType.RECTANGLE to "Rechteck", AnnotationType.TRIANGLE to "Dreieck"
                        ).forEach { (type, lbl) ->
                            DropdownMenuItem(text = { Text(lbl) }, onClick = { showMenu = false; onToolSelected(if (activeTool == type) null else type) })
                        }
                    }
                }
            }
            if (show("signature"))    SlateToggle(Icons.Default.Gesture, "Unterschrift", activeTool == AnnotationType.SIGNATURE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.SIGNATURE) null else AnnotationType.SIGNATURE) }
            if (show("image"))        SlateToggle(Icons.Default.AddPhotoAlternate, "Bild", activeTool == AnnotationType.IMAGE, hasDocument) { onToolSelected(if (activeTool == AnnotationType.IMAGE) null else AnnotationType.IMAGE) }
            if (show("link"))         SlateIconBtn(Icons.Default.Link, "Link", enabled = hasDocument, onClick = onOpenLink)
            if (show("select"))       SlateToggle(Icons.Default.NearMe, "Auswählen", activeTool == AnnotationType.SELECT, hasDocument) { onToolSelected(if (activeTool == AnnotationType.SELECT) null else AnnotationType.SELECT) }
            if (show("lasso"))        SlateToggle(Icons.Default.SelectAll, "Lasso", activeTool == AnnotationType.LASSO, hasDocument) { onToolSelected(if (activeTool == AnnotationType.LASSO) null else AnnotationType.LASSO) }
            if (show("smartgraphic")) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    SlateToggle(Icons.Default.BubbleChart, "Smarte Grafik", activeTool == AnnotationType.SMART_GRAPHIC, hasDocument) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("MindMap") }, onClick = { showMenu = false; onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.MIND_MAP); onToolSelected(AnnotationType.SMART_GRAPHIC) })
                        DropdownMenuItem(text = { Text("Organigramm") }, onClick = { showMenu = false; onSmartGraphicSelected(com.annotatio.maximus.model.SmartGraphicType.ORG_CHART); onToolSelected(AnnotationType.SMART_GRAPHIC) })
                    }
                }
            }
            if (show("translator")) SlateIconBtn(Icons.Default.GTranslate, "Übersetzer", enabled = hasDocument, onClick = onOpenTranslator)
            if (show("converter"))  SlateIconBtn(Icons.Default.ImportExport, "Konverter", enabled = hasDocument, onClick = onOpenConverter)
            if (show("gemini"))     SlateIconBtn(Icons.Default.SmartToy, "KI-Skizze", onClick = onOpenGeminiSketch)
            if (show("scanner"))    SlateIconBtn(Icons.Default.DocumentScanner, "Scanner", onClick = onOpenScanner)
            if (show("search"))     SlateIconBtn(Icons.Default.Search, "Suchen", enabled = hasDocument, onClick = onOpenSearch)
            if (show("tts"))        SlateIconBtn(Icons.Default.RecordVoiceOver, "Vorlesen", enabled = hasDocument, onClick = onOpenTts)
            if (show("wifi"))       SlateIconBtn(Icons.Default.Wifi, "WLAN", enabled = hasDocument, onClick = onOpenWifiShare)
            if (show("cloud"))      SlateIconBtn(Icons.Default.Cloud, "Cloud", onClick = onOpenCloud)
            if (show("createpdf"))  SlateIconBtn(Icons.Default.NoteAdd, "PDF erstellen", onClick = onOpenCreatePdf)
            if (show("password"))   SlateIconBtn(Icons.Default.Lock, "Passwort", enabled = hasDocument, onClick = onOpenPassword)
            SlateSeparatorV()
            if (show("undo")) SlateIconBtn(Icons.Default.Undo, "Rückgängig", enabled = canUndo, onClick = onUndo)
            if (show("redo")) SlateIconBtn(Icons.Default.Redo, "Wiederholen", enabled = canRedo, onClick = onRedo)
            SlateSeparatorV()
            SlateIconBtn(Icons.Default.BugReport, "Bug melden", onClick = onBugReportClick)
            if (show("save")) SlateIconBtn(Icons.Default.Save, "Speichern", enabled = hasDocument, onClick = onSave)
        }
    }
}

@Composable
private fun SlateToggle(
    icon: ImageVector, label: String, isActive: Boolean, enabled: Boolean, onClick: () -> Unit
) {
    val activeBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A3A52), Color(0xFF0D2840))
    )
    val tint = when {
        !enabled -> Color(0xFF606060)
        isActive -> MaterialTheme.colorScheme.primary
        else     -> Color(0xFFD0D0D0)
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(38.dp)
                .let { if (isActive) it.background(activeBg, RoundedCornerShape(6.dp)) else it }
                .border(
                    BorderStroke(1.dp, if (isActive) SlateEdge else Color.Transparent),
                    RoundedCornerShape(6.dp)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SlateIconBtn(
    icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit
) {
    val tint = if (enabled) Color(0xFFD0D0D0) else Color(0xFF606060)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label, style = MaterialTheme.typography.bodyLarge) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SlateSeparatorH() {
    Spacer(Modifier.height(4.dp))
    Box(Modifier.width(32.dp).height(1.dp).background(SlateEdge))
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SlateSeparatorV() {
    Spacer(Modifier.width(4.dp))
    Box(Modifier.width(1.dp).height(22.dp).background(SlateEdge))
    Spacer(Modifier.width(4.dp))
}

// ──────────────────────────────────────────────────────────────────────────────
// Legacy shared divider (still used by ToolOptionsPanel if needed)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ToolbarDivider() {
    Spacer(modifier = Modifier.width(4.dp))
    Divider(
        modifier = Modifier.height(24.dp).width(1.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
    Spacer(modifier = Modifier.width(4.dp))
}
