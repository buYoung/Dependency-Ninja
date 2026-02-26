package com.github.buyoung.dependencyninja.services

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.domain.DependencySnapshot
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyUpdate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel
import com.github.buyoung.dependencyninja.core.shared.infrastructure.SimpleHttpClient
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.application.DependencyDiscoveryUseCase
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import com.github.buyoung.dependencyninja.features.updateResolution.application.DependencyUpdateResolver
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http.GoHttpVersionSource
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http.MavenHttpVersionSource
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http.NpmHttpVersionSource
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http.PyPiHttpVersionSource
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class DependencyNinjaProjectService(
    private val project: Project,
) {
    private val httpClient: HttpClient = SimpleHttpClient()
    private val discoveryUseCase = DependencyDiscoveryUseCase(ManifestDependencyParser())
    private val resolver = DependencyUpdateResolver(
        sources = listOf(
            NpmHttpVersionSource(httpClient),
            PyPiHttpVersionSource(httpClient),
            MavenHttpVersionSource(httpClient),
            GoHttpVersionSource(httpClient),
        ),
        defaultChannelByEcosystem = Ecosystem.entries.associateWith { LookupChannel.HTTP_REGISTRY },
    )

    private val snapshotRef = AtomicReference(DependencySnapshot(emptyList(), 0L))
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun snapshot(): DependencySnapshot = snapshotRef.get()

    fun outdatedByManifestPath(manifestPath: String): List<DependencyUpdate> {
        return snapshotRef.get().updates.filter {
            it.declared.manifestPath == manifestPath && it.status == DependencyStatus.OUTDATED
        }
    }

    fun addSnapshotListener(listener: () -> Unit) {
        listeners += listener
    }

    fun refreshInBackground(showNotification: Boolean = false) {
        AppExecutorUtil.getAppExecutorService().submit {
            val dependencies = discoveryUseCase.discover(project)
            val updates = resolver.resolve(dependencies)
            snapshotRef.set(DependencySnapshot(updates = updates, scannedAtEpochMillis = System.currentTimeMillis()))

            ApplicationManager.getApplication().invokeLater {
                DaemonCodeAnalyzer.getInstance(project).restart()
                listeners.forEach { it.invoke() }
                if (showNotification) {
                    val outdatedCount = updates.count { it.status == DependencyStatus.OUTDATED }
                    Notifications.Bus.notify(
                        com.intellij.notification.Notification(
                            DependencyNinjaBundle.message("notification.title"),
                            DependencyNinjaBundle.message("notification.scanFinished"),
                            DependencyNinjaBundle.message("notification.outdatedCount", outdatedCount),
                            NotificationType.INFORMATION,
                        ),
                        project,
                    )
                }
            }
        }
    }
}
