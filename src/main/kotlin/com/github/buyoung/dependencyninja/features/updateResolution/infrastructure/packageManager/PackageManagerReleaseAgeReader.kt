package com.github.buyoung.dependencyninja.features.updateResolution.infrastructure.packageManager

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

class PackageManagerReleaseAgeReader {
    fun read(project: Project, manifestPath: String): Int? {
        project.basePath
        val file = LocalFileSystem.getInstance().findFileByPath(manifestPath) ?: return null
        if (file.name != "package.json") {
            return null
        }
        val text = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return null
        val directMatch = Regex("\"minimumReleaseAge\"\\s*:\\s*\"?(\\d+)\"?").find(text)
        return directMatch?.groupValues?.get(1)?.toIntOrNull()
    }
}
