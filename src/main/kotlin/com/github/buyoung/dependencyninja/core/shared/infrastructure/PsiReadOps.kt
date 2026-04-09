package com.github.buyoung.dependencyninja.core.shared.infrastructure

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

object PsiReadOps {
    fun <T> nonBlockingRead(
        project: Project,
        computable: () -> T,
    ): T {
        return ReadAction
            .nonBlocking<T> { computable() }
            .expireWith(project)
            .submit(AppExecutorUtil.getAppExecutorService())
            .get()
    }
}
