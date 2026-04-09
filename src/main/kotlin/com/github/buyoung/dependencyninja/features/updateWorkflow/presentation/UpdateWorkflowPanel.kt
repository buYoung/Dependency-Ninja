package com.github.buyoung.dependencyninja.features.updateWorkflow.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.features.updateWorkflow.application.PreviewBatch
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

class UpdateWorkflowPanel : JPanel(BorderLayout()) {
    private val previewArea = JTextArea()
    var currentPreviewBatch: PreviewBatch? = null
        private set

    init {
        previewArea.isEditable = false
        add(previewArea, BorderLayout.CENTER)
    }

    fun renderPreview(previewBatch: PreviewBatch) {
        currentPreviewBatch = previewBatch
        previewArea.text = buildString {
            if (previewBatch.summary != null) {
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.summary", previewBatch.summary))
            }
            previewBatch.items.forEach { item ->
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.item", item.packageName, item.fromVersionText, item.toVersionText))
                if (item.warningFlags.isNotEmpty()) {
                    appendLine(DependencyNinjaBundle.message("toolwindow.preview.warnings", item.warningFlags.joinToString(", ")))
                }
            }
        }
    }

    fun clear() {
        currentPreviewBatch = null
        previewArea.text = ""
    }
}
