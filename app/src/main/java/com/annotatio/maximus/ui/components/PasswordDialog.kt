package com.annotatio.maximus.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Dialog for password-protecting the current PDF using PDFBox.
 * Sets an owner password (restricts printing/editing) and a user password
 * (required to open the file).
 */
@Composable
fun PasswordDialog(
    pdfUri: Uri?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Passwortschutz") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "Setze ein Passwort, um das PDF zu verschlüsseln. Das Passwort wird zum Öffnen benötigt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = userPassword,
                    onValueChange = { userPassword = it },
                    label = { Text("Passwort") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Passwort bestätigen") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        userPassword.isBlank() -> statusText = "Bitte ein Passwort eingeben."
                        userPassword != confirmPassword -> statusText = "Passwörter stimmen nicht überein."
                        pdfUri == null -> statusText = "Keine PDF geöffnet."
                        else -> {
                            scope.launch {
                                isProcessing = true
                                statusText = "Verschlüssele…"
                                val result = withContext(Dispatchers.IO) {
                                    encryptPdf(context, pdfUri, userPassword)
                                }
                                isProcessing = false
                                if (result != null) {
                                    statusText = "Verschlüsselt – teile es…"
                                    val uri = FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", result
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Verschlüsselte PDF teilen"))
                                    onDismiss()
                                } else {
                                    statusText = "Fehler beim Verschlüsseln."
                                }
                            }
                        }
                    }
                },
                enabled = !isProcessing
            ) { Text("Verschlüsseln & Teilen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun encryptPdf(context: Context, sourceUri: Uri, password: String): File? {
    return try {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
            val protection = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
            val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                password, // owner password
                password, // user password
                protection
            )
            spp.encryptionKeyLength = 128
            doc.protect(spp)

            val dir = File(context.cacheDir, "encrypted_pdfs").apply { mkdirs() }
            val outFile = File(dir, "protected_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { doc.save(it) }
            doc.close()
            outFile
        }
    } catch (_: Exception) {
        null
    }
}
