package com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain

import com.github.buyoung.dependencyninja.core.shared.domain.ManifestScope
import com.intellij.openapi.vfs.VirtualFile

data class ManifestTarget(
    val file: VirtualFile,
    val manifestScope: ManifestScope,
) {
    val moduleName: String
        get() = manifestScope.moduleName

    val manifestKind: String
        get() = manifestScope.manifestKind
}
