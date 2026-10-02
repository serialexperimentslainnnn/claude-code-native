package dev.lain.claudejb.frontend.window

import dev.lain.claudejb.rpc.TerminalLaunch

interface TerminalOpener {

    fun open(launch: TerminalLaunch)
}
