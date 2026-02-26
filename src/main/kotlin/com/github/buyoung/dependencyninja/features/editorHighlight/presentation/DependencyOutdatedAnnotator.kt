package com.github.buyoung.dependencyninja.features.editorHighlight.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
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
        val outdatedDependencies = service.outdatedByManifestPath(virtualFile.path)
        outdatedDependencies.forEach { update ->
            val range = update.declared.versionRange ?: return@forEach
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                DependencyNinjaBundle.message(
                    "annotator.updateAvailable",
                    update.declared.coordinate.displayName(),
                    update.latestVersion ?: "?",
                ),
            )
                .range(range)
                .create()
        }
    }
}
