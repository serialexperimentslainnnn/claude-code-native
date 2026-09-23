package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.util.logger

internal object PageScriptCalls {

    private val LOG = logger<PageScriptCalls>()

    private const val DROPPED_PREVIEW_CHARS = 80

    private val CALL = Regex("""^window\.cc\.(\w+) && window\.cc\.\1\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

    fun parse(script: String): PagePush? =
        CALL.matchEntire(script.trim())?.let { PagePush(it.groupValues[1], it.groupValues[2]) }

    fun into(emit: (PagePush) -> Unit): (String) -> Unit = { script ->
        val push = parse(script)
        if (push == null) LOG.warn("A page call is not a guarded window.cc call and was dropped: ${script.take(DROPPED_PREVIEW_CHARS)}") else emit(push)
    }
}
