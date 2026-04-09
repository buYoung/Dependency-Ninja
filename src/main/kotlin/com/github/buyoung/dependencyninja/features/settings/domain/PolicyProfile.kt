package com.github.buyoung.dependencyninja.features.settings.domain

import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgePolicySource
import com.github.buyoung.dependencyninja.core.shared.domain.SurfaceAvailability

enum class StabilityChannel {
    STABLE,
    PRERELEASE,
}

enum class CoexistenceMode {
    COEXIST,
    REPLACE,
}

enum class UpdateStrategy {
    MANIFEST_ONLY,
    PACKAGE_MANAGER_EXECUTION,
}

data class PolicyProfile(
    val allowedChannels: Set<StabilityChannel> = setOf(StabilityChannel.STABLE),
    val releaseAgePolicySource: ReleaseAgePolicySource = ReleaseAgePolicySource.PACKAGE_MANAGER,
    val minimumReleaseAgePluginDefaultDays: Int = 7,
    val minimumReleaseAgeExclusions: Set<String> = emptySet(),
    val ignoredPackages: Set<String> = emptySet(),
    val coexistenceMode: CoexistenceMode = CoexistenceMode.COEXIST,
    val updateStrategy: UpdateStrategy = UpdateStrategy.MANIFEST_ONLY,
    val presentationToggles: SurfaceAvailability = SurfaceAvailability(),
    val bulkApplySoftCap: Int = 25,
)
