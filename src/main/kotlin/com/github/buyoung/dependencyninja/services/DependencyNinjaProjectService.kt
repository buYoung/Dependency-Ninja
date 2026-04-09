package com.github.buyoung.dependencyninja.services

import com.github.buyoung.dependencyninja.DependencyNinjaBundle
import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.domain.DependencyStatus
import com.github.buyoung.dependencyninja.core.shared.domain.ProjectSnapshot
import com.github.buyoung.dependencyninja.core.shared.domain.RecommendationRecord
import com.github.buyoung.dependencyninja.core.shared.infrastructure.BackgroundExecution
import com.github.buyoung.dependencyninja.core.shared.infrastructure.SimpleHttpClient
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.application.DependencyDiscoveryUseCase
import com.github.buyoung.dependencyninja.features.dependencyDiscovery.infrastructure.ManifestDependencyParser
import com.github.buyoung.dependencyninja.features.settings.application.PolicyProfileService
import com.github.buyoung.dependencyninja.features.updateResolution.application.DependencyUpdateResolver
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory.OsvAdvisoryClient
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.cache.RegistryResponseCache
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.http.NpmHttpVersionSource
import com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager.PackageManagerReleaseAgeReader
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class DependencyNinjaProjectService(
    private val project: Project,
) {
    private val httpClient: HttpClient = SimpleHttpClient()
    private val registryResponseCache = RegistryResponseCache()
    private val discoveryUseCase = DependencyDiscoveryUseCase(ManifestDependencyParser())
    private val policyProfileService by lazy { project.service<PolicyProfileService>() }
    private val resolver by lazy {
        DependencyUpdateResolver(
            project = project,
            sources = listOf(NpmHttpVersionSource(httpClient, registryResponseCache)),
            policyProfileService = policyProfileService,
            advisoryClient = OsvAdvisoryClient(httpClient),
            packageManagerReleaseAgeReader = PackageManagerReleaseAgeReader(),
        )
    }

    private val snapshotRef = AtomicReference(
        ProjectSnapshot(
            scannedAtEpochMillis = 0L,
        ),
    )
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun snapshot(): ProjectSnapshot = snapshotRef.get()

    fun recommendationsForManifest(manifestPath: String): List<RecommendationRecord> {
        return snapshotRef.get().recommendations.filter { it.sourceManifestPath == manifestPath }
    }

    fun findRecommendation(recommendationId: String): RecommendationRecord? {
        return snapshotRef.get().recommendations.firstOrNull { it.recommendationId == recommendationId }
    }

    fun addSnapshotListener(listener: () -> Unit) {
        listeners += listener
    }

    fun publishDisabledSnapshot(reason: String) {
        snapshotRef.set(
            ProjectSnapshot(
                scannedAtEpochMillis = System.currentTimeMillis(),
                disabledReason = reason,
            ),
        )
        notifyConsumers(showNotification = false)
    }

    fun refreshInBackground(showNotification: Boolean = false) {
        BackgroundExecution.submit {
            val discoveryResult = discoveryUseCase.discover(project)
            val recommendations = resolver.resolve(
                declarations = discoveryResult.declarations,
                workspaceReferences = discoveryResult.workspaceReferences,
            )
            snapshotRef.set(
                ProjectSnapshot(
                    declarations = discoveryResult.declarations,
                    workspaceReferences = discoveryResult.workspaceReferences,
                    recommendations = recommendations,
                    scannedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            notifyConsumers(showNotification)
        }
    }

    private fun notifyConsumers(showNotification: Boolean) {
        BackgroundExecution.onEdt {
            DaemonCodeAnalyzer.getInstance(project).restart()
            listeners.forEach { it.invoke() }
            if (showNotification) {
                val outdatedCount = snapshotRef.get().recommendations.count {
                    it.status in setOf(DependencyStatus.OUTDATED, DependencyStatus.RISKY, DependencyStatus.STALE)
                }
                Notifications.Bus.notify(
                    Notification(
                        "DependencyNinja",
                        DependencyNinjaBundle.message("notification.title"),
                        DependencyNinjaBundle.message("notification.outdatedCount", outdatedCount),
                        NotificationType.INFORMATION,
                    ),
                    project,
                )
            }
        }
    }
}
