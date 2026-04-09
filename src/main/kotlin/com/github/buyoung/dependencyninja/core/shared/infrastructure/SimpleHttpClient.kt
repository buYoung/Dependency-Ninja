package com.github.buyoung.dependencyninja.core.shared.infrastructure

import com.github.buyoung.dependencyninja.core.shared.application.HttpClient
import java.net.URI
import java.net.http.HttpClient as JdkHttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class SimpleHttpClient(
    private val ttlMillis: Long = 10 * 60 * 1000,
) : HttpClient {
    private val client = JdkHttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val cache = ConcurrentHashMap<String, CachedResponse>()

    override fun get(url: String): String? {
        return execute(url = url, body = null)
    }

    override fun post(url: String, body: String): String? {
        return execute(url = url, body = body)
    }

    private fun execute(url: String, body: String?): String? {
        val now = System.currentTimeMillis()
        val cacheKey = if (body == null) "GET:$url" else "POST:$url:$body"
        val cached = cache[cacheKey]
        if (body == null && cached != null && cached.expiresAt > now) {
            return cached.body
        }

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
        val request = if (body == null) {
            requestBuilder.GET().build()
        } else {
            requestBuilder
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        }

        val response = runCatching {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }.getOrNull() ?: return null

        if (response.statusCode() !in 200..299) {
            return null
        }

        val responseBody = response.body()
        if (request.method() == "GET") {
            cache[cacheKey] = CachedResponse(body = responseBody, expiresAt = now + ttlMillis)
        }
        return responseBody
    }

    private data class CachedResponse(
        val body: String,
        val expiresAt: Long,
    )
}
