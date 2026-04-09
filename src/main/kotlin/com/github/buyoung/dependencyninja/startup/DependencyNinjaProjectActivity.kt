package com.github.buyoung.dependencyninja.startup

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.infrastructure.BackgroundExecution
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.concurrent.ScheduledFuture

class DependencyNinjaProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<DependencyNinjaProjectService>()
        if (!isJsonPsiAvailable()) {
            service.publishDisabledSnapshot(DependencyNinjaBundle.message("disabled.jsonPsiUnavailable"))
            return
        }

        var pendingRefresh: ScheduledFuture<*>? = null
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any(::isRelevantManifestChange)) {
                        pendingRefresh?.cancel(false)
                        pendingRefresh = BackgroundExecution.schedule(500) {
                            service.refreshInBackground(showNotification = false)
                        }
                    }
                }
            },
        )

        service.refreshInBackground(showNotification = false)
    }

    private fun isJsonPsiAvailable(): Boolean {
        val jsonLanguage = Language.findLanguageByID("JSON") ?: return false
        return LanguageParserDefinitions.INSTANCE.forLanguage(jsonLanguage) != null
    }

    private fun isRelevantManifestChange(event: VFileEvent): Boolean {
        val path = when (event) {
            is VFileContentChangeEvent -> event.path
            is VFilePropertyChangeEvent -> event.path
            else -> event.path
        }
        return path.endsWith("/package.json") ||
            path.endsWith("/pnpm-workspace.yaml") ||
            path.endsWith("/bunfig.toml")
    }
}
