package com.annotatio.maximus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.annotatio.maximus.ui.screens.PdfViewerScreen
import com.annotatio.maximus.ui.theme.AnnotatioMaximusTheme
import com.annotatio.maximus.util.PdfTextExtractor
import com.annotatio.maximus.util.loadAppTheme
import com.annotatio.maximus.util.saveAppTheme
import com.annotatio.maximus.viewmodel.PdfViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val incomingPdfUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize PDFBox for text extraction
        PdfTextExtractor.initialize(this)

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        enableEdgeToEdge()
        setContent {
            var appTheme by remember { mutableStateOf(loadAppTheme(this)) }

            AnnotatioMaximusTheme(appTheme = appTheme) {
                val viewModel: PdfViewModel = viewModel()
                val pendingUri by incomingPdfUri.collectAsState()

                LaunchedEffect(pendingUri) {
                    pendingUri?.let { uri ->
                        viewModel.openPdf(uri, this@MainActivity)
                        incomingPdfUri.value = null
                    }
                }

                PdfViewerScreen(
                    viewModel = viewModel,
                    appTheme = appTheme,
                    onAppThemeChange = { theme ->
                        appTheme = theme
                        saveAppTheme(this, theme)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                incomingPdfUri.value = uri
            }
        }
    }
}
