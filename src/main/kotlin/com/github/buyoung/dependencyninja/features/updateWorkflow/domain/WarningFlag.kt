package com.github.buyoung.dependencyninja.features.updateWorkflow.domain

enum class WarningFlag {
    UNCOMMITTED_VCS_CHANGES,
    STALE_METADATA,
    VERIFICATION_UNAVAILABLE_METADATA,
    REPARSE_FAILED_AFTER_APPLY,
}
