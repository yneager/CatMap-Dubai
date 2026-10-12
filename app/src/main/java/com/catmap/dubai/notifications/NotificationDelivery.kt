package com.catmap.dubai.notifications

import com.catmap.dubai.data.model.AppNotification

/** IDs avoid device-clock differences and notifications sharing the same timestamp. */
internal object NotificationDelivery {
    const val MAX_REMEMBERED = 500

    fun fresh(inbox: List<AppNotification>, delivered: Set<String>?): List<AppNotification> =
        if (delivered == null) emptyList() // First snapshot establishes a baseline, even if empty.
        else inbox.filter { !it.read && it.id.isNotBlank() && it.id !in delivered }
            .distinctBy { it.id }.sortedBy { it.createdAt }

    fun checkpoint(inbox: List<AppNotification>, delivered: Set<String>?): Set<String> {
        // Move currently visible IDs to the end so trimming never forgets the current inbox.
        val visible = inbox.map { it.id }.filter { it.isNotBlank() }.toSet()
        return ((delivered.orEmpty() - visible) + visible).toList().takeLast(MAX_REMEMBERED).toSet()
    }
}
