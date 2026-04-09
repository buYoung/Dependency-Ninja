package com.github.buyoung.dependencyninja.core.shared.domain

enum class ReleaseAgePolicySource {
    PACKAGE_MANAGER,
    PLUGIN_SETTINGS,
}

data class ReleaseAgeRule(
    val sourceSelector: ReleaseAgePolicySource = ReleaseAgePolicySource.PACKAGE_MANAGER,
    val pluginDefaultDays: Int = 7,
    val exclusions: Set<String> = emptySet(),
) {
    fun resolveMinimumAgeDays(packageManagerDays: Int?): Int {
        return when (sourceSelector) {
            ReleaseAgePolicySource.PACKAGE_MANAGER -> packageManagerDays ?: pluginDefaultDays
            ReleaseAgePolicySource.PLUGIN_SETTINGS -> pluginDefaultDays
        }
    }

    fun isExcluded(packageName: String): Boolean = exclusions.contains(packageName)
}
