package com.github.buyoung.dependencyninja.features.updateResolution.application

import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.ReasonCode
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgePolicySource
import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgeRule
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference
import com.github.buyoung.dependencyninja.features.settings.application.PolicyProfileService
import com.github.buyoung.dependencyninja.features.settings.domain.StabilityChannel
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory.OsvAdvisoryClient
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager.PackageManagerReleaseAgeReader
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant

class DependencyUpdateResolver(
    private val project: Project,
    sources: List<VersionSource>,
    private val policyProfileService: PolicyProfileService,
    private val advisoryClient: OsvAdvisoryClient,
    private val packageManagerReleaseAgeReader: PackageManagerReleaseAgeReader,
) {
    private val sourceByEcosystem = sources.associateBy { it.ecosystem }

    fun resolve(
        declarations: List<DependencyDeclaration>,
        workspaceReferences: List<WorkspaceReference>,
    ): List<RecommendationRecord> {
        val profile = policyProfileService.currentProfile()
        val workspaceReferenceById = workspaceReferences.associateBy { it.workspaceReferenceId }
        val recommendations = mutableListOf<RecommendationRecord>()

        workspaceReferences.forEach { workspaceReference ->
            recommendations += resolveWorkspaceReference(workspaceReference, profile)
        }

        declarations.forEach { declaration ->
            recommendations += resolveDeclaration(declaration, workspaceReferenceById[declaration.workspaceReferenceId], profile)
        }

        return recommendations
    }

    private fun resolveWorkspaceReference(
        workspaceReference: WorkspaceReference,
        profile: com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile,
    ): RecommendationRecord {
        val observation = sourceByEcosystem[workspaceReference.coordinate.ecosystem]
            ?.resolveRegistryObservation(workspaceReference.coordinate)
        val advisories = advisoryClient.lookup(workspaceReference.packageName).records
        return buildRecommendation(
            declarationId = workspaceReference.workspaceReferenceId,
            packageName = workspaceReference.packageName,
            currentVersion = workspaceReference.normalizedCurrentVersion ?: workspaceReference.declaredVersionText,
            declaredVersionText = workspaceReference.declaredVersionText,
            sourceManifestPath = workspaceReference.ownerManifestPath,
            moduleName = workspaceReference.ownerManifestPath.substringAfterLast('/'),
            coordinate = workspaceReference.coordinate,
            versionRange = workspaceReference.versionRange,
            targetManifestPath = workspaceReference.ownerManifestPath,
            targetVersionRange = workspaceReference.versionRange,
            targetDeclaredVersionText = workspaceReference.declaredVersionText,
            workspaceReferenceId = workspaceReference.workspaceReferenceId,
            isEditableTarget = true,
            observation = observation,
            advisories = advisories,
            profile = profile,
            extraReasons = emptySet(),
            advisorySummary = advisories.firstOrNull()?.summary,
        )
    }

    private fun resolveDeclaration(
        declaration: DependencyDeclaration,
        workspaceReference: WorkspaceReference?,
        profile: com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile,
    ): RecommendationRecord {
        val effectiveCoordinate = workspaceReference?.coordinate ?: declaration.coordinate
        val effectiveCurrentVersion = workspaceReference?.normalizedCurrentVersion ?: declaration.normalizedCurrentVersion ?: declaration.declaredVersionText
        val effectiveDeclaredText = workspaceReference?.declaredVersionText ?: declaration.declaredVersionText
        val observation = sourceByEcosystem[effectiveCoordinate.ecosystem]?.resolveRegistryObservation(effectiveCoordinate)
        val advisories = advisoryClient.lookup(declaration.packageName).records
        val extraReasons = buildSet {
            if (workspaceReference != null) {
                add(ReasonCode.SHARED_REFERENCE_TAKES_PRECEDENCE)
            }
        }
        return buildRecommendation(
            declarationId = declaration.declarationId,
            packageName = declaration.packageName,
            currentVersion = effectiveCurrentVersion,
            declaredVersionText = declaration.declaredVersionText,
            sourceManifestPath = declaration.sourceManifestPath,
            moduleName = declaration.moduleName,
            coordinate = declaration.coordinate,
            versionRange = declaration.versionRange,
            targetManifestPath = workspaceReference?.ownerManifestPath ?: declaration.sourceManifestPath,
            targetVersionRange = workspaceReference?.versionRange ?: declaration.versionRange,
            targetDeclaredVersionText = effectiveDeclaredText,
            workspaceReferenceId = declaration.workspaceReferenceId,
            isEditableTarget = declaration.isEditableTarget,
            observation = observation,
            advisories = advisories,
            profile = profile,
            extraReasons = extraReasons,
            advisorySummary = advisories.firstOrNull()?.summary,
        )
    }

    private fun buildRecommendation(
        declarationId: String,
        packageName: String,
        currentVersion: String,
        declaredVersionText: String,
        sourceManifestPath: String,
        moduleName: String,
        coordinate: com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate,
        versionRange: com.intellij.openapi.util.TextRange?,
        targetManifestPath: String,
        targetVersionRange: com.intellij.openapi.util.TextRange?,
        targetDeclaredVersionText: String,
        workspaceReferenceId: String?,
        isEditableTarget: Boolean,
        observation: com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation?,
        advisories: List<com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord>,
        profile: com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile,
        extraReasons: Set<ReasonCode>,
        advisorySummary: String?,
    ): RecommendationRecord {
        val reasons = linkedSetOf<ReasonCode>().apply { addAll(extraReasons) }
        val effectiveObservation = observation ?: com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation(
            packageName = packageName,
            registryUrl = "",
            availableVersions = emptyList(),
            fetchedAt = null,
            freshnessState = FreshnessState.UNAVAILABLE,
        )

        if (profile.ignoredPackages.contains(packageName)) {
            reasons += ReasonCode.IGNORED
            return recommendation(
                declarationId = declarationId,
                workspaceReferenceId = workspaceReferenceId,
                coordinate = coordinate,
                packageName = packageName,
                sourceManifestPath = sourceManifestPath,
                moduleName = moduleName,
                declaredVersionText = declaredVersionText,
                currentVersion = currentVersion,
                recommendedVersion = null,
                status = DependencyStatus.IGNORED,
                reasonCodes = reasons,
                freshnessState = effectiveObservation.freshnessState,
                isEditableTarget = isEditableTarget,
                versionRange = versionRange,
                targetManifestPath = targetManifestPath,
                targetVersionRange = targetVersionRange,
                targetDeclaredVersionText = targetDeclaredVersionText,
                advisorySummary = advisorySummary,
                surfaceAvailability = profile.presentationToggles,
            )
        }

        if (effectiveObservation.freshnessState == FreshnessState.UNAVAILABLE) {
            reasons += ReasonCode.VERIFICATION_UNAVAILABLE
            return recommendation(
                declarationId = declarationId,
                workspaceReferenceId = workspaceReferenceId,
                coordinate = coordinate,
                packageName = packageName,
                sourceManifestPath = sourceManifestPath,
                moduleName = moduleName,
                declaredVersionText = declaredVersionText,
                currentVersion = currentVersion,
                recommendedVersion = null,
                status = DependencyStatus.VERIFICATION_UNAVAILABLE,
                reasonCodes = reasons,
                freshnessState = effectiveObservation.freshnessState,
                isEditableTarget = isEditableTarget,
                versionRange = versionRange,
                targetManifestPath = targetManifestPath,
                targetVersionRange = targetVersionRange,
                targetDeclaredVersionText = targetDeclaredVersionText,
                advisorySummary = advisorySummary,
                surfaceAvailability = profile.presentationToggles,
            )
        }

        val releaseAgeRule = ReleaseAgeRule(
            sourceSelector = profile.releaseAgePolicySource,
            pluginDefaultDays = profile.minimumReleaseAgePluginDefaultDays,
            exclusions = profile.minimumReleaseAgeExclusions,
        )
        val minimumAgeDays = if (profile.releaseAgePolicySource == ReleaseAgePolicySource.PACKAGE_MANAGER) {
            releaseAgeRule.resolveMinimumAgeDays(packageManagerReleaseAgeReader.read(project, sourceManifestPath))
        } else {
            releaseAgeRule.resolveMinimumAgeDays(null)
        }

        val eligibleVersions = effectiveObservation.availableVersions.filter { candidateVersion ->
            val prereleaseAllowed = profile.allowedChannels.contains(StabilityChannel.PRERELEASE) || !candidateVersion.contains('-')
            if (!prereleaseAllowed) {
                reasons += ReasonCode.STABILITY_BLOCKED
            }

            val ageAllowed = releaseAgeRule.isExcluded(packageName) || candidateIsOldEnough(
                candidateVersion = candidateVersion,
                releaseTimestamps = effectiveObservation.releaseTimestamps,
                minimumAgeDays = minimumAgeDays,
            )
            if (!ageAllowed) {
                reasons += ReasonCode.RELEASE_AGE_BLOCKED
            }
            prereleaseAllowed && ageAllowed
        }

        val recommendedVersion = eligibleVersions.firstOrNull()
        val advisoryFlagged = advisories.isNotEmpty()
        if (advisoryFlagged) {
            reasons += ReasonCode.ADVISORY_FLAGGED
        }

        val freshnessAdjustedStatus = when (effectiveObservation.freshnessState) {
            FreshnessState.STALE -> {
                reasons += ReasonCode.STALE_METADATA
                DependencyStatus.STALE
            }

            FreshnessState.UNAVAILABLE -> {
                reasons += ReasonCode.VERIFICATION_UNAVAILABLE
                DependencyStatus.VERIFICATION_UNAVAILABLE
            }

            FreshnessState.FRESH -> null
        }
        if (freshnessAdjustedStatus != null) {
            return recommendation(
                declarationId = declarationId,
                workspaceReferenceId = workspaceReferenceId,
                coordinate = coordinate,
                packageName = packageName,
                sourceManifestPath = sourceManifestPath,
                moduleName = moduleName,
                declaredVersionText = declaredVersionText,
                currentVersion = currentVersion,
                recommendedVersion = recommendedVersion,
                status = freshnessAdjustedStatus,
                reasonCodes = reasons,
                freshnessState = effectiveObservation.freshnessState,
                isEditableTarget = isEditableTarget,
                versionRange = versionRange,
                targetManifestPath = targetManifestPath,
                targetVersionRange = targetVersionRange,
                targetDeclaredVersionText = targetDeclaredVersionText,
                advisorySummary = advisorySummary,
                surfaceAvailability = profile.presentationToggles,
            )
        }

        val status = when {
            advisoryFlagged -> DependencyStatus.RISKY
            recommendedVersion == null -> DependencyStatus.BLOCKED
            VersionComparator.compare(currentVersion, recommendedVersion) < 0 -> DependencyStatus.OUTDATED
            else -> DependencyStatus.UP_TO_DATE
        }
        when (status) {
            DependencyStatus.OUTDATED -> reasons += ReasonCode.UPDATE_AVAILABLE
            DependencyStatus.UP_TO_DATE -> reasons += ReasonCode.UP_TO_DATE
            else -> Unit
        }

        return recommendation(
            declarationId = declarationId,
            workspaceReferenceId = workspaceReferenceId,
            coordinate = coordinate,
            packageName = packageName,
            sourceManifestPath = sourceManifestPath,
            moduleName = moduleName,
            declaredVersionText = declaredVersionText,
            currentVersion = currentVersion,
            recommendedVersion = recommendedVersion,
            status = status,
            reasonCodes = reasons,
            freshnessState = effectiveObservation.freshnessState,
            isEditableTarget = isEditableTarget,
            versionRange = versionRange,
            targetManifestPath = targetManifestPath,
            targetVersionRange = targetVersionRange,
            targetDeclaredVersionText = targetDeclaredVersionText,
            advisorySummary = advisorySummary,
            surfaceAvailability = profile.presentationToggles,
        )
    }

    private fun candidateIsOldEnough(
        candidateVersion: String,
        releaseTimestamps: Map<String, Instant>,
        minimumAgeDays: Int,
    ): Boolean {
        val releasedAt = releaseTimestamps[candidateVersion] ?: return true
        return Duration.between(releasedAt, Instant.now()).toDays() >= minimumAgeDays.toLong()
    }

    private fun recommendation(
        declarationId: String,
        workspaceReferenceId: String?,
        coordinate: com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate,
        packageName: String,
        sourceManifestPath: String,
        moduleName: String,
        declaredVersionText: String,
        currentVersion: String,
        recommendedVersion: String?,
        status: DependencyStatus,
        reasonCodes: Set<ReasonCode>,
        freshnessState: FreshnessState,
        isEditableTarget: Boolean,
        versionRange: com.intellij.openapi.util.TextRange?,
        targetManifestPath: String,
        targetVersionRange: com.intellij.openapi.util.TextRange?,
        targetDeclaredVersionText: String,
        advisorySummary: String?,
        surfaceAvailability: com.github.buyoung.dependencyninja.core.shared.domain.SurfaceAvailability,
    ): RecommendationRecord {
        return RecommendationRecord(
            recommendationId = "$sourceManifestPath::$declarationId",
            declarationId = declarationId,
            workspaceReferenceId = workspaceReferenceId,
            coordinate = coordinate,
            packageName = packageName,
            sourceManifestPath = sourceManifestPath,
            moduleName = moduleName,
            declaredVersionText = declaredVersionText,
            currentVersion = currentVersion,
            recommendedVersion = recommendedVersion,
            status = status,
            reasonCodes = reasonCodes,
            freshnessState = freshnessState,
            surfaceAvailability = surfaceAvailability,
            isEditableTarget = isEditableTarget,
            versionRange = versionRange,
            targetManifestPath = targetManifestPath,
            targetVersionRange = targetVersionRange,
            targetDeclaredVersionText = targetDeclaredVersionText,
            advisorySummary = advisorySummary,
            updateType = if (recommendedVersion == null) {
                com.github.buyoung.dependencyninja.core.shared.domain.UpdateType.UNKNOWN
            } else {
                VersionComparator.classifyUpdate(currentVersion, recommendedVersion)
            },
        )
    }
}
