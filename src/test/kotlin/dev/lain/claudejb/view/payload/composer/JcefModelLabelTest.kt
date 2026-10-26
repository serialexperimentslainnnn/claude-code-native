package dev.lain.claudejb.view.payload.composer

import dev.lain.claudejb.model.protocol.models.ModelInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JcefModelLabelTest {

    private fun opus() = ModelInfo("opus[1m]", "Opus (1M context)", "Opus 5 with 1M context · Best for everyday, complex tasks")
    private fun sonnet() = ModelInfo("sonnet", "Sonnet", "Sonnet 5 · Efficient for routine tasks")
    private fun haiku() = ModelInfo("haiku", "Haiku", "Haiku 4.5 · Fastest for quick answers")

    @Test
    fun `label prefers the description head so the version is shown`() {
        assertEquals("Opus 5 with 1M context", JcefModelLabels.modelDisplayLabel(opus()))
        assertEquals("Sonnet 5", JcefModelLabels.modelDisplayLabel(sonnet()))
        assertEquals("Haiku 4.5", JcefModelLabels.modelDisplayLabel(haiku()))
    }

    @Test
    fun `a description without the model name never becomes the label`() {
        assertEquals("Haiku 4.5", JcefModelLabels.modelDisplayLabel(ModelInfo("haiku", "Haiku 4.5", "Fastest for quick answers")))
        assertEquals(
            "Sonnet 5",
            JcefModelLabels.modelDisplayLabel(ModelInfo("claude-sonnet-5", "Sonnet", "For complex work and everyday tasks")),
        )
        assertEquals("Sonnet", JcefModelLabels.modelDisplayLabel(ModelInfo("sonnet", "Sonnet", "For complex work and everyday tasks")))
    }

    @Test
    fun `the named segment wins wherever it sits in the description`() {
        assertEquals("Opus 5.5", JcefModelLabels.modelDisplayLabel(ModelInfo("opus", "Opus", "Best for complex work · Opus 5.5")))
    }

    @Test
    fun `a context size is not mistaken for a version`() {
        assertEquals("Opus 4.8", JcefModelLabels.modelDisplayLabel(ModelInfo("claude-opus-4-8[1m]", "Opus (1M context)", "For long sessions")))
    }

    @Test
    fun `label falls back to displayName when there is no description`() {
        assertEquals("Sonnet", JcefModelLabels.modelDisplayLabel(ModelInfo("sonnet", "Sonnet", "")))
    }

    @Test
    fun `label falls back to the derived id when neither description nor displayName is present`() {
        assertEquals("Opus 4.8", JcefModelLabels.modelDisplayLabel(ModelInfo("claude-opus-4-8", "", "")))
    }

    @Test
    fun `deriveModelLabel splits family and version from a canonical id`() {
        assertEquals("Opus 4.8", JcefModelLabels.deriveModelLabel("claude-opus-4-8"))
        assertEquals("Sonnet 5", JcefModelLabels.deriveModelLabel("claude-sonnet-5"))
    }

    @Test
    fun `deriveModelLabel strips an alias bracket suffix — no version to invent`() {
        assertEquals("Opus", JcefModelLabels.deriveModelLabel("opus[1m]"))
        assertEquals("Sonnet", JcefModelLabels.deriveModelLabel("sonnet"))
    }

    @Test
    fun `deriveModelLabel never emits a hardcoded default version`() {
        assertEquals("Claude", JcefModelLabels.deriveModelLabel(""))
        assertEquals("Default", JcefModelLabels.deriveModelLabel("default"))
    }
}
