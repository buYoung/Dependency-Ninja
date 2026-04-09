package com.github.buyoung.dependencyninja.features.dependencyDiscovery.domain

import com.github.buyoung.dependencyninja.core.shared.domain.DependencyDeclaration
import com.github.buyoung.dependencyninja.core.shared.domain.WorkspaceReference

data class DiscoveryResult(
    val declarations: List<DependencyDeclaration>,
    val workspaceReferences: List<WorkspaceReference>,
)
