package com.github.buyoung.dependencyninja.core.shared.domain

import java.time.Duration
import java.time.Instant

enum class FreshnessState {
    FRESH,
    STALE,
    UNAVAILABLE,
}

data class CacheFreshnessPolicy(
    val ttl: Duration = Duration.ofHours(24),
) {
    fun classify(
        lastSuccessfulFetchAt: Instant?,
        refreshFailed: Boolean,
        now: Instant,
    ): FreshnessState {
        if (lastSuccessfulFetchAt == null) {
            return FreshnessState.UNAVAILABLE
        }
        if (refreshFailed || lastSuccessfulFetchAt.plus(ttl).isBefore(now)) {
            return FreshnessState.STALE
        }
        return FreshnessState.FRESH
    }
}
