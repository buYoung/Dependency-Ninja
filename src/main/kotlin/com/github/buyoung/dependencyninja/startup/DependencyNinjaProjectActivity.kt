package com.github.buyoung.dependencyninja.startup

import com.github.buyoung.dependencyninja.services.DependencyNinjaProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class DependencyNinjaProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<DependencyNinjaProjectService>().refreshInBackground(showNotification = false)
    }
}
