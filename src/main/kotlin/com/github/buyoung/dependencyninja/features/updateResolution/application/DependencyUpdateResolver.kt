package com.github.buyoung.dependencyninja.features.updateResolution.application

import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DeclaredDependency
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyUpdate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.domain.UpdateType
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator

class DependencyUpdateResolver(
    sources: List<VersionSource>,
    private val defaultChannelByEcosystem: Map<Ecosystem, LookupChannel>,
) {
    private val sourceByKey = sources.associateBy { it.ecosystem to it.channel }

    fun resolve(dependencies: List<DeclaredDependency>): List<DependencyUpdate> {
        return dependencies.map { dependency ->
            val channel = defaultChannelByEcosystem[dependency.coordinate.ecosystem]
            val source = channel?.let { sourceByKey[dependency.coordinate.ecosystem to it] }
            val latest = source?.resolveLatestVersion(dependency.coordinate)

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
