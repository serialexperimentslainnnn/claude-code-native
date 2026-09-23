package dev.lain.claudejb.rpc.backend

import com.intellij.platform.rpc.backend.RemoteApiProvider
import dev.lain.claudejb.rpc.ChatApi
import fleet.rpc.remoteApiDescriptor

internal class ChatApiProvider : RemoteApiProvider {

    override fun RemoteApiProvider.Sink.remoteApis() {
        remoteApi(remoteApiDescriptor<ChatApi>()) { ChatApiImpl() }
    }
}
