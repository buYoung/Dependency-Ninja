package com.github.buyoung.dependencyninja.features.dependencyToolwindow.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.features.updateWorkflow.application.UpdatePreviewService
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdateExecutionMode
import com.github.buyoung.dependencyninja.features.updateWorkflow.presentation.BulkResultPanel
import com.github.buyoung.dependencyninja.features.updateWorkflow.presentation.UpdateWorkflowPanel
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class DependencyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DependencyToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class DependencyToolWindowPanel(project: Project) : JPanel(BorderLayout()) {
    private val service = project.service<DependencyNinjaProjectService>()
    private val previewService = project.service<UpdatePreviewService>()
    private val listModel = DefaultListModel<RecommendationRecord>()
    private val recommendationList = JBList(listModel)
    private val detailLabel = JLabel()
    private val previewPanel = UpdateWorkflowPanel()
    private val bulkResultPanel = BulkResultPanel()

    init {
        recommendationList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        recommendationList.addListSelectionListener {
            val selectedValues = recommendationList.selectedValuesList
            detailLabel.text = selectedValues.joinToString(" | ") { recommendation -> formatRecommendation(recommendation) }
        }

        val refreshButton = JButton(DependencyNinjaBundle.message("toolwindow.refresh"))
        refreshButton.addActionListener { service.refreshInBackground(showNotification = true) }

        val previewButton = JButton(DependencyNinjaBundle.message("toolwindow.preview"))
        previewButton.addActionListener {
            val selectedIds = recommendationList.selectedValuesList.map { it.recommendationId }
            val previewBatch = previewService.previewSelection(
                recommendationIds = selectedIds,
                executionMode = UpdateExecutionMode.MANIFEST_ONLY,
            )
            if (previewBatch.requiresSoftCapAcknowledgement) {
                Messages.showWarningDialog(
                    project,
                    DependencyNinjaBundle.message("toolwindow.bulkSoftCap", previewBatch.summary ?: ""),
                    DependencyNinjaBundle.message("toolwindow.preview"),
                )
            }
            previewPanel.renderPreview(previewBatch)
        }

        val applyButton = JButton(DependencyNinjaBundle.message("toolwindow.apply"))
        applyButton.addActionListener {
            val previewBatch = previewPanel.currentPreviewBatch ?: return@addActionListener
            val result = previewService.applySelection(
                previewBatch = previewBatch,
                acknowledgeWarnings = true,
                acknowledgeSoftCap = true,
            )
            previewPanel.renderPreview(result)
            bulkResultPanel.render(result)
            service.refreshInBackground(showNotification = false)
        }

        val buttonPanel = JPanel(GridLayout(1, 3, 8, 0))
        buttonPanel.add(refreshButton)
        buttonPanel.add(previewButton)
        buttonPanel.add(applyButton)

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(JBScrollPane(recommendationList), BorderLayout.CENTER)
        centerPanel.add(detailLabel, BorderLayout.SOUTH)

        val southPanel = JPanel(GridLayout(2, 1, 8, 8))
        southPanel.add(previewPanel)
        southPanel.add(bulkResultPanel)

        add(buttonPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(southPanel, BorderLayout.SOUTH)

        service.addSnapshotListener { refreshList() }
        refreshList()
    }

    private fun refreshList() {
        listModel.clear()
        val snapshot = service.snapshot()
        if (snapshot.disabledReason != null) {
            detailLabel.text = snapshot.disabledReason
            previewPanel.clear()
            return
        }
        snapshot.recommendations
            .filter { it.surfaceAvailability.toolWindow }
            .sortedWith(compareBy<RecommendationRecord> { it.moduleName }.thenBy { it.packageName })
            .forEach(listModel::addElement)
    }

    private fun formatRecommendation(recommendation: RecommendationRecord): String {
        val editabilityLabel = if (recommendation.isEditableTarget) {
            DependencyNinjaBundle.message("toolwindow.editable")
        } else {
            DependencyNinjaBundle.message("toolwindow.reviewOnly")
        }
        val recommendedVersion = recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none")
        return DependencyNinjaBundle.message(
            "toolwindow.row",
            recommendation.packageName,
            recommendation.currentVersion,
            recommendedVersion,
            DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}"),
            editabilityLabel,
        )
    }
}
