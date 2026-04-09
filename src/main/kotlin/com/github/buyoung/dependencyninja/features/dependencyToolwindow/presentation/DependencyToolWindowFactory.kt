package com.github.buyoung.dependencyninja.features.dependencyToolwindow.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.domain.hasRecommendedUpgrade
import com.github.buyoung.dependencyninja.core.shared.domain.shouldAppearInToolWindowByDefault
import com.github.buyoung.dependencyninja.core.shared.domain.supportsUpdateAction
import com.github.buyoung.dependencyninja.core.shared.domain.visibleReasonCodes
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
import java.awt.Component
import java.awt.GridLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
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
    private val detailArea = JTextArea()
    private val previewPanel = UpdateWorkflowPanel()
    private val bulkResultPanel = BulkResultPanel()

    init {
        recommendationList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        recommendationList.cellRenderer = RecommendationListRenderer()
        recommendationList.addListSelectionListener {
            val selectedValues = recommendationList.selectedValuesList
            detailArea.text = selectedValues.joinToString("\n\n") { recommendation ->
                formatRecommendationDetail(recommendation)
            }
        }

        detailArea.isEditable = false
        detailArea.lineWrap = true
        detailArea.wrapStyleWord = true

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
                    DependencyNinjaBundle.message("toolwindow.bulkSoftCap", previewBatch.softCap.toString()),
                    DependencyNinjaBundle.message("toolwindow.preview"),
                )
            }
            if (previewBatch.items.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    DependencyNinjaBundle.message("toolwindow.preview.noActionableSelection"),
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
        centerPanel.add(JBScrollPane(detailArea), BorderLayout.SOUTH)

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
            detailArea.text = snapshot.disabledReason
            previewPanel.clear()
            return
        }
        snapshot.recommendations
            .filter { it.shouldAppearInToolWindowByDefault() }
            .sortedWith(
                compareBy<RecommendationRecord> { statusOrder(it) }
                    .thenBy { it.moduleName }
                    .thenBy { it.packageName },
            )
            .forEach(listModel::addElement)
    }

    private fun formatRecommendationRow(recommendation: RecommendationRecord): String {
        val manifestName = recommendation.sourceManifestPath.substringAfterLast('/')
        val statusLabel = DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}")
        return if (recommendation.hasRecommendedUpgrade()) {
            DependencyNinjaBundle.message(
                "toolwindow.row.withUpgrade",
                recommendation.packageName,
                recommendation.currentVersion,
                recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none"),
                statusLabel,
                manifestName,
            )
        } else {
            DependencyNinjaBundle.message(
                "toolwindow.row.statusOnly",
                recommendation.packageName,
                recommendation.currentVersion,
                statusLabel,
                manifestName,
            )
        }
    }

    private fun formatRecommendationDetail(recommendation: RecommendationRecord): String {
        val reasonSummary = recommendation.visibleReasonCodes()
            .joinToString(", ") { DependencyNinjaBundle.message("reason.${it.name.lowercase()}") }
            .ifBlank { DependencyNinjaBundle.message("reason.none") }
        val recommendedVersion = recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none")
        val editability = if (recommendation.isEditableTarget) {
            DependencyNinjaBundle.message("toolwindow.editable")
        } else {
            DependencyNinjaBundle.message("toolwindow.reviewOnly")
        }
        return buildString {
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.package", recommendation.packageName))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.manifest", recommendation.sourceManifestPath))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.current", recommendation.currentVersion))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.recommended", recommendedVersion))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.status", DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}")))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.freshness", DependencyNinjaBundle.message("freshness.${recommendation.freshnessState.name.lowercase()}")))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.editability", editability))
            append(DependencyNinjaBundle.message("toolwindow.detail.reason", reasonSummary))
        }
    }

    private fun statusOrder(recommendation: RecommendationRecord): Int {
        return when (recommendation.status) {
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.RISKY -> 0
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.OUTDATED -> 1
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.BLOCKED -> 2
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.STALE -> 3
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.VERIFICATION_UNAVAILABLE -> 4
            else -> 5
        }
    }

    private inner class RecommendationListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            text = (value as? RecommendationRecord)?.let(::formatRecommendationRow).orEmpty()
            return component
        }
    }
}
