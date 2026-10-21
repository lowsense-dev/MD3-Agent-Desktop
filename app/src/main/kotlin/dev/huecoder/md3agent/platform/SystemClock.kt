package dev.huecoder.md3agent.platform

/** Monotonic milliseconds, as android.os.SystemClock gives them (not wall-clock time). */
object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / NANOS_PER_MS

    fun elapsedRealtime(): Long = uptimeMillis()

    private const val NANOS_PER_MS = 1_000_000L
}
