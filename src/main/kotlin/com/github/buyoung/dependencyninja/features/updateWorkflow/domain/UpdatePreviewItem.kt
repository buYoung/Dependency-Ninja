package com.github.buyoung.dependencyninja.features.updateWorkflow.domain

import com.intellij.openapi.util.TextRange

enum class ValidationState {
    DRAFT,
    PREVIEWED,
    APPROVED,
    APPLIED,
    VALIDATED,
    FAILED,
    ROLLED_BACK,
}

enum class UpdateExecutionMode {
    MANIFEST_ONLY,
    PACKAGE_MANAGER_EXECUTION,
}

data class UpdatePreviewItem(
    val previewId: String,
    val recommendationId: String,
    val targetManifestPath: String,
    val packageName: String,
    val fromVersionText: String,
    val toVersionText: String,
    val executionMode: UpdateExecutionMode,
    val warningFlags: Set<WarningFlag> = emptySet(),
    val validationState: ValidationState = ValidationState.DRAFT,
    val targetRange: TextRange? = null,
    val commandPreview: String? = null,
)
