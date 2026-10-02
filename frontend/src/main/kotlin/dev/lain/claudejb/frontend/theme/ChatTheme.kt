package dev.lain.claudejb.frontend.theme

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import java.awt.Color

object ChatTheme {

    private const val VIBE_SATURATION = 0.85f
    private const val VIBE_BRIGHTNESS = 1.0f

    private val CORAL = Color(0xD97757)

    private val VIBE_ACCENT = Color(Color.HSBtoRGB(0f, VIBE_SATURATION, VIBE_BRIGHTNESS))

    private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<() -> Unit>()

    @Volatile var vibeMode: Boolean = false
        private set

    fun setVibeMode(on: Boolean) {
        if (vibeMode == on) return
        vibeMode = on
        listeners.forEach { it() }
    }

    fun onChange(parent: Disposable, listener: () -> Unit) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    val ACCENT: Color get() = if (vibeMode) VIBE_ACCENT else CORAL
}
