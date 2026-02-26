package com.github.buyoung.dependencyninja.core.shared.domain

import com.intellij.openapi.util.TextRange

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

enum class DependencyStatus {
    OUTDATED,
    UP_TO_DATE,
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

data class DeclaredDependency(
    val coordinate: DependencyCoordinate,
    val currentVersion: String,
    val manifestPath: String,
    val moduleName: String,
    val versionRange: TextRange?,
)

data class DependencyUpdate(
    val declared: DeclaredDependency,
    val latestVersion: String?,
    val status: DependencyStatus,
    val updateType: UpdateType,
)

data class DependencySnapshot(
    val updates: List<DependencyUpdate>,
    val scannedAtEpochMillis: Long,
)
