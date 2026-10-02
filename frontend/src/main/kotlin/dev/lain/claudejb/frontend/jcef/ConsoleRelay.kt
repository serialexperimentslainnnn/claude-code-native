package dev.lain.claudejb.frontend.jcef

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter

internal class ConsoleRelay : CefDisplayHandlerAdapter() {
    override fun onConsoleMessage(
        browser: CefBrowser?,
        level: CefSettings.LogSeverity?,
        message: String?,
        source: String?,
        line: Int,
    ): Boolean {
        val text = "chat page console [${level?.name?.removePrefix("LOGSEVERITY_")?.lowercase()}] $message ($source:$line)"
        when (level) {
            CefSettings.LogSeverity.LOGSEVERITY_ERROR,
            CefSettings.LogSeverity.LOGSEVERITY_FATAL,
            CefSettings.LogSeverity.LOGSEVERITY_WARNING,
            -> log.warn(text)

            else -> log.debug { text }
        }
        return false
    }

    private companion object {
        private val log = logger<ConsoleRelay>()
    }
}
