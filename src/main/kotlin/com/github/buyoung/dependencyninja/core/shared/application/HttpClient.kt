package com.github.buyoung.dependencyninja.core.shared.application

interface HttpClient {
    fun get(url: String): String?
}
