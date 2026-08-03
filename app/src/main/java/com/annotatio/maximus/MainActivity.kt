package com.annotatio.maximus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.annotatio.maximus.ui.screens.PdfViewerScreen
import com.annotatio.maximus.ui.theme.AnnotatioMaximusTheme
import com.annotatio.maximus.ui.theme.AppTheme
import com.annotatio.maximus.util.PdfTextExtractor
import com.annotatio.maximus.util.loadAppTheme
import com.annotatio.maximus.util.saveAppTheme
import com.annotatio.maximus.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PDFBox for text extraction
        PdfTextExtractor.initialize(this)
        
        enableEdgeToEdge()
        setContent {
            var appTheme by remember { mutableStateOf(loadAppTheme(this)) }

            AnnotatioMaximusTheme(appTheme = appTheme) {
                val viewModel: PdfViewModel = viewModel()
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
}
