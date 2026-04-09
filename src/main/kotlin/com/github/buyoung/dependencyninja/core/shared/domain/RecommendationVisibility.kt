package com.github.buyoung.dependencyninja.core.shared.domain

private val toolWindowStatuses = setOf(
    DependencyStatus.OUTDATED,
    DependencyStatus.RISKY,
    DependencyStatus.BLOCKED,
    DependencyStatus.STALE,
    DependencyStatus.VERIFICATION_UNAVAILABLE,
)

private val inlineHintStatuses = setOf(
    DependencyStatus.OUTDATED,
    DependencyStatus.RISKY,
    DependencyStatus.STALE,
    DependencyStatus.VERIFICATION_UNAVAILABLE,
)

private val inspectionStatuses = setOf(
    DependencyStatus.OUTDATED,
    DependencyStatus.RISKY,
)

fun RecommendationRecord.hasRecommendedUpgrade(): Boolean {
    val recommendedVersion = recommendedVersion ?: return false
    return VersionComparator.compare(currentVersion, recommendedVersion) < 0
}

fun RecommendationRecord.supportsUpdateAction(): Boolean {
    return hasRecommendedUpgrade() && status in setOf(
        DependencyStatus.OUTDATED,
        DependencyStatus.RISKY,
        DependencyStatus.STALE,
    )
}

fun RecommendationRecord.shouldAppearInToolWindowByDefault(): Boolean {
    return surfaceAvailability.toolWindow && status in toolWindowStatuses
}

fun RecommendationRecord.shouldShowInlineHint(): Boolean {
    return surfaceAvailability.inlineHints && status in inlineHintStatuses
}

fun RecommendationRecord.shouldAppearInInspection(): Boolean {
    return surfaceAvailability.inspection && status in inspectionStatuses
}

fun RecommendationRecord.visibleReasonCodes(): Set<ReasonCode> {
    return reasonCodes - setOf(ReasonCode.UPDATE_AVAILABLE, ReasonCode.UP_TO_DATE)
}
