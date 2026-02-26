package com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure

import com.github.buyoung.dependencyninja.core.shared.domain.DeclaredDependency
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain.ManifestTarget
import com.intellij.openapi.util.TextRange

class ManifestDependencyParser {

    fun parse(target: ManifestTarget): List<DeclaredDependency> {
        val text = runCatching { String(target.file.contentsToByteArray()) }.getOrElse { return emptyList() }
        return parse(
            fileName = target.file.name,
            text = text,
            manifestPath = target.file.path,
            moduleName = target.moduleName,
        )
    }

    fun parse(
        fileName: String,
        text: String,
        manifestPath: String,
        moduleName: String,
    ): List<DeclaredDependency> {
        val syntheticTarget = SyntheticTarget(manifestPath = manifestPath, moduleName = moduleName)
        return when (fileName) {
            "package.json" -> parsePackageJson(text, syntheticTarget)
            "deno.json", "deno.jsonc" -> parseDeno(text, syntheticTarget)
            "requirements.txt" -> parseRequirements(text, syntheticTarget)
            "pyproject.toml" -> parsePyproject(text, syntheticTarget)
            "pom.xml" -> parsePom(text, syntheticTarget)
            "build.gradle", "build.gradle.kts" -> parseGradle(text, syntheticTarget)
            "go.mod" -> parseGoMod(text, syntheticTarget)
            else -> emptyList()
        }
    }

    private data class SyntheticTarget(
        val manifestPath: String,
        val moduleName: String,
    )

    private fun parsePackageJson(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val results = mutableListOf<DeclaredDependency>()
        val sectionPattern = Regex(
            "\"(dependencies|devDependencies|peerDependencies|optionalDependencies)\"\\s*:\\s*\\{(.*?)\\}",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val itemPattern = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")

        sectionPattern.findAll(text).forEach { section ->
            val body = section.groups[2]?.value ?: return@forEach
            val bodyStart = section.groups[2]?.range?.first ?: return@forEach
            itemPattern.findAll(body).forEach { item ->
                val dependencyName = item.groups[1]?.value ?: return@forEach
                val currentVersionRaw = item.groups[2]?.value ?: return@forEach
                val versionGroup = item.groups[2] ?: return@forEach
                val absoluteStart = bodyStart + versionGroup.range.first
                val absoluteEndExclusive = bodyStart + versionGroup.range.last + 1
                val currentVersion = VersionComparator.normalize(currentVersionRaw)

                results += DeclaredDependency(
                    coordinate = DependencyCoordinate(ecosystem = Ecosystem.NPM, name = dependencyName),
                    currentVersion = currentVersion,
                    manifestPath = target.manifestPath,
                    moduleName = target.moduleName,
                    versionRange = TextRange(absoluteStart, absoluteEndExclusive),
                )
            }
        }
        return results
    }

    private fun parseDeno(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val pattern = Regex("""npm:([^@"\s]+)@([^"\s]+)""")
        return pattern.findAll(text).map { match ->
            val name = match.groups[1]?.value.orEmpty()
            val versionRaw = match.groups[2]?.value.orEmpty()
            val versionGroup = match.groups[2]!!
            DeclaredDependency(
                coordinate = DependencyCoordinate(ecosystem = Ecosystem.NPM, name = name),
                currentVersion = VersionComparator.normalize(versionRaw),
                manifestPath = target.manifestPath,
                moduleName = target.moduleName,
                versionRange = TextRange(versionGroup.range.first, versionGroup.range.last + 1),
            )
        }.toList()
    }

    private fun parseRequirements(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val pattern = Regex("""(?m)^\s*([A-Za-z0-9_.-]+)\s*[=~!<>]{1,2}\s*([A-Za-z0-9_.+-]+)""")
        return pattern.findAll(text).map { match ->
            val name = match.groups[1]?.value.orEmpty()
            val versionRaw = match.groups[2]?.value.orEmpty()
            val versionGroup = match.groups[2]!!
            DeclaredDependency(
                coordinate = DependencyCoordinate(ecosystem = Ecosystem.PYPI, name = name),
                currentVersion = VersionComparator.normalize(versionRaw),
                manifestPath = target.manifestPath,
                moduleName = target.moduleName,
                versionRange = TextRange(versionGroup.range.first, versionGroup.range.last + 1),
            )
        }.toList()
    }

    private fun parsePyproject(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val results = mutableListOf<DeclaredDependency>()

        val arrayPattern = Regex("""dependencies\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        val depStringPattern = Regex("\"([A-Za-z0-9_.-]+)[=~!<>]+([A-Za-z0-9_.+-]+)\"")
        arrayPattern.findAll(text).forEach { arrayMatch ->
            val body = arrayMatch.groups[1]?.value ?: return@forEach
            val bodyOffset = arrayMatch.groups[1]?.range?.first ?: return@forEach
            depStringPattern.findAll(body).forEach { depMatch ->
                val name = depMatch.groups[1]?.value ?: return@forEach
                val versionRaw = depMatch.groups[2]?.value ?: return@forEach
                val versionGroup = depMatch.groups[2] ?: return@forEach
                results += DeclaredDependency(
                    coordinate = DependencyCoordinate(ecosystem = Ecosystem.PYPI, name = name),
                    currentVersion = VersionComparator.normalize(versionRaw),
                    manifestPath = target.manifestPath,
                    moduleName = target.moduleName,
                    versionRange = TextRange(bodyOffset + versionGroup.range.first, bodyOffset + versionGroup.range.last + 1),
                )
            }
        }

        val poetrySectionPattern = Regex("""(?ms)^\[tool\.poetry\.dependencies\]\s*(.*?)(^\[|\z)""")
        val poetryDepLinePattern = Regex("(?m)^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*\"([^\"]+)\"")
        poetrySectionPattern.findAll(text).forEach { section ->
            val body = section.groups[1]?.value ?: return@forEach
            val bodyOffset = section.groups[1]?.range?.first ?: return@forEach
            poetryDepLinePattern.findAll(body).forEach { depLine ->
                val name = depLine.groups[1]?.value ?: return@forEach
                if (name == "python") return@forEach
                val versionRaw = depLine.groups[2]?.value ?: return@forEach
                val versionGroup = depLine.groups[2] ?: return@forEach
                results += DeclaredDependency(
                    coordinate = DependencyCoordinate(ecosystem = Ecosystem.PYPI, name = name),
                    currentVersion = VersionComparator.normalize(versionRaw),
                    manifestPath = target.manifestPath,
                    moduleName = target.moduleName,
                    versionRange = TextRange(bodyOffset + versionGroup.range.first, bodyOffset + versionGroup.range.last + 1),
                )
            }
        }

        return results
    }

    private fun parsePom(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val depBlockPattern = Regex("<dependency>(.*?)</dependency>", RegexOption.DOT_MATCHES_ALL)
        val groupPattern = Regex("<groupId>([^<]+)</groupId>")
        val artifactPattern = Regex("<artifactId>([^<]+)</artifactId>")
        val versionPattern = Regex("<version>([^<]+)</version>")

        return depBlockPattern.findAll(text).mapNotNull { blockMatch ->
            val block = blockMatch.groups[1]?.value ?: return@mapNotNull null
            val blockOffset = blockMatch.groups[1]?.range?.first ?: return@mapNotNull null
            val group = groupPattern.find(block)?.groups?.get(1)?.value ?: return@mapNotNull null
            val artifact = artifactPattern.find(block)?.groups?.get(1)?.value ?: return@mapNotNull null
            val versionMatch = versionPattern.find(block)?.groups?.get(1) ?: return@mapNotNull null
            DeclaredDependency(
                coordinate = DependencyCoordinate(
                    ecosystem = Ecosystem.MAVEN,
                    name = "$group:$artifact",
                    group = group,
                    artifact = artifact,
                ),
                currentVersion = VersionComparator.normalize(versionMatch.value),
                manifestPath = target.manifestPath,
                moduleName = target.moduleName,
                versionRange = TextRange(blockOffset + versionMatch.range.first, blockOffset + versionMatch.range.last + 1),
            )
        }.toList()
    }

    private fun parseGradle(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val dependencyPattern = Regex("""["']([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^"']+)["']""")
        return dependencyPattern.findAll(text).map { match ->
            val group = match.groups[1]?.value.orEmpty()
            val artifact = match.groups[2]?.value.orEmpty()
            val versionMatch = match.groups[3]!!
            DeclaredDependency(
                coordinate = DependencyCoordinate(
                    ecosystem = Ecosystem.MAVEN,
                    name = "$group:$artifact",
                    group = group,
                    artifact = artifact,
                ),
                currentVersion = VersionComparator.normalize(versionMatch.value),
                manifestPath = target.manifestPath,
                moduleName = target.moduleName,
                versionRange = TextRange(versionMatch.range.first, versionMatch.range.last + 1),
            )
        }.toList()
    }

    private fun parseGoMod(text: String, target: SyntheticTarget): List<DeclaredDependency> {
        val pattern = Regex("""(?m)^\s*([A-Za-z0-9_./-]+)\s+v([0-9][A-Za-z0-9_.+-]*)""")
        return pattern.findAll(text).map { match ->
            val module = match.groups[1]?.value.orEmpty()
            val versionMatch = match.groups[2]!!
            DeclaredDependency(
                coordinate = DependencyCoordinate(ecosystem = Ecosystem.GO, name = module),
                currentVersion = VersionComparator.normalize(versionMatch.value),
                manifestPath = target.manifestPath,
                moduleName = target.moduleName,
                versionRange = TextRange(versionMatch.range.first, versionMatch.range.last + 1),
            )
        }.toList()
    }
}
