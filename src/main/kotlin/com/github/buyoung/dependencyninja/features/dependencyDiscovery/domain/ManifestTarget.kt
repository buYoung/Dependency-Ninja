package com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain

import com.intellij.openapi.vfs.VirtualFile

data class ManifestTarget(
    val file: VirtualFile,
    val moduleName: String,
)
