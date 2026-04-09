package com.github.buyoung.dependencyninja.features.updateWorkflow.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.features.updateWorkflow.application.PreviewBatch
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

class BulkResultPanel : JPanel(BorderLayout()) {
    private val resultArea = JTextArea()

    init {
        resultArea.isEditable = false
        resultArea.lineWrap = true
        resultArea.wrapStyleWord = true
        add(resultArea, BorderLayout.CENTER)
    }

    fun render(previewBatch: PreviewBatch) {
        resultArea.text = previewBatch.items.joinToString(separator = "\n") { item ->
            DependencyNinjaBundle.message(
                "toolwindow.result.item",
                item.packageName,
                DependencyNinjaBundle.message("validation.${item.validationState.name.lowercase()}"),
            )
        }
    }
}
