package com.github.buyoung.dependencyninja.core.shared.domain

enum class ReasonCode {
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    STABILITY_BLOCKED,
    RELEASE_AGE_BLOCKED,
    IGNORED,
    SHARED_REFERENCE_TAKES_PRECEDENCE,
    ADVISORY_FLAGGED,
    STALE_METADATA,
    VERIFICATION_UNAVAILABLE,
}
