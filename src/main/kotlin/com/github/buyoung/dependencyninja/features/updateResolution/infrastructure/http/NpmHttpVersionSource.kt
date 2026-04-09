package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
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
            val versionPattern = Regex("\"([0-9][^\"]*)\"\\s*:")
            val releaseTimePattern = Regex("\"([0-9][^\"]*)\"\\s*:\\s*\"([^\"]+)\"")
            val availableVersions = versionPattern.findAll(body)
                .map { it.groupValues[1] }
                .distinct()
                .sortedWith { left, right -> VersionComparator.compare(right, left) }
                .toList()
            val releaseTimestamps = releaseTimePattern.findAll(body).associate { matchResult ->
                val version = matchResult.groupValues[1]
                val instant = runCatching { Instant.parse(matchResult.groupValues[2]) }.getOrNull()
                version to instant
            }.filterValues { it != null }.mapValues { it.value!! }

            RegistryObservation(
                packageName = coordinate.name,
                registryUrl = registryUrl,
                availableVersions = availableVersions,
                fetchedAt = Instant.now(),
                freshnessState = com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState.FRESH,
                releaseTimestamps = releaseTimestamps,
            )
        }
    }

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        return resolveRegistryObservation(coordinate).availableVersions.firstOrNull()
    }
}
