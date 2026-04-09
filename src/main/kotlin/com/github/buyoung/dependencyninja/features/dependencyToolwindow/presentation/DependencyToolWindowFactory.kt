package com.github.buyoung.dependencyninja.features.dependencyToolwindow.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.domain.hasRecommendedUpgrade
import com.github.buyoung.dependencyninja.core.shared.domain.shouldAppearInToolWindowByDefault
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
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.border.EmptyBorder

class DependencyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DependencyToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class DependencyToolWindowPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
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
        val primaryLine = if (recommendation.hasRecommendedUpgrade()) {
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
        val secondaryLine = buildRiskSummaryLine(recommendation)
        return if (secondaryLine == null) {
            primaryLine
        } else {
            primaryLine + "\n" + secondaryLine
        }
    }

    private fun buildRiskSummaryLine(recommendation: RecommendationRecord): String? {
        if (recommendation.status != com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.RISKY) {
            return null
        }
        val advisory = recommendation.advisories.firstOrNull() ?: return recommendation.advisorySummary
        val summary = truncateForRow(advisory.summary)
        return DependencyNinjaBundle.message(
            "toolwindow.row.riskSummary",
            advisory.severityLabel,
            advisory.advisoryId,
            summary,
        )
    }

    private fun formatRecommendationDetail(recommendation: RecommendationRecord): String {
        val recommendedVersion = recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none")
        val editability = if (recommendation.isEditableTarget) {
            DependencyNinjaBundle.message("toolwindow.editable")
        } else {
            DependencyNinjaBundle.message("toolwindow.reviewOnly")
        }
        val relativeManifestPath = formatRelativeManifestPath(recommendation.sourceManifestPath)
        val advisoryDetails = recommendation.advisories.joinToString("\n\n") { advisory ->
            formatAdvisoryDetail(advisory)
        }
        return buildString {
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.package", recommendation.packageName))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.manifest.relative", relativeManifestPath))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.manifest.absolute", recommendation.sourceManifestPath))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.installed", recommendation.currentVersion))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.recommended", recommendedVersion))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.status", DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}")))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.freshness", DependencyNinjaBundle.message("freshness.${recommendation.freshnessState.name.lowercase()}")))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.editability", editability))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.reason", buildReasonSummary(recommendation)))
            if (advisoryDetails.isNotBlank()) {
                appendLine()
                appendLine(DependencyNinjaBundle.message("toolwindow.detail.advisories"))
                append(advisoryDetails)
            }
        }
    }

    private fun formatAdvisoryDetail(advisory: AdvisoryRecord): String {
        return buildString {
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.advisory.severity", advisory.severityLabel))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.advisory.id", advisory.advisoryId))
            appendLine(DependencyNinjaBundle.message("toolwindow.detail.advisory.summary", advisory.summary))
            appendLine(
                DependencyNinjaBundle.message(
                    "toolwindow.detail.advisory.affectedRange",
                    advisory.affectedRange.ifBlank { DependencyNinjaBundle.message("status.none") },
                ),
            )
            append(
                DependencyNinjaBundle.message(
                    "toolwindow.detail.advisory.fixedVersions",
                    advisory.fixedVersions.joinToString(", ").ifBlank { DependencyNinjaBundle.message("status.none") },
                ),
            )
        }
    }

    private fun buildReasonSummary(recommendation: RecommendationRecord): String {
        val reasons = recommendation.visibleReasonCodes()
            .joinToString(", ") { reasonCode ->
                DependencyNinjaBundle.message("reason.${reasonCode.name.lowercase()}")
            }
        return reasons.ifBlank { DependencyNinjaBundle.message("reason.none") }
    }

    private fun formatRelativeManifestPath(absoluteManifestPath: String): String {
        val basePath = project.basePath ?: return absoluteManifestPath.substringAfterLast('/')
        val normalizedBasePath = if (basePath.endsWith("/")) basePath else "$basePath/"
        return if (absoluteManifestPath.startsWith(normalizedBasePath)) {
            absoluteManifestPath.removePrefix(normalizedBasePath)
        } else {
            absoluteManifestPath.substringAfterLast('/')
        }
    }

    private fun truncateForRow(text: String): String {
        val normalizedText = text.replace(Regex("\\s+"), " ").trim()
        return if (normalizedText.length > MAX_ROW_SUMMARY_LENGTH) {
            normalizedText.take(MAX_ROW_SUMMARY_LENGTH - 3) + "..."
        } else {
            normalizedText
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

    private inner class RecommendationListRenderer : JTextArea(), javax.swing.ListCellRenderer<RecommendationRecord> {
        init {
            isOpaque = true
            isEditable = false
            lineWrap = false
            wrapStyleWord = false
            border = EmptyBorder(6, 8, 6, 8)
        }

        override fun getListCellRendererComponent(
            list: JList<out RecommendationRecord>,
            value: RecommendationRecord,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            font = list.font
            text = formatRecommendationRow(value)
            background = if (isSelected) list.selectionBackground else list.background
            foreground = if (isSelected) list.selectionForeground else list.foreground
            return this
        }
    }

    companion object {
        private const val MAX_ROW_SUMMARY_LENGTH = 84
    }
}
