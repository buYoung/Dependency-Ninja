package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager

import com.github.buyoung.dependencyninja.core.shared.application.VersionSource
import com.github.buyoung.dependencyninja.core.shared.domain.LookupChannel

abstract class PackageManagerVersionSource : VersionSource {
    final override val channel: LookupChannel = LookupChannel.PACKAGE_MANAGER
}
