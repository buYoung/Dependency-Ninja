package com.github.buyoung.dependencyninja.features.updateWorkflow.infrastructure

import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.UpdatePreviewItem
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.ValidationState
import com.github.buyoung.dependencyninja.features.updateWorkflow.domain.WarningFlag
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil

class ManifestEditor(
    private val project: Project,
) {
    fun applyPreview(previewItem: UpdatePreviewItem): UpdatePreviewItem {
        val targetRange = previewItem.targetRange ?: return previewItem.copy(validationState = ValidationState.FAILED)
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(previewItem.targetManifestPath)
            ?: return previewItem.copy(validationState = ValidationState.FAILED)
        val originalText = runCatching { String(virtualFile.contentsToByteArray()) }.getOrNull()
            ?: return previewItem.copy(validationState = ValidationState.FAILED)
        val updatedText = buildString {
            append(originalText.substring(0, targetRange.startOffset))
            append(previewItem.toVersionText)
            append(originalText.substring(targetRange.endOffset))
        }

        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(virtualFile, updatedText)
        }

        if (!isValidManifestText(updatedText)) {
            WriteCommandAction.runWriteCommandAction(project) {
                VfsUtil.saveText(virtualFile, originalText)
            }
            return previewItem.copy(
                validationState = ValidationState.ROLLED_BACK,
                warningFlags = previewItem.warningFlags + WarningFlag.REPARSE_FAILED_AFTER_APPLY,
            )
        }

        return previewItem.copy(validationState = ValidationState.VALIDATED)
    }

    private fun isValidManifestText(text: String): Boolean {
        val trimmedText = text.trim()
        return trimmedText.startsWith("{") &&
            trimmedText.endsWith("}") &&
            trimmedText.count { it == '{' } == trimmedText.count { it == '}' }
    }
}
