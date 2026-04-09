package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.advisory

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import com.github.buyoung.dependencyninja.core.shared.domain.AdvisoryRecord
import com.github.buyoung.dependencyninja.core.shared.domain.FreshnessState
import java.time.Instant

class OsvAdvisoryClient(
    private val httpClient: HttpClient,
    private val cache: AdvisoryResponseCache,
) {
    fun lookup(packageName: String): AdvisoryLookupResult {
        return cache.load(packageName) {
            val body = httpClient.post(
                "https://api.osv.dev/v1/query",
                """{"package":{"name":"$packageName","ecosystem":"npm"}}""",
            ) ?: return@load null

            AdvisoryLookupResult(
                records = parseAdvisories(packageName, body),
                freshnessState = FreshnessState.FRESH,
            )
        }
    }

    private fun parseAdvisories(
        packageName: String,
        body: String,
    ): List<AdvisoryRecord> {
        return extractArrayObjects(body, "vulns").mapNotNull { advisoryJson ->
            val advisoryId = extractStringValue(advisoryJson, "id") ?: return@mapNotNull null
            val summary = extractStringValue(advisoryJson, "summary")
                ?: extractStringValue(advisoryJson, "details")
                ?: return@mapNotNull null
            val severityLabel = extractNestedSeverity(advisoryJson)
                ?: extractFirstArrayValue(advisoryJson, "aliases")
                ?: "warning"
            val fixedVersions = extractFixedVersions(advisoryJson)
            val affectedRange = extractAffectedRange(advisoryJson)

            AdvisoryRecord(
                advisoryId = advisoryId,
                packageName = packageName,
                affectedRange = affectedRange,
                summary = summarize(summary),
                severityLabel = severityLabel,
                fixedVersions = fixedVersions,
                fetchedAt = Instant.now(),
            )
        }
    }

    private fun extractNestedSeverity(advisoryJson: String): String? {
        return Regex("\"severity\"\\s*:\\s*\"([^\"]+)\"").find(advisoryJson)?.groupValues?.get(1)
            ?: Regex("\"score\"\\s*:\\s*\"([^\"]+)\"").find(advisoryJson)?.groupValues?.get(1)
    }

    private fun extractFixedVersions(advisoryJson: String): List<String> {
        return Regex("\"fixed\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(advisoryJson)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    private fun extractAffectedRange(advisoryJson: String): String {
        val introduced = Regex("\"introduced\"\\s*:\\s*\"([^\"]+)\"").find(advisoryJson)?.groupValues?.get(1)
        val fixed = Regex("\"fixed\"\\s*:\\s*\"([^\"]+)\"").find(advisoryJson)?.groupValues?.get(1)
        return buildString {
            if (introduced != null) {
                append(introduced)
            }
            if (fixed != null) {
                if (isNotEmpty()) {
                    append(" -> ")
                }
                append(fixed)
            }
        }
    }

    private fun extractStringValue(
        json: String,
        key: String,
    ): String? {
        return Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", " ")
            ?.trim()
    }

    private fun extractFirstArrayValue(
        json: String,
        key: String,
    ): String? {
        val arrayBody = extractArrayBody(json, key) ?: return null
        return Regex("\"((?:\\\\.|[^\"])*)\"")
            .find(arrayBody)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
            ?.trim()
    }

    private fun extractArrayObjects(
        json: String,
        key: String,
    ): List<String> {
        val arrayBody = extractArrayBody(json, key) ?: return emptyList()
        val objects = mutableListOf<String>()
        var depth = 0
        var objectStart = -1
        var inString = false
        var escaped = false

        arrayBody.forEachIndexed { index, character ->
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> {
                    if (depth == 0) {
                        objectStart = index
                    }
                    depth += 1
                }

                character == '}' -> {
                    depth -= 1
                    if (depth == 0 && objectStart >= 0) {
                        objects += arrayBody.substring(objectStart, index + 1)
                        objectStart = -1
                    }
                }
            }
        }

        return objects
    }

    private fun extractArrayBody(
        json: String,
        key: String,
    ): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) {
            return null
        }
        val arrayStart = json.indexOf('[', keyIndex)
        if (arrayStart < 0) {
            return null
        }
        var depth = 0
        var inString = false
        var escaped = false
        for (index in arrayStart until json.length) {
            val character = json[index]
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '[' -> depth += 1
                character == ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return json.substring(arrayStart + 1, index)
                    }
                }
            }
        }
        return null
    }

    private fun summarize(text: String): String {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length > 180) {
            normalized.take(177) + "..."
        } else {
            normalized
        }
    }
}

data class AdvisoryLookupResult(
    val records: List<AdvisoryRecord>,
    val freshnessState: FreshnessState,
)
