package dev.lain.claudejb.controller.mcp.tools.run

import java.util.concurrent.atomic.AtomicReference

internal class RunIdentity<E : Any, H : Any>(private val matches: (E) -> Boolean) {

    private val handler = AtomicReference<H?>()

    fun ours(environment: E): Boolean = matches(environment)

    fun started(environment: E, handler: H): Boolean = ours(environment) && this.handler.compareAndSet(null, handler)

    fun terminated(handler: H): Boolean = this.handler.get() === handler
}
