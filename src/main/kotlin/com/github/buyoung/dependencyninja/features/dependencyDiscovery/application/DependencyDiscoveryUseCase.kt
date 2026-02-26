package com.github.buyoung.dependencyninja.features.dependencyDiscovery.application

import com.github.buyoung.dependencyninja.core.shared.domain.DeclaredDependency
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain.ManifestTarget
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex

class DependencyDiscoveryUseCase(
    private val parser: ManifestDependencyParser,
) {
    fun discover(project: Project): List<DeclaredDependency> {
        val targets = mutableListOf<ManifestTarget>()
        val fileIndex = ProjectFileIndex.getInstance(project)
        fileIndex.iterateContent { file ->
            if (!file.isDirectory && isSupportedManifest(file.name) && !isIgnored(file.path)) {
                val moduleName = fileIndex.getContentRootForFile(file)?.name ?: project.name
                targets += ManifestTarget(file, moduleName)
            }
            true
        }

        return targets.flatMap { parser.parse(it) }
    }

    private fun isIgnored(path: String): Boolean {
        return path.contains("/node_modules/") ||
            path.contains("/.git/") ||
            path.contains("/.gradle/") ||
            path.contains("/build/")
    }

    private fun isSupportedManifest(fileName: String): Boolean = fileName in supportedFileNames

    companion object {
        private val supportedFileNames = setOf(
            "package.json",
            "deno.json",
            "deno.jsonc",
            "requirements.txt",
            "pyproject.toml",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "go.mod",
        )
    }
}
