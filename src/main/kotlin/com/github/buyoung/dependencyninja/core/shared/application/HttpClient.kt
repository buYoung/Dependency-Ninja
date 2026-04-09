package com.github.buyoung.dependencyninja.core.shared.application

interface HttpClient {
    fun get(url: String): String?

    fun post(url: String, body: String): String? = null
}
