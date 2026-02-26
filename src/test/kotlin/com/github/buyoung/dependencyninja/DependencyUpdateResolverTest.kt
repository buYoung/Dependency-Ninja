package com.github.buyoung.dependencyninja

import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DeclaredDependency
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.features.updateResolution.application.DependencyUpdateResolver
import junit.framework.TestCase.assertEquals
import org.junit.Test

class DependencyUpdateResolverTest {

    @Test
    fun resolve_usesConfiguredChannelPerEcosystem() {
        val resolver = DependencyUpdateResolver(
            sources = listOf(
                FakeVersionSource(Ecosystem.NPM, LookupChannel.HTTP_REGISTRY, "1.1.0"),
                FakeVersionSource(Ecosystem.NPM, LookupChannel.PACKAGE_MANAGER, "2.0.0"),
            ),
            defaultChannelByEcosystem = mapOf(Ecosystem.NPM to LookupChannel.HTTP_REGISTRY),
        )

        val updates = resolver.resolve(
            listOf(
                DeclaredDependency(
                    coordinate = DependencyCoordinate(Ecosystem.NPM, "react"),
                    currentVersion = "1.0.0",
                    manifestPath = "/repo/package.json",
                    moduleName = "repo",
                    versionRange = null,
                ),
            ),
        )

        assertEquals(DependencyStatus.OUTDATED, updates.single().status)
        assertEquals("1.1.0", updates.single().latestVersion)
    }

    @Test
    fun resolve_returnsUnknownWhenNoSourceForConfiguredChannel() {
        val resolver = DependencyUpdateResolver(
            sources = listOf(FakeVersionSource(Ecosystem.NPM, LookupChannel.PACKAGE_MANAGER, "2.0.0")),
            defaultChannelByEcosystem = mapOf(Ecosystem.NPM to LookupChannel.HTTP_REGISTRY),
        )

        val updates = resolver.resolve(
            listOf(
                DeclaredDependency(
                    coordinate = DependencyCoordinate(Ecosystem.NPM, "react"),
                    currentVersion = "1.0.0",
                    manifestPath = "/repo/package.json",
                    moduleName = "repo",
                    versionRange = null,
                ),
            ),
        )

        assertEquals(DependencyStatus.UNKNOWN, updates.single().status)
        assertEquals(null, updates.single().latestVersion)
    }

    private data class FakeVersionSource(
        override val ecosystem: Ecosystem,
        override val channel: LookupChannel,
        private val latest: String?,
    ) : VersionSource {
        override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? = latest
    }
}
