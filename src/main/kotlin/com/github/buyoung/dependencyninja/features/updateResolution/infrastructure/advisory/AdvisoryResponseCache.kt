package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory

import com.github.buyoung.dependencyninja.core.shared.domain.CacheFreshnessPolicy
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AdvisoryResponseCache(
    private val clock: Clock = Clock.systemUTC(),
    private val freshnessPolicy: CacheFreshnessPolicy = CacheFreshnessPolicy(),
) {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun load(
        packageName: String,
        loader: () -> AdvisoryLookupResult?,
    ): AdvisoryLookupResult {
        val now = Instant.now(clock)
        val cachedEntry = cache[packageName]
        val cachedFreshness = cachedEntry?.let {
            freshnessPolicy.classify(
                lastSuccessfulFetchAt = it.fetchedAt,
                refreshFailed = it.refreshFailed,
                now = now,
            )
        }

        if (cachedEntry != null && cachedFreshness == FreshnessState.FRESH) {
            return AdvisoryLookupResult(
                records = cachedEntry.records,
                freshnessState = FreshnessState.FRESH,
            )
        }

        val loadedResult = loader()
        if (loadedResult != null) {
            cache[packageName] = CacheEntry(
                records = loadedResult.records,
                fetchedAt = now,
                refreshFailed = false,
            )
            return loadedResult.copy(freshnessState = FreshnessState.FRESH)
        }

        if (cachedEntry != null) {
            cache[packageName] = cachedEntry.copy(refreshFailed = true)
            return AdvisoryLookupResult(
                records = cachedEntry.records,
                freshnessState = FreshnessState.STALE,
            )
        }

        return AdvisoryLookupResult(
            records = emptyList(),
            freshnessState = FreshnessState.UNAVAILABLE,
        )
    }

    private data class CacheEntry(
        val records: List<com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord>,
        val fetchedAt: Instant,
        val refreshFailed: Boolean,
    )
}
