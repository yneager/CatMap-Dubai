package com.catmap.dubai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.model.Post

/** Pure header shared with the detail screen and deterministic screenshot fixtures. */
@Composable
fun PostDetailHeader(post: Post) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryPill(post.category)
        if (post.reward > 0) RewardPill(post.reward)
        StatusBadge(post)
        if (post.hidden) StatusPill(stringResource(R.string.hidden), MaterialTheme.colorScheme.error)
    }
    Text(postTitle(post), style = MaterialTheme.typography.headlineMedium)
    if (post.name.isNotBlank()) Text(postHeadline(post), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
