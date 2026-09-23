package dev.lain.claudejb.frontend.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import javax.swing.JComponent
import javax.swing.border.EmptyBorder

class JcefHost(
    parentDisposable: Disposable,
    private val onMessage: (String) -> Unit,
    private val onResync: () -> Unit = {},
    onPageLost: () -> Unit = {},
) {

    val supported: Boolean = JBCefApp.isSupported()

    private val browser: JBCefBrowser?

    private var ready: Boolean = false

    private val pending = PendingCalls()

    @Volatile
    var isWebReady: Boolean = false
        private set

    @Volatile
    private var disposed: Boolean = false

    private var delivery: PageDelivery? = null

    private val deferred = ArrayList<ReadyBlock>()

    private val deferredAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)

    val component: JComponent

    init {
        if (!supported) {
            browser = null
            component = JBLabel(
                "Claude Code needs JCEF — enable `ide.browser.jcef.enabled` in the Registry and restart.",
            ).apply {
                border = EmptyBorder(16, 16, 16, 16)
            }
        } else {
            val b = JBCefBrowser.createBuilder().build()
            browser = b
            component = b.component

            Disposer.register(parentDisposable, b)

            Disposer.register(
                parentDisposable,
                Disposable {
                    disposed = true
                    deferredAlarm.cancelAllRequests()
                    deferred.clear()
                    delivery?.stopLoopback()
                },
            )

            val base: JBCefBrowserBase = b
            val query = JBCefJSQuery.create(base)
            Disposer.register(parentDisposable, query)
            query.addHandler { request ->
                if (!disposed) onMessage(request)
                null
            }

            installNavigationGuards(b, ::isOwnPage)
            b.jbCefClient.addLoadHandler(
                PageLoadHandler(
                    onStarted = { delivery?.pageLoadStarted() },
                    onArrived = { drainInto(b, query) },
                    onMissed = ::pageMissed,
                ),
                b.cefBrowser,
            )
            b.jbCefClient.addDisplayHandler(ConsoleRelay(), b.cefBrowser)

            val d = PageDelivery(
                browser = b,
                parentDisposable = parentDisposable,
                webReady = { isWebReady },
                onRedeliver = { ready = false },
                isDisposed = { disposed },
                onExhausted = onPageLost,
            )
            delivery = d
            d.start()
        }
    }

    fun call(method: String, json: String) {
        if (!METHOD_NAME.matches(method)) {
            log.warn("Claude Code chat page was asked to run a method that is not a name: $method")
            return
        }
        exec("window.cc.$method && window.cc.$method($json)", method.takeIf { it in SNAPSHOT_METHODS })
    }

    fun exec(js: String, snapshotKey: String? = null) {
        val b = browser ?: return
        edtNow {
            if (disposed) return@edtNow
            if (ready) {
                executeNow(b, js)
            } else {
                pending.add(snapshotKey, js)
            }
        }
    }

    fun whenWebReady(timeoutMs: Long = WEB_READY_TIMEOUT_MS, block: () -> Unit) {
        edtNow {
            if (isWebReady || browser == null) {
                block()
                return@edtNow
            }
            val entry = ReadyBlock(block)
            deferred.add(entry)
            deferredAlarm.addRequest({ runDeferred(entry) }, timeoutMs)
        }
    }

    fun markWebReady() {
        edtNow {
            isWebReady = true
            delivery?.cancelWatchdog()
            delivery?.stopLoopback()
            if (inputComponent()?.isFocusOwner == true) grantCefFocus()
            flushDeferred()
        }
    }

    fun requestFocus() {
        edtNow {
            val target = inputComponent() ?: return@edtNow
            if (target.isFocusOwner) grantCefFocus() else IdeFocusManager.getGlobalInstance().requestFocus(target, true)
        }
    }

    private fun grantCefFocus() {
        runCatching { browser?.cefBrowser?.setFocus(true) }
        exec("window.cc.focusInput && window.cc.focusInput()")
    }

    fun inputComponent(): JComponent? {
        val b = browser ?: return null
        return runCatching { b.cefBrowser.uiComponent }.getOrNull() as? JComponent
    }

    private class ReadyBlock(val block: () -> Unit)

    private fun flushDeferred() {
        deferredAlarm.cancelAllRequests()
        val queued = ArrayList(deferred)
        deferred.clear()
        queued.forEach { it.block() }
    }

    private fun runDeferred(entry: ReadyBlock) {
        if (!deferred.remove(entry)) return
        log.warn("Claude Code chat page has not announced itself in time — running a deferred action without it")
        entry.block()
    }

    private fun pageMissed(httpStatusCode: Int) = edtNow {
        log.warn(
            "Claude Code chat page did not load over ${delivery?.route ?: "an unknown route"} " +
                "(http status $httpStatusCode) — keeping the queued state for the next one",
        )
    }

    private fun drainInto(b: JBCefBrowser, query: JBCefJSQuery) {
        executeNow(b, "window.__ccSend = function(p){ " + query.inject("p") + " };")
        edtNow {
            ready = true
            delivery?.relaxWatchdog()
            val drained = pending.drain()
            drained.calls.forEach { executeNow(b, it) }
            if (drained.overflowed) {
                log.warn("Claude Code chat page missed more updates than it can queue — asking for a full resync")
                onResync()
            }
        }
    }

    private fun isOwnPage(url: String?): Boolean =
        delivery?.isOwnPage(url) ?: isOwnPageUrl(url, SchemePageServer.PAGE_URL, null)

    private fun executeNow(b: JBCefBrowser, js: String) {
        val url = b.cefBrowser.url ?: SchemePageServer.PAGE_URL
        val guarded = "try{" + js + "}catch(e){try{window.__ccSend&&window.__ccSend(JSON.stringify(" +
            "{type:'diag',report:'uncaught exec: '+((e&&e.stack)||e)}))}catch(_){}}"
        b.cefBrowser.executeJavaScript(guarded, url, 0)
    }

    private companion object {
        private val log = logger<JcefHost>()

        private const val WEB_READY_TIMEOUT_MS = 5_000L

        private val METHOD_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

        private val SNAPSHOT_METHODS = setOf("session", "meta", "state", "settingsMenu", "theme", "permissions", "tabs")
    }
}
