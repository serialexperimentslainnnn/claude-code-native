package dev.lain.claudejb.model.permission

import dev.lain.claudejb.model.permission.SensitiveGuard.Verdict
import dev.lain.claudejb.model.permission.vocab.SecurityRule
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ScratchRootGuardTest : GuardProbe(GuardFixture.basePolicy().copy(scratchRoot = SCRATCHES)) {

    private fun tool(path: String) = buildJsonObject { put("path", path) }

    @Test
    fun `a scratch file of the IDE is not outside the project for the code tools`() {
        assertEquals(Verdict.ALLOW, v(tool("$SCRATCHES/verify_inject.kt")))
        assertEquals(Verdict.ALLOW, v(read("$SCRATCHES/notes/plan.md")))
    }

    @Test
    fun `without the scratch root the same path is still outside the project`() {
        val unaware = policy.copy(scratchRoot = null)

        assertEquals(Verdict.DENY, v(tool("$SCRATCHES/verify_inject.kt"), unaware))
        assertEquals(SecurityRule.OUTSIDE_PROJECT, rule(tool("$SCRATCHES/verify_inject.kt"), unaware))
    }

    @Test
    fun `a sibling that only shares the prefix stays outside the project`() {
        assertEquals(SecurityRule.OUTSIDE_PROJECT, rule(tool("$SCRATCHES-evil/x.kt")))
        assertEquals(SecurityRule.OUTSIDE_PROJECT, rule(tool("$SCRATCHES/../options/other.xml")))
    }

    @Test
    fun `key material in the scratch folder is still refused as credentials`() {
        val verdict = SensitiveGuard.evaluate(read("$SCRATCHES/id_rsa"), policy)

        assertEquals(Verdict.DENY, verdict.verdict)
        assertEquals(SecurityRule.CREDENTIALS, verdict.rule)
    }

    @Test
    fun `the exemption covers only the outside-project rule, never another user's home`() {
        val foreign = policy.copy(scratchRoot = "/home/bob/.config/JetBrains/IDE/scratches")

        assertNotEquals(Verdict.ALLOW, v(tool("/home/bob/.config/JetBrains/IDE/scratches/x.kt"), foreign))
    }

    private companion object {
        const val SCRATCHES = "/home/me/.config/JetBrains/PyCharm2026.3/scratches"
    }
}
