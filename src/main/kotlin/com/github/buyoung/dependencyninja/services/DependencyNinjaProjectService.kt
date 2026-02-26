package com.github.buyoung.dependencyninja.services

import com.github.buyoung.dependencyninja.core.shared.domain.DependencySnapshot
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyUpdate
import com.github.buyoung.dependencyninja.core.shared.infrastructure.SimpleHttpClient
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.application.DependencyDiscoveryUseCase
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import com.github.buyoung.dependencyninja.features.updateResolution.application.DependencyUpdateResolver
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.registry.GoProxyAdapter
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.registry.MavenCentralAdapter
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.registry.NpmRegistryAdapter
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.registry.PyPiRegistryAdapter
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
    private val httpClient = SimpleHttpClient()
    private val discoveryUseCase = DependencyDiscoveryUseCase(ManifestDependencyParser())
    private val resolver = DependencyUpdateResolver(
        listOf(
            NpmRegistryAdapter(httpClient),
            PyPiRegistryAdapter(httpClient),
            MavenCentralAdapter(httpClient),
            GoProxyAdapter(httpClient),
        ),
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
                            "Dependency Ninja",
                            "Dependency scan finished",
                            "Outdated dependencies: $outdatedCount",
                            NotificationType.INFORMATION,
                        ),
                        project,
                    )
                }
            }
        }
    }
}
