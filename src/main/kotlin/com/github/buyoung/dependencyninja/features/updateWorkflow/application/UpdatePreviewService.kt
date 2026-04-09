package com.github.buyoung.dependencyninja.features.updateWorkflow.application

import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.features.settings.application.PolicyProfileService
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdateExecutionMode
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdatePreviewItem
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.ValidationState
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.WarningFlag
import com.github.buyoung.dependencyninja.features.updateWorkflow.infrastructure.ManifestEditor
import com.github.buyoung.dependencyninja.features.updateWorkflow.infrastructure.PackageManagerExecutor
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem

@Service(Service.Level.PROJECT)
class UpdatePreviewService(
    private val project: Project,
) {
    private val projectService by lazy { project.service<DependencyNinjaProjectService>() }
    private val policyProfileService by lazy { project.service<PolicyProfileService>() }
    private val manifestEditor by lazy { ManifestEditor(project) }
    private val packageManagerExecutor = PackageManagerExecutor()

    fun previewSelection(
        recommendationIds: List<String>,
        executionMode: UpdateExecutionMode = UpdateExecutionMode.MANIFEST_ONLY,
        acknowledgeSoftCap: Boolean = false,
    ): PreviewBatch {
        val recommendations = recommendationIds.mapNotNull(projectService::findRecommendation)
        val items = recommendations.map { buildPreview(it, executionMode) }
        val softCap = policyProfileService.currentProfile().bulkApplySoftCap
        val requiresSoftCapAcknowledgement = items.size > softCap && !acknowledgeSoftCap
        return PreviewBatch(
            items = items,
            requiresSoftCapAcknowledgement = requiresSoftCapAcknowledgement,
            summary = if (requiresSoftCapAcknowledgement) {
                "bulk-soft-cap:$softCap"
            } else {
                null
            },
        )
    }

    fun applySelection(
        previewBatch: PreviewBatch,
        acknowledgeWarnings: Boolean,
        acknowledgeSoftCap: Boolean,
    ): PreviewBatch {
        if (previewBatch.requiresSoftCapAcknowledgement && !acknowledgeSoftCap) {
            return previewBatch
        }
        val appliedItems = previewBatch.items.map { previewItem ->
            val approvedItem = previewItem.copy(validationState = ValidationState.APPROVED)
            if (approvedItem.warningFlags.isNotEmpty() && !acknowledgeWarnings) {
                approvedItem
            } else if (approvedItem.executionMode == UpdateExecutionMode.MANIFEST_ONLY) {
                manifestEditor.applyPreview(approvedItem.copy(validationState = ValidationState.APPLIED))
            } else {
                packageManagerExecutor.execute(approvedItem)
            }
        }
        return previewBatch.copy(
            items = appliedItems,
            requiresSoftCapAcknowledgement = false,
        )
    }

    private fun buildPreview(
        recommendation: RecommendationRecord,
        executionMode: UpdateExecutionMode,
    ): UpdatePreviewItem {
        val file = LocalFileSystem.getInstance().findFileByPath(recommendation.targetManifestPath)
        val fileText = runCatching { file?.contentsToByteArray()?.decodeToString() }.getOrNull().orEmpty()
        val range = recommendation.targetVersionRange
        val fromText = if (range != null && range.endOffset <= fileText.length) {
            fileText.substring(range.startOffset, range.endOffset)
        } else {
            recommendation.targetDeclaredVersionText
        }
        val warningFlags = linkedSetOf<WarningFlag>().apply {
            if (hasUncommittedChanges(recommendation.targetManifestPath)) {
                add(WarningFlag.UNCOMMITTED_VCS_CHANGES)
            }
            when (recommendation.freshnessState) {
                FreshnessState.STALE -> add(WarningFlag.STALE_METADATA)
                FreshnessState.UNAVAILABLE -> add(WarningFlag.VERIFICATION_UNAVAILABLE_METADATA)
                FreshnessState.FRESH -> Unit
            }
        }

        return UpdatePreviewItem(
            previewId = "${recommendation.recommendationId}:${executionMode.name}",
            recommendationId = recommendation.recommendationId,
            targetManifestPath = recommendation.targetManifestPath,
            packageName = recommendation.packageName,
            fromVersionText = fromText,
            toVersionText = "\"${recommendation.recommendedVersion ?: recommendation.currentVersion}\"",
            executionMode = executionMode,
            warningFlags = warningFlags,
            validationState = ValidationState.PREVIEWED,
            targetRange = recommendation.targetVersionRange,
            commandPreview = if (executionMode == UpdateExecutionMode.PACKAGE_MANAGER_EXECUTION) {
                "npm install ${recommendation.packageName}@${recommendation.recommendedVersion ?: recommendation.currentVersion}"
            } else {
                null
            },
        )
    }

    private fun hasUncommittedChanges(manifestPath: String): Boolean {
        val file = LocalFileSystem.getInstance().findFileByPath(manifestPath) ?: return false
        return ChangeListManager.getInstance(project).isFileAffected(file)
    }
}

data class PreviewBatch(
    val items: List<UpdatePreviewItem>,
    val requiresSoftCapAcknowledgement: Boolean,
    val summary: String?,
)
