package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.cache

import com.github.buyoung.dependencyninja.core.shared.domain.CacheFreshnessPolicy
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import com.github.buyoung.dependencyninja.core.shared.domain.RegistryObservation
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RegistryResponseCache(
    private val clock: Clock = Clock.systemUTC(),
    private val freshnessPolicy: CacheFreshnessPolicy = CacheFreshnessPolicy(),
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun load(
        registryUrl: String,
        packageName: String,
        loader: () -> RegistryObservation?,
    ): RegistryObservation {
        val key = "$registryUrl::$packageName"
        val now = Instant.now(clock)
        val cachedEntry = cache[key]
        val cachedFreshness = cachedEntry?.let {
            freshnessPolicy.classify(
                lastSuccessfulFetchAt = it.observation.fetchedAt,
                refreshFailed = it.refreshFailed,
                now = now,
            )
        }

        if (cachedEntry != null && cachedFreshness == FreshnessState.FRESH) {
            return cachedEntry.observation.copy(freshnessState = FreshnessState.FRESH)
        }

        val loadedObservation = loader()
        if (loadedObservation != null) {
            val freshObservation = loadedObservation.copy(
                registryUrl = registryUrl,
                packageName = packageName,
                fetchedAt = now,
                freshnessState = FreshnessState.FRESH,
            )
            cache[key] = CacheEntry(observation = freshObservation, refreshFailed = false)
            return freshObservation
        }

        if (cachedEntry != null) {
            val staleObservation = cachedEntry.observation.copy(freshnessState = FreshnessState.STALE)
            cache[key] = CacheEntry(observation = staleObservation, refreshFailed = true)
            return staleObservation
        }

        return RegistryObservation(
            packageName = packageName,
            registryUrl = registryUrl,
            availableVersions = emptyList(),
            fetchedAt = null,
            freshnessState = FreshnessState.UNAVAILABLE,
        )
    }

    private data class CacheEntry(
        val observation: RegistryObservation,
        val refreshFailed: Boolean,
    )
}
