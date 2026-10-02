package dev.lain.claudejb.controller.bridge

import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.model.bridge.Msg
import dev.lain.claudejb.view.window.ChatPresenter

internal class BridgePrompting(private val presenter: ChatPresenter) {

    private val session get() = presenter.session

    fun handle(m: Msg.Prompting) {
        when (m) {
            is Msg.Send -> if (m.scope == JcefBridge.SCOPE_GIT) presenter.gitChat.send(m.text) else send(m.text)

            is Msg.Interrupt ->
                if (m.scope == JcefBridge.SCOPE_GIT) presenter.gitChat.interrupt() else session.turnControl.interrupt()

            is Msg.RemoveQueued -> session.prompts.remove(m.index)

            is Msg.Copy -> presenter.frontend.copyToClient(m.text)
        }
    }

    private fun send(raw: String) {
        session.prompts.clearSuggestion()
        val attachments = presenter.tray.all()
        val text = raw.trim()
        if (attachments.isEmpty() && text == "/login") {
            session.login.start()
            return
        }
        if (!session.isRunning() && !session.start()) {
            presenter.exec("insertText", JcefBridge.jsString(raw))
            return
        }
        if (attachments.isEmpty() && BTW.matches(text.substringBefore('\n'))) {
            session.sendSideQuestion(text.removePrefix("/btw").trim())
            return
        }
        presenter.tray.take()
        session.send(raw, attachments)
    }

    private companion object {
        val BTW = Regex("^/btw\\b.*")
    }
}
