package com.github.buyoung.dependencyninja.features.updateResolution.application

import com.github.buyoung.dependencyninja.core.shared.application.EcosystemAdapter
import com.github.buyoung.dependencyninja.core.shared.domain.DeclaredDependency
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyUpdate
import com.github.buyoung.dependencyninja.core.shared.domain.UpdateType
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator

class DependencyUpdateResolver(
    adapters: List<EcosystemAdapter>,
) {
    private val adapterByEcosystem = adapters.flatMap { adapter ->
        adapter.supportedEcosystems.map { ecosystem -> ecosystem to adapter }
    }.toMap()

    fun resolve(dependencies: List<DeclaredDependency>): List<DependencyUpdate> {
        return dependencies.map { dependency ->
            val adapter = adapterByEcosystem[dependency.coordinate.ecosystem]
            val latest = adapter?.resolveLatestVersion(dependency.coordinate)

            if (latest == null) {
                return@map DependencyUpdate(
                    declared = dependency,
                    latestVersion = null,
                    status = DependencyStatus.UNKNOWN,
                    updateType = UpdateType.UNKNOWN,
                )
            }

            val compare = VersionComparator.compare(dependency.currentVersion, latest)
            if (compare < 0) {
                DependencyUpdate(
                    declared = dependency,
                    latestVersion = latest,
                    status = DependencyStatus.OUTDATED,
                    updateType = VersionComparator.classifyUpdate(dependency.currentVersion, latest),
                )
            } else {
                DependencyUpdate(
                    declared = dependency,
                    latestVersion = latest,
                    status = DependencyStatus.UP_TO_DATE,
                    updateType = UpdateType.UNKNOWN,
                )
            }
        }
    }
}
