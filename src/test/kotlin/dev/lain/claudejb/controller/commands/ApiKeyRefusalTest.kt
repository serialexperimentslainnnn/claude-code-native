package dev.lain.claudejb.controller.commands

import dev.lain.claudejb.controller.process.auth.AuthCli
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ApiKeyRefusalTest {

    @Test
    fun `a key is refused when the binary is missing`() {
        assertNotNull(apiKeyRefusal(binaryFound = false, state = null))
    }

    @Test
    fun `a key is refused when the status is unknown`() {
        assertNotNull(apiKeyRefusal(binaryFound = true, state = null))
    }

    @Test
    fun `a key the binary does not accept is refused`() {
        assertNotNull(apiKeyRefusal(binaryFound = true, state = AuthCli.AuthState(loggedIn = false)))
    }

    @Test
    fun `only a verified key is accepted`() {
        assertNull(apiKeyRefusal(binaryFound = true, state = AuthCli.AuthState(loggedIn = true)))
    }
}
