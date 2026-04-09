package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem

@Deprecated("out of v1.0 scope")
class MavenPackageManagerVersionSource : PackageManagerVersionSource() {
    override val ecosystem: Ecosystem = Ecosystem.MAVEN

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? = null
}
