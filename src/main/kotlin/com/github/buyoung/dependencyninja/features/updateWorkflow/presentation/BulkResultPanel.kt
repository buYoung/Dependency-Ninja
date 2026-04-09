package com.github.buyoung.dependencyninja.features.updateWorkflow.presentation

import com.github.buyoung.dependencyninja.features.updateWorkflow.application.PreviewBatch
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

class BulkResultPanel : JPanel(BorderLayout()) {
    private val resultArea = JTextArea()

    init {
        resultArea.isEditable = false
        add(resultArea, BorderLayout.CENTER)
    }

    fun render(previewBatch: PreviewBatch) {
        resultArea.text = previewBatch.items.joinToString(separator = "\n") { item ->
            "${item.packageName}: ${item.validationState.name}"
        }
    }
}
