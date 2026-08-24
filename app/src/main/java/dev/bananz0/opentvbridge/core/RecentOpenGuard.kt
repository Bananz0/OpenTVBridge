package dev.bananz0.opentvbridge.core

class RecentOpenGuard(
    private val cooldownMs: Long = 8_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastKey: String? = null
    private var lastOpenedAt: Long = Long.MIN_VALUE

    @Synchronized
    fun shouldOpen(key: String): Boolean {
        val now = clock()
        val normalized = MetadataMatcher.normalize(key)
        if (normalized == lastKey && elapsed(now, lastOpenedAt) < cooldownMs) return false
        lastKey = normalized
        lastOpenedAt = now
        return true
    }

    @Synchronized
    fun reset() {
        lastKey = null
        lastOpenedAt = Long.MIN_VALUE
    }

    private fun elapsed(now: Long, previous: Long): Long =
        if (previous == Long.MIN_VALUE || now < previous) Long.MAX_VALUE else now - previous
}
