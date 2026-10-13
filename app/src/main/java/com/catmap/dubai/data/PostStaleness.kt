package com.catmap.dubai.data

import com.catmap.dubai.data.model.Post

object PostStaleness {
    const val DAY = 86_400_000L
    fun activity(post: Post) = maxOf(post.lastActivityAt, post.updatedAt, post.createdAt)
    fun searchPost(post: Post) = post.category in listOf("lost", "found") && post.status == "open"
    fun needsReminder(post: Post, now: Long) = searchPost(post) && activity(post) > 0 && now - activity(post) >= 14 * DAY
    fun hiddenByDefault(post: Post, now: Long) = post.status == "open" && activity(post) > 0 && now - activity(post) >= 90 * DAY
    fun reminderId(post: Post, now: Long) = "stale_${post.id}_${now / (14 * DAY)}"
}
