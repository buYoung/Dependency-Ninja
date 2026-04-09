package com.github.buyoung.dependencyninja.features.inspection.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.github.buyoung.dependencyninja.features.updateWorkflow.application.UpdatePreviewService
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdateExecutionMode
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement

class DependencyRecommendationInspection : LocalInspectionTool() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val service = file.project.service<DependencyNinjaProjectService>()
                service.recommendationsForManifest(file.virtualFile?.path ?: return)
                    .filter { it.surfaceAvailability.inspection }
                    .forEach { recommendation ->
                        val targetElement = recommendation.versionRange
                            ?.let { file.findElementAt(it.startOffset) }
                            ?: file as PsiElement
                        holder.registerProblem(
                            targetElement,
                            DependencyNinjaBundle.message(
                                "inspection.description",
                                recommendation.packageName,
                                recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none"),
                            ),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            UpdateDependencyQuickFix(recommendation.recommendationId),
                        )
                    }
            }
        }
    }
}

class UpdateDependencyQuickFix(
    private val recommendationId: String,
) : LocalQuickFix {
    override fun getFamilyName(): String = DependencyNinjaBundle.message("inspection.quickFix.family")

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {
        val previewService = project.service<UpdatePreviewService>()
        val previewBatch = previewService.previewSelection(
            recommendationIds = listOf(recommendationId),
            executionMode = UpdateExecutionMode.MANIFEST_ONLY,
        )
        val previewItem = previewBatch.items.singleOrNull() ?: return
        val warningMessage = previewItem.warningFlags.joinToString(", ") { it.name }
        val previewMessage = buildString {
            appendLine(DependencyNinjaBundle.message("inspection.preview.target", previewItem.targetManifestPath))
            appendLine(DependencyNinjaBundle.message("inspection.preview.change", previewItem.fromVersionText, previewItem.toVersionText))
            if (warningMessage.isNotBlank()) {
                appendLine(DependencyNinjaBundle.message("inspection.preview.warnings", warningMessage))
            }
        }
        val shouldApply = Messages.showYesNoDialog(
            project,
            previewMessage,
            DependencyNinjaBundle.message("inspection.preview.title"),
            null,
        ) == Messages.YES
        if (!shouldApply) {
            return
        }
        previewService.applySelection(
            previewBatch = previewBatch,
            acknowledgeWarnings = true,
            acknowledgeSoftCap = true,
        )
    }
}
