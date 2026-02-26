package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.registry

import com.github.buyoung.dependencyninja.core.shared.application.EcosystemAdapter
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.core.shared.infrastructure.SimpleHttpClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NpmRegistryAdapter(
    private val httpClient: SimpleHttpClient,
) : EcosystemAdapter {
    override val supportedEcosystems: Set<Ecosystem> = setOf(Ecosystem.NPM)

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val encoded = URLEncoder.encode(coordinate.name, StandardCharsets.UTF_8)
        val body = httpClient.get("https://registry.npmjs.org/$encoded/latest") ?: return null
        val version = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}

class PyPiRegistryAdapter(
    private val httpClient: SimpleHttpClient,
) : EcosystemAdapter {
    override val supportedEcosystems: Set<Ecosystem> = setOf(Ecosystem.PYPI)

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val encoded = URLEncoder.encode(coordinate.name, StandardCharsets.UTF_8)
        val body = httpClient.get("https://pypi.org/pypi/$encoded/json") ?: return null
        val version = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}

class MavenCentralAdapter(
    private val httpClient: SimpleHttpClient,
) : EcosystemAdapter {
    override val supportedEcosystems: Set<Ecosystem> = setOf(Ecosystem.MAVEN)

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val group = coordinate.group ?: return null
        val artifact = coordinate.artifact ?: return null
        val query = "g:\"$group\" AND a:\"$artifact\""
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val body = httpClient.get("https://search.maven.org/solrsearch/select?q=$encodedQuery&rows=1&wt=json") ?: return null
        val version = "\"latestVersion\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}

class GoProxyAdapter(
    private val httpClient: SimpleHttpClient,
) : EcosystemAdapter {
    override val supportedEcosystems: Set<Ecosystem> = setOf(Ecosystem.GO)

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val encoded = coordinate.name.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8)
        }
        val body = httpClient.get("https://proxy.golang.org/$encoded/@latest") ?: return null
        val version = "\"Version\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}
