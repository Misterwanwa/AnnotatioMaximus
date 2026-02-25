package com.annotatio.maximus.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun generateExportFilename(originalName: String? = null): String {
        val base = originalName?.removeSuffix(".pdf") ?: "document"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${base}_annotated_${timestamp}.pdf"
    }
}
