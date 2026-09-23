package dev.lain.claudejb.model.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

suspend fun rethrowIfCancelled(cause: Throwable) {
    if (cause is CancellationException && !currentCoroutineContext().isActive) throw cause
}
