package com.annotatio.maximus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.annotatio.maximus.ui.screens.PdfViewerScreen
import com.annotatio.maximus.ui.theme.AnnotatioMaximusTheme
import com.annotatio.maximus.viewmodel.PdfViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnnotatioMaximusTheme {
                val viewModel: PdfViewModel = viewModel()
                PdfViewerScreen(viewModel = viewModel)
            }
        }
    }
}
