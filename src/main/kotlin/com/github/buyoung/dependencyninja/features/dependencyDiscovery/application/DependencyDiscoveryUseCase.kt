package com.github.buyoung.dependencyninja.features.dependencyDiscovery.application

import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.ManifestScope
import com.github.buyoung.dependencyninja.core.shared.domain.RegistryContext
import com.github.buyoung.dependencyninja.core.shared.infrastructure.PsiReadOps
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain.DiscoveryResult
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain.ManifestTarget
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiManager

class DependencyDiscoveryUseCase(
    private val parser: ManifestDependencyParser,
) {
    fun discover(project: Project): DiscoveryResult {
        return PsiReadOps.nonBlockingRead(project) {
            val fileIndex = ProjectFileIndex.getInstance(project)
            val psiManager = PsiManager.getInstance(project)
            val declarations = mutableListOf<com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration>()
            val workspaceReferences = mutableListOf<com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference>()

            collectTargets(project, fileIndex).forEach { target ->
                val parsedManifest = parser.parse(target, psiManager.findFile(target.file))
                declarations += parsedManifest.declarations
                workspaceReferences += parsedManifest.workspaceReferences
            }

            DiscoveryResult(
                declarations = declarations,
                workspaceReferences = mergeWorkspaceReferences(workspaceReferences, declarations),
            )
        }
    }

    private fun collectTargets(
        project: Project,
        fileIndex: ProjectFileIndex,
    ): List<ManifestTarget> {
        val targets = mutableListOf<ManifestTarget>()
        fileIndex.iterateContent { file ->
            if (!file.isDirectory && isSupportedManifest(file.name) && !isIgnored(file.path)) {
                val moduleName = fileIndex.getContentRootForFile(file)?.name ?: project.name
                val manifestScope = ManifestScope(
                    manifestPath = file.path,
                    manifestKind = file.name,
                    ecosystem = Ecosystem.NPM,
                    moduleName = moduleName,
                    isWorkspaceRoot = file.path == project.basePath + "/package.json",
                    registryContext = RegistryContext(packageManager = detectPackageManager(file.path)),
                )
                targets += ManifestTarget(file = file, manifestScope = manifestScope)
            }
            true
        }
        return targets
    }

    private fun mergeWorkspaceReferences(
        workspaceReferences: List<com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference>,
        declarations: List<com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration>,
    ): List<com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference> {
        val affectedMemberCountByReferenceId = declarations
            .mapNotNull { it.workspaceReferenceId }
            .groupingBy { it }
            .eachCount()
        return workspaceReferences.map { reference ->
            reference.copy(
                affectedMemberCount = affectedMemberCountByReferenceId[reference.workspaceReferenceId] ?: reference.affectedMemberCount,
            )
        }
    }

    private fun detectPackageManager(path: String): String {
        return when {
            path.contains("pnpm", ignoreCase = true) -> "pnpm"
            path.contains("yarn", ignoreCase = true) -> "yarn"
            path.contains("bun", ignoreCase = true) -> "bun"
            else -> "npm"
        }
    }

    private fun isIgnored(path: String): Boolean {
        return path.contains("/node_modules/") ||
            path.contains("/.git/") ||
            path.contains("/.gradle/") ||
            path.contains("/build/") ||
            path.contains("/dist/")
    }

    private fun isSupportedManifest(fileName: String): Boolean {
        return fileName in setOf(
            "package.json",
            "pnpm-workspace.yaml",
            "bunfig.toml",
        )
    }
}
