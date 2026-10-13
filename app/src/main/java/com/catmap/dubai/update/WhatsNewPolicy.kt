package com.catmap.dubai.update

object WhatsNewPolicy {
    fun shouldShow(lastSeen: String, current: String, installedAt: Long, updatedAt: Long): Boolean =
        if (lastSeen.isBlank()) updatedAt > installedAt + 1_000 else lastSeen != current
}
