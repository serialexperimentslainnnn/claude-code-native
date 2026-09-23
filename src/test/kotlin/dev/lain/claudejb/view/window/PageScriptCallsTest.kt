package dev.lain.claudejb.view.window

import dev.lain.claudejb.rpc.PagePush
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PageScriptCallsTest {

    @Test
    fun `a guarded call becomes its method and its argument`() {
        assertEquals(
            PagePush("authState", "{\"step\":\"url\"}"),
            PageScriptCalls.parse("window.cc.authState && window.cc.authState({\"step\":\"url\"})"),
        )
    }

    @Test
    fun `an argument spanning lines is kept whole`() {
        assertEquals(
            PagePush("bootPathError", "\"a\nb\""),
            PageScriptCalls.parse("window.cc.bootPathError && window.cc.bootPathError(\"a\nb\")"),
        )
    }

    @Test
    fun `a script that is not one guarded call is refused`() {
        assertNull(PageScriptCalls.parse("window.cc.meta && window.cc.state({})"))
        assertNull(PageScriptCalls.parse("alert(1)"))
    }
}
