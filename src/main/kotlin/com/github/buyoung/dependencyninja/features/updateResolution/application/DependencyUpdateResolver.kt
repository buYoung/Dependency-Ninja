package com.github.buyoung.dependencyninja.features.updateResolution.application

import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.ReasonCode
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgePolicySource
import com.github.buyoung.dependencyninja.core.shared.domain.ReleaseAgeRule
import com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation
import com.github.buyoung.dependencyninja.core.shared.domain.SurfaceAvailability
import com.github.buyoung.dependencyninja.core.shared.domain.UpdateType
import com.github.buyoung.dependencyninja.core.shared.domain.VersionComparator
import com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference
import com.github.buyoung.dependencyninja.core.shared.infrastructure.BackgroundExecution
import com.github.buyoung.dependencyninja.features.settings.application.PolicyProfileService
import com.github.buyoung.dependencyninja.features.settings.domain.PolicyProfile
import com.github.buyoung.dependencyninja.features.settings.domain.StabilityChannel
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory.OsvAdvisoryClient
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager.PackageManagerReleaseAgeReader
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Semaphore

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
        val packageInsights = loadPackageInsights(
            coordinates = (workspaceReferences.map { it.coordinate } + declarations.map { it.coordinate })
                .distinctBy(::packageKey),
        )
        val recommendations = mutableListOf<RecommendationRecord>()

        workspaceReferences.forEach { workspaceReference ->
            recommendations += resolveWorkspaceReference(
                workspaceReference = workspaceReference,
                packageInsight = packageInsights[packageKey(workspaceReference.coordinate)] ?: PackageInsight.empty(workspaceReference.coordinate.name),
                profile = profile,
            )
        }

        declarations.forEach { declaration ->
            val workspaceReference = workspaceReferenceById[declaration.workspaceReferenceId]
            val effectiveCoordinate = workspaceReference?.coordinate ?: declaration.coordinate
            recommendations += resolveDeclaration(
                declaration = declaration,
                workspaceReference = workspaceReference,
                packageInsight = packageInsights[packageKey(effectiveCoordinate)] ?: PackageInsight.empty(declaration.packageName),
                profile = profile,
            )
        }

        return recommendations
    }

    private fun loadPackageInsights(
        coordinates: List<DependencyCoordinate>,
    ): Map<String, PackageInsight> {
        val semaphore = Semaphore(MAX_CONCURRENT_PACKAGE_LOOKUPS)
        val futuresByPackageKey = coordinates.associateBy(::packageKey).mapValues { (_, coordinate) ->
            BackgroundExecution.submit {
                semaphore.acquire()
                try {
                    val observation = sourceByEcosystem[coordinate.ecosystem]?.resolveRegistryObservation(coordinate)
                    val advisoryLookup = if (coordinate.ecosystem == com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem.NPM) {
                        advisoryClient.lookup(coordinate.name)
                    } else {
                        com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory.AdvisoryLookupResult(
                            records = emptyList(),
                            freshnessState = FreshnessState.UNAVAILABLE,
                        )
                    }
                    PackageInsight(
                        observation = observation,
                        advisories = advisoryLookup.records,
                        advisoryFreshnessState = advisoryLookup.freshnessState,
                    )
                } finally {
                    semaphore.release()
                }
            }
        }

        return futuresByPackageKey.mapValues { (_, future) -> future.get() }
    }

    private fun resolveWorkspaceReference(
        workspaceReference: WorkspaceReference,
        packageInsight: PackageInsight,
        profile: PolicyProfile,
    ): RecommendationRecord {
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
            packageInsight = packageInsight,
            profile = profile,
            extraReasons = emptySet(),
        )
    }

    private fun resolveDeclaration(
        declaration: DependencyDeclaration,
        workspaceReference: WorkspaceReference?,
        packageInsight: PackageInsight,
        profile: PolicyProfile,
    ): RecommendationRecord {
        val effectiveCurrentVersion = workspaceReference?.normalizedCurrentVersion
            ?: declaration.normalizedCurrentVersion
            ?: declaration.declaredVersionText
        val effectiveDeclaredText = workspaceReference?.declaredVersionText ?: declaration.declaredVersionText
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
            packageInsight = packageInsight,
            profile = profile,
            extraReasons = extraReasons,
        )
    }

    private fun buildRecommendation(
        declarationId: String,
        packageName: String,
        currentVersion: String,
        declaredVersionText: String,
        sourceManifestPath: String,
        moduleName: String,
        coordinate: DependencyCoordinate,
        versionRange: com.intellij.openapi.util.TextRange?,
        targetManifestPath: String,
        targetVersionRange: com.intellij.openapi.util.TextRange?,
        targetDeclaredVersionText: String,
        workspaceReferenceId: String?,
        isEditableTarget: Boolean,
        packageInsight: PackageInsight,
        profile: PolicyProfile,
        extraReasons: Set<ReasonCode>,
    ): RecommendationRecord {
        val reasons = linkedSetOf<ReasonCode>().apply { addAll(extraReasons) }
        val effectiveObservation = packageInsight.observation ?: RegistryObservation(
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
                advisories = packageInsight.advisories,
                advisorySummary = packageInsight.summary,
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
                advisories = packageInsight.advisories,
                advisorySummary = packageInsight.summary,
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

        val newerVersionPolicyChecks = effectiveObservation.availableVersions
            .filter { candidateVersion -> VersionComparator.compare(currentVersion, candidateVersion) < 0 }
            .map { candidateVersion ->
                val prereleaseAllowed = profile.allowedChannels.contains(StabilityChannel.PRERELEASE) || !candidateVersion.contains('-')
                val ageAllowed = releaseAgeRule.isExcluded(packageName) || candidateIsOldEnough(
                    candidateVersion = candidateVersion,
                    releaseTimestamps = effectiveObservation.releaseTimestamps,
                    minimumAgeDays = minimumAgeDays,
                )
                CandidatePolicyCheck(
                    version = candidateVersion,
                    prereleaseAllowed = prereleaseAllowed,
                    ageAllowed = ageAllowed,
                )
            }

        val recommendedVersion = newerVersionPolicyChecks.firstOrNull { it.isAllowed }?.version
        val blockedReasons = buildSet {
            if (newerVersionPolicyChecks.any { !it.prereleaseAllowed }) {
                add(ReasonCode.STABILITY_BLOCKED)
            }
            if (newerVersionPolicyChecks.any { !it.ageAllowed }) {
                add(ReasonCode.RELEASE_AGE_BLOCKED)
            }
        }
        val advisoryFlagged = packageInsight.advisories.isNotEmpty()
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
            if (recommendedVersion != null) {
                reasons += ReasonCode.UPDATE_AVAILABLE
            } else if (newerVersionPolicyChecks.isNotEmpty()) {
                reasons += blockedReasons
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
                status = freshnessAdjustedStatus,
                reasonCodes = reasons,
                freshnessState = effectiveObservation.freshnessState,
                isEditableTarget = isEditableTarget,
                versionRange = versionRange,
                targetManifestPath = targetManifestPath,
                targetVersionRange = targetVersionRange,
                targetDeclaredVersionText = targetDeclaredVersionText,
                advisories = packageInsight.advisories,
                advisorySummary = packageInsight.summary,
                surfaceAvailability = profile.presentationToggles,
            )
        }

        val status = when {
            advisoryFlagged -> DependencyStatus.RISKY
            recommendedVersion != null -> DependencyStatus.OUTDATED
            newerVersionPolicyChecks.isNotEmpty() -> DependencyStatus.BLOCKED
            else -> DependencyStatus.UP_TO_DATE
        }
        when (status) {
            DependencyStatus.OUTDATED -> reasons += ReasonCode.UPDATE_AVAILABLE
            DependencyStatus.UP_TO_DATE -> reasons += ReasonCode.UP_TO_DATE
            DependencyStatus.BLOCKED -> reasons += blockedReasons
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
            advisories = packageInsight.advisories,
            advisorySummary = packageInsight.summary,
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
        coordinate: DependencyCoordinate,
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
        advisories: List<AdvisoryRecord>,
        advisorySummary: String?,
        surfaceAvailability: SurfaceAvailability,
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
            advisories = advisories,
            advisorySummary = advisorySummary,
            updateType = if (recommendedVersion == null) {
                UpdateType.UNKNOWN
            } else {
                VersionComparator.classifyUpdate(currentVersion, recommendedVersion)
            },
        )
    }

    private fun packageKey(coordinate: DependencyCoordinate): String {
        return "${coordinate.ecosystem.name}:${coordinate.name}"
    }

    private data class CandidatePolicyCheck(
        val version: String,
        val prereleaseAllowed: Boolean,
        val ageAllowed: Boolean,
    ) {
        val isAllowed: Boolean
            get() = prereleaseAllowed && ageAllowed
    }

    private data class PackageInsight(
        val observation: RegistryObservation?,
        val advisories: List<AdvisoryRecord>,
        val advisoryFreshnessState: FreshnessState,
    ) {
        val summary: String?
            get() = advisories.firstOrNull()?.let { advisory ->
                buildString {
                    append(advisory.severityLabel)
                    append(" · ")
                    append(advisory.advisoryId)
                    if (advisory.summary.isNotBlank()) {
                        append(" · ")
                        append(advisory.summary)
                    }
                    if (advisory.fixedVersions.isNotEmpty()) {
                        append(" · fixed ")
                        append(advisory.fixedVersions.joinToString(", "))
                    }
                }
            }

        companion object {
            fun empty(packageName: String): PackageInsight {
                return PackageInsight(
                    observation = RegistryObservation(
                        packageName = packageName,
                        registryUrl = "",
                        availableVersions = emptyList(),
                        fetchedAt = null,
                        freshnessState = FreshnessState.UNAVAILABLE,
                    ),
                    advisories = emptyList(),
                    advisoryFreshnessState = FreshnessState.UNAVAILABLE,
                )
            }
        }
    }

    companion object {
        private const val MAX_CONCURRENT_PACKAGE_LOOKUPS = 6
    }
}
