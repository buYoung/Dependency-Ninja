package com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyKind
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.ManifestScope
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain.ManifestTarget
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class ManifestDependencyParser {

    fun parse(
        target: ManifestTarget,
        psiFile: PsiFile?,
    ): ParsedManifest {
        val text = psiFile?.text ?: runCatching { String(target.file.contentsToByteArray()) }.getOrNull() ?: return ParsedManifest()
        return when (target.manifestKind) {
            "package.json" -> parsePackageJson(target.manifestScope, text)
            else -> ParsedManifest()
        }
    }

    private fun parsePackageJson(
        manifestScope: ManifestScope,
        text: String,
    ): ParsedManifest {
        val workspaceReferences = linkedMapOf<String, WorkspaceReference>()
        if (manifestScope.isWorkspaceRoot) {
            collectWorkspaceReferences(text, manifestScope, "resolutions", "catalog", workspaceReferences)
            collectWorkspaceReferences(text, manifestScope, "overrides", "workspace", workspaceReferences)
        }

        val declarations = buildList {
            addAll(collectDependencies(text, manifestScope, "dependencies", DependencyKind.PRODUCTION, workspaceReferences))
            addAll(collectDependencies(text, manifestScope, "devDependencies", DependencyKind.DEVELOPMENT, workspaceReferences))
            addAll(collectDependencies(text, manifestScope, "peerDependencies", DependencyKind.PEER, workspaceReferences))
            addAll(collectDependencies(text, manifestScope, "optionalDependencies", DependencyKind.OPTIONAL, workspaceReferences))
        }

        return ParsedManifest(
            declarations = declarations,
            workspaceReferences = workspaceReferences.values.toList(),
        )
    }

    private fun collectWorkspaceReferences(
        text: String,
        manifestScope: ManifestScope,
        sectionName: String,
        referenceType: String,
        workspaceReferences: MutableMap<String, WorkspaceReference>,
    ) {
        val sectionText = findSection(text, sectionName) ?: return
        val dependencyPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
        dependencyPattern.findAll(sectionText.text).forEach { matchResult ->
            val packageName = matchResult.groupValues[1]
            val rawValue = matchResult.groupValues[2]
            val valueRange = TextRange(
                sectionText.offset + matchResult.groups[2]!!.range.first - 1,
                sectionText.offset + matchResult.groups[2]!!.range.last + 2,
            )
            workspaceReferences[packageName] = WorkspaceReference(
                workspaceReferenceId = createWorkspaceReferenceId(manifestScope.manifestPath, packageName),
                referenceType = referenceType,
                ownerManifestPath = manifestScope.manifestPath,
                packageName = packageName,
                declaredVersionText = rawValue,
                normalizedCurrentVersion = normalizeVersion(rawValue),
                affectedMemberCount = 0,
                versionRange = valueRange,
                coordinate = DependencyCoordinate(Ecosystem.NPM, packageName),
            )
        }
    }

    private fun collectDependencies(
        text: String,
        manifestScope: ManifestScope,
        sectionName: String,
        dependencyKind: DependencyKind,
        workspaceReferences: Map<String, WorkspaceReference>,
    ): List<DependencyDeclaration> {
        val sectionText = findSection(text, sectionName) ?: return emptyList()
        val dependencyPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
        return dependencyPattern.findAll(sectionText.text).map { matchResult ->
            val packageName = matchResult.groupValues[1]
            val rawValue = matchResult.groupValues[2]
            val workspaceReferenceId = when {
                rawValue.startsWith("workspace:") || rawValue.startsWith("catalog:") -> {
                    workspaceReferences[packageName]?.workspaceReferenceId
                        ?: createWorkspaceReferenceId(manifestScope.manifestPath, packageName)
                }

                else -> workspaceReferences[packageName]?.workspaceReferenceId
            }
            DependencyDeclaration(
                declarationId = createDeclarationId(manifestScope.manifestPath, dependencyKind, packageName),
                coordinate = DependencyCoordinate(Ecosystem.NPM, packageName),
                packageName = packageName,
                sourceManifestPath = manifestScope.manifestPath,
                moduleName = manifestScope.moduleName,
                dependencyKind = dependencyKind,
                declaredVersionText = rawValue,
                normalizedCurrentVersion = normalizeVersion(rawValue),
                workspaceReferenceId = workspaceReferenceId,
                isEditableTarget = workspaceReferenceId == null,
                versionRange = TextRange(
                    sectionText.offset + matchResult.groups[2]!!.range.first - 1,
                    sectionText.offset + matchResult.groups[2]!!.range.last + 2,
                ),
                manifestScope = manifestScope,
            )
        }.toList()
    }

    private fun normalizeVersion(rawValue: String): String? {
        val cleanedValue = rawValue.removePrefix("workspace:").removePrefix("catalog:")
        return cleanedValue.takeIf { it.isNotBlank() }?.let(VersionComparator::normalize)
    }

    private fun createDeclarationId(
        manifestPath: String,
        dependencyKind: DependencyKind,
        packageName: String,
    ): String {
        return "$manifestPath::$dependencyKind::$packageName"
    }

    private fun createWorkspaceReferenceId(
        manifestPath: String,
        packageName: String,
    ): String {
        return "$manifestPath::workspace::$packageName"
    }

    private fun findSection(
        text: String,
        sectionName: String,
    ): SectionText? {
        val sectionHeader = "\"$sectionName\""
        val startIndex = text.indexOf(sectionHeader)
        if (startIndex < 0) {
            return null
        }
        val objectStart = text.indexOf('{', startIndex)
        if (objectStart < 0) {
            return null
        }
        var depth = 0
        for (index in objectStart until text.length) {
            when (text[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return SectionText(
                            text = text.substring(objectStart + 1, index),
                            offset = objectStart + 1,
                        )
                    }
                }
            }
        }
        return null
    }
}

data class ParsedManifest(
    val declarations: List<DependencyDeclaration> = emptyList(),
    val workspaceReferences: List<WorkspaceReference> = emptyList(),
)

private data class SectionText(
    val text: String,
    val offset: Int,
)
