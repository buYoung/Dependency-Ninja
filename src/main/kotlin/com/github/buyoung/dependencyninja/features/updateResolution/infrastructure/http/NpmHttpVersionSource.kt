package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.cache.RegistryResponseCache
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class NpmHttpVersionSource(
    private val httpClient: HttpClient,
    private val cache: RegistryResponseCache,
) : VersionSource {
    override val ecosystem: Ecosystem = Ecosystem.NPM
    override val channel: LookupChannel = LookupChannel.HTTP_REGISTRY

    override fun resolveRegistryObservation(coordinate: DependencyCoordinate): RegistryObservation {
        val encodedName = URLEncoder.encode(coordinate.name, StandardCharsets.UTF_8)
        val registryUrl = "https://registry.npmjs.org"
        return cache.load(registryUrl = registryUrl, packageName = coordinate.name) {
            val body = httpClient.get("$registryUrl/$encodedName") ?: return@load null
            val versionsObject = extractObjectBody(body, "versions").orEmpty()
            val timeObject = extractObjectBody(body, "time").orEmpty()
            val latestVersion = extractObjectBody(body, "dist-tags")
                ?.let { extractTopLevelStringValues(it)["latest"] }
                ?.takeIf(::isSemverLike)
                ?.let(VersionComparator::normalize)

            val availableVersions = extractTopLevelObjectKeys(versionsObject)
                .mapNotNull { version -> version.takeIf(::isSemverLike)?.let(VersionComparator::normalize) }
                .distinct()
                .sortedWith { left, right -> VersionComparator.compare(right, left) }
                .let { versions ->
                    if (latestVersion == null || latestVersion !in versions) {
                        versions
                    } else {
                        listOf(latestVersion) + versions.filter { it != latestVersion }
                    }
                }

            val releaseTimestamps = extractTopLevelStringValues(timeObject)
                .filterKeys(::isSemverLike)
                .mapKeys { (version, _) -> VersionComparator.normalize(version) }
                .mapNotNull { (version, value) ->
                    runCatching { Instant.parse(value) }.getOrNull()?.let { version to it }
                }
                .toMap()

            RegistryObservation(
                packageName = coordinate.name,
                registryUrl = registryUrl,
                availableVersions = availableVersions,
                fetchedAt = Instant.now(),
                freshnessState = FreshnessState.FRESH,
                releaseTimestamps = releaseTimestamps,
            )
        }
    }

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        return resolveRegistryObservation(coordinate).availableVersions.firstOrNull()
    }

    private fun extractTopLevelObjectKeys(objectBody: String): List<String> {
        val keys = mutableListOf<String>()
        iterateTopLevelEntries(objectBody) { key, _, isObjectValue ->
            if (isObjectValue) {
                keys += key
            }
        }
        return keys
    }

    private fun extractTopLevelStringValues(objectBody: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        iterateTopLevelEntries(objectBody) { key, value, isObjectValue ->
            if (!isObjectValue) {
                values[key] = value
            }
        }
        return values
    }

    private fun iterateTopLevelEntries(
        objectBody: String,
        consumer: (key: String, value: String, isObjectValue: Boolean) -> Unit,
    ) {
        var index = 0
        while (index < objectBody.length) {
            val keyStart = objectBody.indexOf('"', index)
            if (keyStart < 0) {
                return
            }
            val keyEnd = findStringEnd(objectBody, keyStart + 1)
            if (keyEnd < 0) {
                return
            }
            val key = objectBody.substring(keyStart + 1, keyEnd)
            val colonIndex = objectBody.indexOf(':', keyEnd)
            if (colonIndex < 0) {
                return
            }
            var valueStart = colonIndex + 1
            while (valueStart < objectBody.length && objectBody[valueStart].isWhitespace()) {
                valueStart += 1
            }
            if (valueStart >= objectBody.length) {
                return
            }
            when (objectBody[valueStart]) {
                '{' -> {
                    val valueEnd = findMatchingBracket(objectBody, valueStart, '{', '}')
                    if (valueEnd < 0) {
                        return
                    }
                    consumer(key, objectBody.substring(valueStart, valueEnd + 1), true)
                    index = valueEnd + 1
                }

                '"' -> {
                    val valueEnd = findStringEnd(objectBody, valueStart + 1)
                    if (valueEnd < 0) {
                        return
                    }
                    consumer(key, objectBody.substring(valueStart + 1, valueEnd), false)
                    index = valueEnd + 1
                }

                else -> {
                    val valueEnd = objectBody.indexOf(',', valueStart).takeIf { it >= 0 } ?: objectBody.length
                    consumer(key, objectBody.substring(valueStart, valueEnd).trim(), false)
                    index = valueEnd + 1
                }
            }
        }
    }

    private fun extractObjectBody(
        json: String,
        key: String,
    ): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) {
            return null
        }
        val objectStart = json.indexOf('{', keyIndex)
        if (objectStart < 0) {
            return null
        }
        val objectEnd = findMatchingBracket(json, objectStart, '{', '}')
        if (objectEnd < 0) {
            return null
        }
        return json.substring(objectStart + 1, objectEnd)
    }

    private fun findStringEnd(
        text: String,
        startIndex: Int,
    ): Int {
        var escaped = false
        for (index in startIndex until text.length) {
            val character = text[index]
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> return index
            }
        }
        return -1
    }

    private fun findMatchingBracket(
        text: String,
        startIndex: Int,
        open: Char,
        close: Char,
    ): Int {
        var depth = 0
        var escaped = false
        var inString = false
        for (index in startIndex until text.length) {
            val character = text[index]
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == open -> depth += 1
                character == close -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }

    private fun isSemverLike(version: String): Boolean {
        return version.matches(Regex("^v?\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?$"))
    }
}
