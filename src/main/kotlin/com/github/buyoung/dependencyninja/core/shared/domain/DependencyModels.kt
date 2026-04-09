package com.github.buyoung.dependencyninja.core.shared.domain

import com.intellij.openapi.util.TextRange
import java.time.Instant

enum class Ecosystem {
    NPM,
    PYPI,
    MAVEN,
    GO,
}

enum class LookupChannel {
    HTTP_REGISTRY,
    PACKAGE_MANAGER,
}

enum class UpdateType {
    MAJOR,
    MINOR,
    PATCH,
    UNKNOWN,
}

// Canonical v1.0 status vocabulary:
// up-to-date, outdated, blocked, risky, ignored, stale, verification-unavailable
enum class DependencyStatus {
    UP_TO_DATE,
    OUTDATED,
    BLOCKED,
    RISKY,
    IGNORED,
    STALE,
    VERIFICATION_UNAVAILABLE,
}

enum class DependencyKind {
    PRODUCTION,
    DEVELOPMENT,
    PEER,
    OPTIONAL,
    OVERRIDE,
    RESOLUTION,
    WORKSPACE,
    CATALOG,
    UNKNOWN,
}

data class DependencyCoordinate(
    val ecosystem: Ecosystem,
    val name: String,
    val group: String? = null,
    val artifact: String? = null,
) {
    fun displayName(): String = when {
        group != null && artifact != null -> "$group:$artifact"
        else -> name
    }
}

data class RegistryContext(
    val registryUrl: String = "https://registry.npmjs.org",
    val packageManager: String? = null,
)

data class ManifestScope(
    val manifestPath: String,
    val manifestKind: String,
    val ecosystem: Ecosystem,
    val moduleName: String,
    val isWorkspaceRoot: Boolean,
    val registryContext: RegistryContext,
)

data class DependencyDeclaration(
    val declarationId: String,
    val coordinate: DependencyCoordinate,
    val packageName: String,
    val sourceManifestPath: String,
    val moduleName: String,
    val dependencyKind: DependencyKind,
    val declaredVersionText: String,
    val normalizedCurrentVersion: String?,
    val workspaceReferenceId: String? = null,
    val isEditableTarget: Boolean = true,
    val versionRange: TextRange?,
    val manifestScope: ManifestScope,
)

typealias DeclaredDependency = DependencyDeclaration

data class WorkspaceReference(
    val workspaceReferenceId: String,
    val referenceType: String,
    val ownerManifestPath: String,
    val packageName: String,
    val declaredVersionText: String,
    val normalizedCurrentVersion: String?,
    val affectedMemberCount: Int,
    val versionRange: TextRange?,
    val coordinate: DependencyCoordinate,
)

data class RegistryObservation(
    val packageName: String,
    val registryUrl: String,
    val availableVersions: List<String>,
    val fetchedAt: Instant?,
    val freshnessState: FreshnessState,
    val releaseTimestamps: Map<String, Instant> = emptyMap(),
)

data class AdvisoryRecord(
    val advisoryId: String,
    val packageName: String,
    val affectedRange: String,
    val summary: String,
    val severityLabel: String,
    val fixedVersions: List<String> = emptyList(),
    val fetchedAt: Instant? = null,
)

data class SurfaceAvailability(
    val inlineHints: Boolean = true,
    val toolWindow: Boolean = true,
    val inspection: Boolean = true,
)

data class RecommendationRecord(
    val recommendationId: String,
    val declarationId: String,
    val workspaceReferenceId: String? = null,
    val coordinate: DependencyCoordinate,
    val packageName: String,
    val sourceManifestPath: String,
    val moduleName: String,
    val declaredVersionText: String,
    val currentVersion: String,
    val recommendedVersion: String?,
    val status: DependencyStatus,
    val reasonCodes: Set<ReasonCode> = emptySet(),
    val freshnessState: FreshnessState,
    val surfaceAvailability: SurfaceAvailability = SurfaceAvailability(),
    val isEditableTarget: Boolean,
    val versionRange: TextRange?,
    val targetManifestPath: String,
    val targetVersionRange: TextRange?,
    val targetDeclaredVersionText: String,
    val advisorySummary: String? = null,
    val updateType: UpdateType = UpdateType.UNKNOWN,
)

typealias DependencyUpdate = RecommendationRecord

data class ProjectSnapshot(
    val declarations: List<DependencyDeclaration> = emptyList(),
    val workspaceReferences: List<WorkspaceReference> = emptyList(),
    val recommendations: List<RecommendationRecord> = emptyList(),
    val scannedAtEpochMillis: Long,
    val disabledReason: String? = null,
)

data class DependencySnapshot(
    val updates: List<RecommendationRecord>,
    val declarations: List<DependencyDeclaration> = emptyList(),
    val workspaceReferences: List<WorkspaceReference> = emptyList(),
    val scannedAtEpochMillis: Long,
    val disabledReason: String? = null,
)
