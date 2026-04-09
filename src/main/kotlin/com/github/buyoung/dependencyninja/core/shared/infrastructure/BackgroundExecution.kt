package com.github.buyoung.dependencyninja.core.shared.infrastructure

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object BackgroundExecution {
    fun <T> submit(task: () -> T): Future<T> {
        return AppExecutorUtil.getAppExecutorService().submit(Callable { task() })
    }

    fun schedule(
        delayMillis: Long,
        task: () -> Unit,
    ): ScheduledFuture<*> {
        return AppExecutorUtil.getAppScheduledExecutorService().schedule(task, delayMillis, TimeUnit.MILLISECONDS)
    }

    fun onEdt(task: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(task, ModalityState.defaultModalityState())
    }
}
