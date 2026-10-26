package com.annotatio.maximus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.annotatio.maximus.model.AecToolType
import com.annotatio.maximus.model.DrawingScale

@Composable
fun AecToolbarMenu(
    selectedTool: AecToolType,
    currentScale: DrawingScale,
    onToolSelected: (AecToolType) -> Unit,
    onOpenScaleDialog: () -> Unit,
    onOpenTakeoffDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scale Button
            FilterChip(
                selected = false,
                onClick = onOpenScaleDialog,
                leadingIcon = { Icon(Icons.Default.Straighten, contentDescription = null) },
                label = { Text("Maßstab: ${currentScale.label} (${currentScale.unit})") }
            )

            // Distance Tool
            FilterChip(
                selected = selectedTool == AecToolType.DISTANCE,
                onClick = {
                    onToolSelected(if (selectedTool == AecToolType.DISTANCE) AecToolType.NONE else AecToolType.DISTANCE)
                },
                leadingIcon = { Icon(Icons.Default.LinearScale, contentDescription = null) },
                label = { Text("Strecke") }
            )

            // Area Tool
            FilterChip(
                selected = selectedTool == AecToolType.AREA,
                onClick = {
                    onToolSelected(if (selectedTool == AecToolType.AREA) AecToolType.NONE else AecToolType.AREA)
                },
                leadingIcon = { Icon(Icons.Default.SquareFoot, contentDescription = null) },
                label = { Text("Fläche & Umfang") }
            )

            // Count Tool
            FilterChip(
                selected = selectedTool == AecToolType.COUNT,
                onClick = {
                    onToolSelected(if (selectedTool == AecToolType.COUNT) AecToolType.NONE else AecToolType.COUNT)
                },
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                label = { Text("Zählen") }
            )

            // Revision Cloud
            FilterChip(
                selected = selectedTool == AecToolType.REVISION_CLOUD,
                onClick = {
                    onToolSelected(if (selectedTool == AecToolType.REVISION_CLOUD) AecToolType.NONE else AecToolType.REVISION_CLOUD)
                },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                label = { Text("Revisionswolke") }
            )

            // Callout
            FilterChip(
                selected = selectedTool == AecToolType.CALLOUT,
                onClick = {
                    onToolSelected(if (selectedTool == AecToolType.CALLOUT) AecToolType.NONE else AecToolType.CALLOUT)
                },
                leadingIcon = { Icon(Icons.Default.Message, contentDescription = null) },
                label = { Text("Callout") }
            )

            // Takeoff List
            FilterChip(
                selected = false,
                onClick = onOpenTakeoffDialog,
                leadingIcon = { Icon(Icons.Default.Calculate, contentDescription = null) },
                label = { Text("Aufmaß-Stückliste") }
            )
        }
    }
}
