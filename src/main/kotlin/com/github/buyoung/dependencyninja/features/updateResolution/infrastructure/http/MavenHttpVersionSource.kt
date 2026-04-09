package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Deprecated("out of v1.0 scope")
class MavenHttpVersionSource(
    private val httpClient: HttpClient,
) : VersionSource {
    override val ecosystem: Ecosystem = Ecosystem.MAVEN
    override val channel: LookupChannel = LookupChannel.HTTP_REGISTRY

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
