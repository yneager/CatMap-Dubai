package com.catmap.dubai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.ui.components.LocalContainer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
fun NotificationBell(nav: NavHostController) {
    val container = LocalContainer.current
    val uid = container.session.uid
    val unread by remember(uid) {
        if (uid == null) flowOf(0) else container.notifications.notifications(uid).map { list -> list.count { !it.read } }
    }.collectAsStateWithLifecycle(0)
    val description = pluralStringResource(R.plurals.unread_notifications, unread, unread)
    IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
        BadgedBox(
            badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } },
            modifier = androidx.compose.ui.Modifier.semantics { contentDescription = description },
        ) {
            Icon(Icons.Outlined.Notifications, null)
        }
    }
}
