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
class GoHttpVersionSource(
    private val httpClient: HttpClient,
) : VersionSource {
    override val ecosystem: Ecosystem = Ecosystem.GO
    override val channel: LookupChannel = LookupChannel.HTTP_REGISTRY

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? {
        val encoded = coordinate.name.split('/').joinToString("/") {
            URLEncoder.encode(it, StandardCharsets.UTF_8)
        }
        val body = httpClient.get("https://proxy.golang.org/$encoded/@latest") ?: return null
        val version = "\"Version\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(body)?.groups?.get(1)?.value
        return version?.let(VersionComparator::normalize)
    }
}
