package com.annotatio.maximus.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PdfCompareViewer(
    uriA: Uri?,
    uriB: Uri?,
    mode: CompareMode,
    isSyncZoomEnabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    var pageA by remember { mutableIntStateOf(0) }
    var pageB by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Compare Header Bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Vergleich: ${mode.label}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    if (mode == CompareMode.OVERLAY_DIFF) {
                        // Legend
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFE53935), CircleShape))
                            Text("Alt (A)", style = MaterialTheme.typography.labelSmall)
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF43A047), CircleShape))
                            Text("Neu (B)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Vergleich schließen")
                }
            }
        }

        // Viewport based on mode
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                CompareMode.SIDE_BY_SIDE -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PdfViewer(
                                pdfUri = uriA,
                                modifier = Modifier.fillMaxSize(),
                                onPageChanged = { page, _ ->
                                    pageA = page
                                    if (isSyncZoomEnabled) pageB = page
                                }
                            )
                            Text(
                                text = "Revision A",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp)
                            )
                        }
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PdfViewer(
                                pdfUri = uriB,
                                modifier = Modifier.fillMaxSize(),
                                onPageChanged = { page, _ ->
                                    pageB = page
                                    if (isSyncZoomEnabled) pageA = page
                                }
                            )
                            Text(
                                text = "Revision B",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                CompareMode.SPLIT_SLIDER -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalW = maxWidth
                        val totalH = maxHeight
                        val splitW = totalW * splitRatio

                        // Revision B full
                        PdfViewer(pdfUri = uriB, modifier = Modifier.fillMaxSize())

                        // Revision A clipped to splitW
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(splitW)
                                .clipToBounds()
                        ) {
                            PdfViewer(
                                pdfUri = uriA,
                                modifier = Modifier.size(width = totalW, height = totalH)
                            )
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(splitW)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(3.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }

                        // Slider control at bottom
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(0.6f)
                        ) {
                            Slider(
                                value = splitRatio,
                                onValueChange = { splitRatio = it },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                CompareMode.OVERLAY_DIFF -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Underlay: Revision A with Red tint
                        Box(modifier = Modifier.fillMaxSize()) {
                            PdfViewer(pdfUri = uriA, modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE53935).copy(alpha = 0.25f)))
                        }

                        // Overlay: Revision B with Green tint and half transparency
                        Box(modifier = Modifier.fillMaxSize().alpha(0.55f)) {
                            PdfViewer(pdfUri = uriB, modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF43A047).copy(alpha = 0.25f)))
                        }
                    }
                }
            }
        }
    }
}
