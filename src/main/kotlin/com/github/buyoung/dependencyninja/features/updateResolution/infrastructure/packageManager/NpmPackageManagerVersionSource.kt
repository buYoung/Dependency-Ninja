package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem

class NpmPackageManagerVersionSource : PackageManagerVersionSource() {
    override val ecosystem: Ecosystem = Ecosystem.NPM

    override fun resolveLatestVersion(coordinate: DependencyCoordinate): String? = null
}
