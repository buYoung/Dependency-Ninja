package com.github.buyoung.dependencyninja.features.updateWorkflow.infrastructure

import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdatePreviewItem

class PackageManagerExecutor {
    fun execute(previewItem: UpdatePreviewItem): UpdatePreviewItem {
        return previewItem.copy(
            validationState = com.github.buyoung.dependencyninja.features.updateWorkflow.domain.ValidationState.PREVIEWED,
        )
    }
}
