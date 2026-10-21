package dev.huecoder.md3agent.data.attachments

/** Least-recently-used cache capped by total weight, not entry count. Values heavier than the cap are not cached. */
internal class SizedLruCache<K : Any, V : Any>(private val maxWeight: Long, private val weightOf: (V) -> Long) {
    private val entries = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)
    private var weight = 0L

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        val added = weightOf(value)
        if (added > maxWeight) return
        entries.put(key, value)?.let { weight -= weightOf(it) }
        weight += added
        val eldest = entries.entries.iterator()
        while (weight > maxWeight && eldest.hasNext()) {
            weight -= weightOf(eldest.next().value)
            eldest.remove()
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
