package dev.lain.claudejb.view.git

import dev.lain.claudejb.controller.commands.git.GitChatConversation
import dev.lain.claudejb.controller.session.ClaudeSession
import dev.lain.claudejb.model.bridge.JcefBridge
import dev.lain.claudejb.rpc.PagePush
import dev.lain.claudejb.view.payload.chat.JcefCardPayload
import dev.lain.claudejb.view.window.ChatPresenter
import dev.lain.claudejb.view.window.ChatSnapshots.Kind
import dev.lain.claudejb.view.window.PushStream

internal class GitChatFeed(
    private val presenter: ChatPresenter,
    private val emit: (PagePush) -> Unit,
) : GitChatConversation.View {

    private val conversation = GitChatConversation.getInstance(presenter.project)

    private var lastPushed: String? = null

    init {
        conversation.attach(this)
    }

    fun session(): ClaudeSession = conversation.sessionOrCreate()

    fun send(text: String) = conversation.send(text)

    fun interrupt() = conversation.interrupt()

    fun permissionGroup(): List<JcefCardPayload.Group> = conversation.permissionGroup()

    fun show() {
        emit(PagePush("setGitSubView", JcefBridge.jsString("chat")))
        emit(PagePush("showGitView", PushStream.NO_ARGS))
    }

    override fun drawGitChat(payload: String?) {
        val json = payload ?: "null"
        if (json == lastPushed) return
        lastPushed = json
        emit(PagePush("gitChat", json))
    }

    override fun refreshGitChatPermissions() = presenter.snapshots.mark(Kind.PERMISSIONS)

    fun dispose() = conversation.detach(this)
}
