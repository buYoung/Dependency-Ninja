package com.github.buyoung.dependencyninja.core.shared.infrastructure

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class SimpleHttpClient(
    private val ttlMillis: Long = 10 * 60 * 1000,
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val cache = ConcurrentHashMap<String, CachedResponse>()

    fun get(url: String): String? {
        val now = System.currentTimeMillis()
        val cached = cache[url]
        if (cached != null && cached.expiresAt > now) {
            return cached.body
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = runCatching {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null

        if (response.statusCode() !in 200..299) {
            return null
        }

        val body = response.body()
        cache[url] = CachedResponse(body = body, expiresAt = now + ttlMillis)
        return body
    }

    private data class CachedResponse(
        val body: String,
        val expiresAt: Long,
    )
}
