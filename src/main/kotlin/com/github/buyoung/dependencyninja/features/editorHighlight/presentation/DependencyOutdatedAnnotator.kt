package com.github.buyoung.dependencyninja.features.editorHighlight.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.ReasonCode
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
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
        val recommendations = service.recommendationsForManifest(virtualFile.path)
        recommendations
            .filter { it.surfaceAvailability.inlineHints }
            .forEach { recommendation ->
                val range = recommendation.versionRange ?: return@forEach
                holder.newAnnotation(
                    severityFor(recommendation),
                    DependencyNinjaBundle.message(
                        "annotator.recommendation",
                        recommendation.packageName,
                        DependencyNinjaBundle.message("status.${recommendation.status.name.lowercase()}"),
                        recommendation.recommendedVersion ?: DependencyNinjaBundle.message("status.none"),
                        reasonSummary(recommendation),
                    ),
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
            else -> HighlightSeverity.TEXT_ATTRIBUTES
        }
    }

    private fun reasonSummary(recommendation: RecommendationRecord): String {
        val reasonMessages = recommendation.reasonCodes.mapNotNull { reasonCode ->
            when (reasonCode) {
                ReasonCode.UPDATE_AVAILABLE -> null
                ReasonCode.UP_TO_DATE -> null
                else -> DependencyNinjaBundle.message("reason.${reasonCode.name.lowercase()}")
            }
        }
        val reviewOnlySuffix = if (!recommendation.isEditableTarget) {
            listOf(DependencyNinjaBundle.message("annotator.reviewOnly"))
        } else {
            emptyList()
        }
        return (reasonMessages + reviewOnlySuffix).joinToString(", ")
    }
}
