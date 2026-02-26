package com.github.buyoung.dependencyninja.core.shared.application

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyCoordinate
import com.github.buyoung.dependencyninja.core.shared.domain.Ecosystem
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel

interface VersionSource {
    val ecosystem: Ecosystem
    val channel: LookupChannel
    fun resolveLatestVersion(coordinate: DependencyCoordinate): String?
}
