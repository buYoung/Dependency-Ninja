package com.github.buyoung.dependencyninja.core.shared.application

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem

interface EcosystemAdapter {
    val supportedEcosystems: Set<Ecosystem>
    fun resolveLatestVersion(coordinate: DependencyCoordinate): String?
}
