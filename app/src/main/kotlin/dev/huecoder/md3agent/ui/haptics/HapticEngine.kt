package dev.huecoder.md3agent.ui.haptics

enum class Primitive { TICK, CLICK, THUD }

data class Pulse(val primitive: Primitive, val scale: Float, val delayMs: Int = 0)

/** Computers have no vibration motor; the engine keeps the phone app's API so callers stay the same. */
class HapticEngine {
    @Volatile
    var lastPlayedAt: Long = 0L
        private set

    fun play(pulses: List<Pulse>) = Unit
}
