package com.github.buyoung.dependencyninja.features.settings.application

import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgePolicySource
import com.github.buyoung.dependencyninja.core.shared.domain.SurfaceAvailability
import com.github.buyoung.dependencyninja.features.settings.domain.CoexistenceMode
import com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile
import com.github.buyoung.dependencyninja.features.settings.domain.StabilityChannel
import com.github.buyoung.dependencyninja.features.settings.domain.UpdateStrategy
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(name = "DependencyNinjaPolicyProfile", storages = [Storage("dependency-ninja.xml")])
class PolicyProfileService : PersistentStateComponent<PolicyProfileService.State> {
    private var state = State()

    fun currentProfile(): PolicyProfile {
        return PolicyProfile(
            allowedChannels = state.allowedChannels.ifEmpty { setOf(StabilityChannel.STABLE.name) }.mapTo(linkedSetOf()) {
                StabilityChannel.valueOf(it)
            },
            releaseAgePolicySource = ReleaseAgePolicySource.valueOf(state.releaseAgePolicySource),
            minimumReleaseAgePluginDefaultDays = state.minimumReleaseAgePluginDefaultDays,
            minimumReleaseAgeExclusions = state.minimumReleaseAgeExclusions.toSet(),
            ignoredPackages = state.ignoredPackages.toSet(),
            coexistenceMode = CoexistenceMode.valueOf(state.coexistenceMode),
            updateStrategy = UpdateStrategy.valueOf(state.updateStrategy),
            presentationToggles = SurfaceAvailability(
                inlineHints = state.inlineHintsEnabled,
                toolWindow = state.toolWindowEnabled,
                inspection = state.inspectionEnabled,
            ),
            bulkApplySoftCap = state.bulkApplySoftCap,
        )
    }

    fun updateProfile(profile: PolicyProfile) {
        state = State(
            allowedChannels = profile.allowedChannels.mapTo(linkedSetOf()) { it.name },
            releaseAgePolicySource = profile.releaseAgePolicySource.name,
            minimumReleaseAgePluginDefaultDays = profile.minimumReleaseAgePluginDefaultDays,
            minimumReleaseAgeExclusions = profile.minimumReleaseAgeExclusions.toMutableList(),
            ignoredPackages = profile.ignoredPackages.toMutableList(),
            coexistenceMode = profile.coexistenceMode.name,
            updateStrategy = profile.updateStrategy.name,
            inlineHintsEnabled = profile.presentationToggles.inlineHints,
            toolWindowEnabled = profile.presentationToggles.toolWindow,
            inspectionEnabled = profile.presentationToggles.inspection,
            bulkApplySoftCap = profile.bulkApplySoftCap,
        )
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    data class State(
        var allowedChannels: MutableSet<String> = linkedSetOf(StabilityChannel.STABLE.name),
        var releaseAgePolicySource: String = ReleaseAgePolicySource.PACKAGE_MANAGER.name,
        var minimumReleaseAgePluginDefaultDays: Int = 7,
        var minimumReleaseAgeExclusions: MutableList<String> = mutableListOf(),
        var ignoredPackages: MutableList<String> = mutableListOf(),
        var coexistenceMode: String = CoexistenceMode.COEXIST.name,
        var updateStrategy: String = UpdateStrategy.MANIFEST_ONLY.name,
        var inlineHintsEnabled: Boolean = true,
        var toolWindowEnabled: Boolean = true,
        var inspectionEnabled: Boolean = true,
        var bulkApplySoftCap: Int = 25,
    )
}
