package com.github.buyoung.dependencyninja.features.settings.presentation

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgePolicySource
import com.github.buyoung.dependencyninja.core.shared.domain.SurfaceAvailability
import com.github.buyoung.dependencyninja.features.settings.application.PolicyProfileService
import com.github.buyoung.dependencyninja.features.settings.domain.CoexistenceMode
import com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile
import com.github.buyoung.dependencyninja.features.settings.domain.StabilityChannel
import com.github.buyoung.dependencyninja.features.settings.domain.UpdateStrategy
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import java.awt.GridLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class DependencyNinjaConfigurable(
    private val project: Project,
) : SearchableConfigurable {
    private val profileService = project.service<PolicyProfileService>()
    private var settingsComponent: SettingsComponent? = null

    override fun getId(): String = "dependency.ninja.settings"

    override fun getDisplayName(): String = DependencyNinjaBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        return SettingsComponent().also {
            settingsComponent = it
            reset()
        }.panel
    }

    override fun isModified(): Boolean {
        val component = settingsComponent ?: return false
        return component.toProfile() != profileService.currentProfile()
    }

    override fun apply() {
        val component = settingsComponent ?: return
        profileService.updateProfile(component.toProfile())
    }

    override fun reset() {
        settingsComponent?.load(profileService.currentProfile())
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }

    private class SettingsComponent {
        val inlineHintsCheckBox = JBCheckBox(DependencyNinjaBundle.message("settings.inlineHints"))
        val toolWindowCheckBox = JBCheckBox(DependencyNinjaBundle.message("settings.toolWindow"))
        val inspectionCheckBox = JBCheckBox(DependencyNinjaBundle.message("settings.inspection"))
        val prereleaseCheckBox = JBCheckBox(DependencyNinjaBundle.message("settings.allowPrerelease"))
        val ignoredPackagesField = JBTextField()
        val releaseAgeSourceCombo = JComboBox(ReleaseAgePolicySource.entries.toTypedArray())
        val releaseAgeDaysField = JBTextField()
        val coexistenceCombo = JComboBox(CoexistenceMode.entries.toTypedArray())
        val updateStrategyCombo = JComboBox(UpdateStrategy.entries.toTypedArray())
        val bulkSoftCapField = JBTextField()
        val panel = JPanel(GridLayout(0, 2, 8, 8))

        init {
            panel.add(inlineHintsCheckBox)
            panel.add(toolWindowCheckBox)
            panel.add(inspectionCheckBox)
            panel.add(prereleaseCheckBox)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.ignoredPackages")))
            panel.add(ignoredPackagesField)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.releaseAgeSource")))
            panel.add(releaseAgeSourceCombo)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.releaseAgeDays")))
            panel.add(releaseAgeDaysField)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.coexistenceMode")))
            panel.add(coexistenceCombo)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.updateStrategy")))
            panel.add(updateStrategyCombo)
            panel.add(JLabel(DependencyNinjaBundle.message("settings.bulkSoftCap")))
            panel.add(bulkSoftCapField)
        }

        fun load(profile: PolicyProfile) {
            inlineHintsCheckBox.isSelected = profile.presentationToggles.inlineHints
            toolWindowCheckBox.isSelected = profile.presentationToggles.toolWindow
            inspectionCheckBox.isSelected = profile.presentationToggles.inspection
            prereleaseCheckBox.isSelected = profile.allowedChannels.contains(StabilityChannel.PRERELEASE)
            ignoredPackagesField.text = profile.ignoredPackages.joinToString(", ")
            releaseAgeSourceCombo.selectedItem = profile.releaseAgePolicySource
            releaseAgeDaysField.text = profile.minimumReleaseAgePluginDefaultDays.toString()
            coexistenceCombo.selectedItem = profile.coexistenceMode
            updateStrategyCombo.selectedItem = profile.updateStrategy
            bulkSoftCapField.text = profile.bulkApplySoftCap.toString()
        }

        fun toProfile(): PolicyProfile {
            val channels = linkedSetOf(StabilityChannel.STABLE).apply {
                if (prereleaseCheckBox.isSelected) {
                    add(StabilityChannel.PRERELEASE)
                }
            }
            return PolicyProfile(
                allowedChannels = channels,
                releaseAgePolicySource = releaseAgeSourceCombo.selectedItem as? ReleaseAgePolicySource
                    ?: ReleaseAgePolicySource.PACKAGE_MANAGER,
                minimumReleaseAgePluginDefaultDays = releaseAgeDaysField.text.toIntOrNull() ?: 7,
                ignoredPackages = ignoredPackagesField.text
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                coexistenceMode = coexistenceCombo.selectedItem as? CoexistenceMode ?: CoexistenceMode.COEXIST,
                updateStrategy = updateStrategyCombo.selectedItem as? UpdateStrategy ?: UpdateStrategy.MANIFEST_ONLY,
                presentationToggles = SurfaceAvailability(
                    inlineHints = inlineHintsCheckBox.isSelected,
                    toolWindow = toolWindowCheckBox.isSelected,
                    inspection = inspectionCheckBox.isSelected,
                ),
                bulkApplySoftCap = bulkSoftCapField.text.toIntOrNull() ?: 25,
            )
        }
    }
}
