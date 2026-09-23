package dev.lain.claudejb.frontend.window

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import dev.lain.claudejb.frontend.jcef.JcefHost
import dev.lain.claudejb.frontend.jcef.edtNow
import dev.lain.claudejb.frontend.rpc.ChatClient
import dev.lain.claudejb.frontend.theme.ChatTheme
import dev.lain.claudejb.frontend.theme.JcefTheme
import dev.lain.claudejb.rpc.ChatId
import dev.lain.claudejb.rpc.FrontendChannel
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.rpc.TerminalLaunch
import kotlinx.serialization.json.Json
import javax.swing.JComponent

internal class ChatView(
    chatId: ChatId,
    private val client: ChatClient,
    private val onFocusRequest: () -> Unit,
) : ChatCard {

    private val host = JcefHost(this, ::onPageMessage, onResync = ::resync)

    private val link = client.link(chatId, this, ::onPush) { host.isWebReady }

    override val component: JComponent get() = host.component

    init {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { pushTheme() })
        ChatTheme.onChange(this, ::pushTheme)
    }

    override fun focus() = host.requestFocus()

    override fun focusTarget(): JComponent? = host.inputComponent()

    override fun whenReady(block: () -> Unit) = host.whenWebReady(block = block)

    override fun dispose() = Unit

    private fun onPageMessage(json: String) {
        val message = PageMessage.parse(json)
        when {
            message.type in FrontendChannel.clientMessages -> paste(message.type)

            message.type == PageMessage.OPEN && PageMessage.isSecureLink(message.url) -> BrowserUtil.browse(message.url.trim())

            else -> {
                link.post(json)
                if (message.type == PageMessage.READY) pageReady()
            }
        }
    }

    private fun pageReady() {
        host.markWebReady()
        pushTheme()
        link.ready()
    }

    private fun resync() {
        if (host.isWebReady) link.ready()
    }

    private fun onPush(push: PagePush) {
        if (!FrontendChannel.isFrontendPush(push.method)) {
            host.call(push.method, push.json)
            return
        }
        when (push.method) {
            FrontendChannel.COPY -> PageMessage.string(push.json)?.let { text -> edtNow { ClientClipboard.copy(text) } }
            FrontendChannel.BROWSE -> PageMessage.string(push.json)?.takeIf(PageMessage::isWebLink)?.let { BrowserUtil.browse(it) }
            FrontendChannel.VIBE -> ChatTheme.setVibeMode(push.json.trim() == "true")
            FrontendChannel.FOCUS -> edtNow(onFocusRequest)
            FrontendChannel.TERMINAL -> openTerminal(Json.decodeFromString(TerminalLaunch.serializer(), push.json))
            FrontendChannel.ACTION -> PageMessage.string(push.json)?.let { id -> edtNow { IdeActionRunner.run(component, id) } }
            else -> log.warn("Claude Code host sent a frontend push this client does not know: ${push.method}")
        }
    }

    private fun openTerminal(launch: TerminalLaunch) {
        val terminal = client.project.serviceOrNull<TerminalOpener>()
            ?: return log.warn("Claude Code could not open '${launch.tabName}': this client has no Terminal plugin")
        edtNow { terminal.open(launch) }
    }

    private fun paste(type: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val image = ClientClipboard.pngBase64()
            when {
                image != null -> link.post(PageMessage.imageAttachment(image))
                type == PageMessage.PASTE_TEXT -> ClientClipboard.text()?.let { host.call(INSERT_TEXT, PageMessage.quote(it)) }
            }
        }
    }

    private fun pushTheme() = edtNow {
        host.exec("window.cc.theme && window.cc.theme(" + JcefTheme.vars() + ")", THEME_KEY)
    }

    private companion object {
        private val log = logger<ChatView>()

        const val INSERT_TEXT = "insertText"

        const val THEME_KEY = "frontend.theme"
    }
}
