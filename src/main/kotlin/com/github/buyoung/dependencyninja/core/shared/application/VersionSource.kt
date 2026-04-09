package com.github.buyoung.dependencyninja.core.shared.application

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation

interface VersionSource {
    val ecosystem: Ecosystem
    val channel: LookupChannel

    fun resolveRegistryObservation(coordinate: DependencyCoordinate): RegistryObservation {
        val latestVersion = resolveLatestVersion(coordinate)
        return RegistryObservation(
            packageName = coordinate.name,
            registryUrl = "",
            availableVersions = latestVersion?.let(::listOf) ?: emptyList(),
            fetchedAt = null,
            freshnessState = if (latestVersion == null) FreshnessState.UNAVAILABLE else FreshnessState.FRESH,
        )
    }

    fun resolveLatestVersion(coordinate: DependencyCoordinate): String?
}
