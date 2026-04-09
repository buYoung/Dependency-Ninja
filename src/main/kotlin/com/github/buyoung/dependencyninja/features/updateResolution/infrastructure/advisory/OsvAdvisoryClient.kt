package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import java.time.Instant

class OsvAdvisoryClient(
    private val httpClient: HttpClient,
) {
    fun lookup(packageName: String): AdvisoryLookupResult {
        val body = httpClient.post(
            "https://api.osv.dev/v1/query",
            """{"package":{"name":"$packageName","ecosystem":"npm"}}""",
        ) ?: return AdvisoryLookupResult(emptyList(), FreshnessState.UNAVAILABLE)

        val idPattern = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
        val summaryPattern = Regex("\"summary\"\\s*:\\s*\"([^\"]+)\"")
        val advisories = idPattern.findAll(body).map { matchResult ->
            AdvisoryRecord(
                advisoryId = matchResult.groupValues[1],
                packageName = packageName,
                affectedRange = "",
                summary = summaryPattern.find(body, matchResult.range.first)?.groupValues?.get(1).orEmpty(),
                severityLabel = "warning",
                fetchedAt = Instant.now(),
            )
        }.toList()

        return AdvisoryLookupResult(
            records = advisories,
            freshnessState = FreshnessState.FRESH,
        )
    }
}

data class AdvisoryLookupResult(
    val records: List<AdvisoryRecord>,
    val freshnessState: FreshnessState,
)
