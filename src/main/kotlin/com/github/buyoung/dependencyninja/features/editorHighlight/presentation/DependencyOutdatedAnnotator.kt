package com.github.buyoung.dependencyninja.features.editorHighlight.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.ReasonCode
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.domain.shouldShowInlineHint
import com.github.buyoung.dependencyninja.core.shared.domain.supportsUpdateAction
import com.github.buyoung.dependencyninja.core.shared.domain.visibleReasonCodes
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class DependencyOutdatedAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        val virtualFile = file.virtualFile ?: return

        val service = file.project.service<DependencyNinjaProjectService>()
        service.recommendationsForManifest(virtualFile.path)
            .filter { it.shouldShowInlineHint() }
            .forEach { recommendation ->
                val range = recommendation.versionRange ?: return@forEach
                holder.newAnnotation(
                    severityFor(recommendation),
                    buildMessage(recommendation),
                )
                    .range(range)
                    .create()
            }
    }

    private fun severityFor(recommendation: RecommendationRecord): HighlightSeverity {
        return when (recommendation.status) {
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.RISKY -> HighlightSeverity.WARNING
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.OUTDATED -> HighlightSeverity.INFORMATION
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.STALE -> HighlightSeverity.INFORMATION
            com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus.VERIFICATION_UNAVAILABLE -> HighlightSeverity.WEAK_WARNING
            else -> HighlightSeverity.INFORMATION
        }
    }

    private fun buildMessage(recommendation: RecommendationRecord): String {
        val reasonSummary = buildReasonSummary(recommendation)
        return if (recommendation.supportsUpdateAction()) {
            val recommendedVersion = recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none")
            DependencyNinjaBundle.message(
                "annotator.recommendation.withUpgrade",
                recommendation.packageName,
                recommendation.currentVersion,
                recommendedVersion,
                DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}"),
                reasonSummary,
            )
        } else {
            DependencyNinjaBundle.message(
                "annotator.recommendation.statusOnly",
                recommendation.packageName,
                DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}"),
                reasonSummary,
            )
        }
    }

    private fun buildReasonSummary(recommendation: RecommendationRecord): String {
        val reasonMessages = recommendation.visibleReasonCodes().mapNotNull { reasonCode ->
            when (reasonCode) {
                ReasonCode.UPDATE_AVAILABLE,
                ReasonCode.UP_TO_DATE,
                -> null

                ReasonCode.ADVISORY_FLAGGED -> {
                    recommendation.advisorySummary?.let {
                        DependencyNinjaBundle.message("reason.advisory_flagged_with_summary", it)
                    } ?: DependencyNinjaBundle.message("reason.${reasonCode.name.lowercase()}")
                }

                else -> DependencyNinjaBundle.message("reason.${reasonCode.name.lowercase()}")
            }
        }
        val reviewOnlySuffix = if (!recommendation.isEditableTarget) {
            listOf(DependencyNinjaBundle.message("annotator.reviewOnly"))
        } else {
            emptyList()
        }
        val summary = (reasonMessages + reviewOnlySuffix).joinToString(", ")
        return summary.ifBlank { DependencyNinjaBundle.message("reason.none") }
    }
}
