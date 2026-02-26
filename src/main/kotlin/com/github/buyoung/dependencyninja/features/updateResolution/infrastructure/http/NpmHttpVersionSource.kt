package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NpmHttpVersionSource(
    private val httpClient: HttpClient,
) : VersionSource {
    override val ecosystem: Ecosystem = Ecosystem.NPM
    override val channel: LookupChannel = LookupChannel.HTTP_REGISTRY

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val encoded = URLEncoder.encode(coordinate.name, StandardCharsets.UTF_8)
        val body = httpClient.get("https://registry.npmjs.org/$encoded/latest") ?: return null
        val version = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}
