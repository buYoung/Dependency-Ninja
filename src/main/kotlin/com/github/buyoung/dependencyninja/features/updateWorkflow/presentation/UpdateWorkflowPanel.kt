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
        previewArea.lineWrap = true
        previewArea.wrapStyleWord = true
        add(previewArea, BorderLayout.CENTER)
    }

    fun renderPreview(previewBatch: PreviewBatch) {
        currentPreviewBatch = previewBatch
        previewArea.text = buildString {
            if (previewBatch.requiresSoftCapAcknowledgement) {
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.summary", DependencyNinjaBundle.message("toolwindow.bulkSoftCap", previewBatch.softCap.toString())))
            }
            if (previewBatch.nonActionableCount > 0) {
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.skipped", previewBatch.nonActionableCount))
            }
            if (previewBatch.items.isEmpty()) {
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.noActionableSelection"))
            }
            previewBatch.items.forEach { item ->
                appendLine(DependencyNinjaBundle.message("toolwindow.preview.item", item.packageName, item.fromVersionText, item.toVersionText))
                if (item.warningFlags.isNotEmpty()) {
                    appendLine(
                        DependencyNinjaBundle.message(
                            "toolwindow.preview.warnings",
                            item.warningFlags.joinToString(", ") { warningFlag ->
                                DependencyNinjaBundle.message("warning.${warningFlag.name.lowercase()}")
                            },
                        ),
                    )
                }
            }
        }
    }

    fun clear() {
        currentPreviewBatch = null
        previewArea.text = ""
    }
}
