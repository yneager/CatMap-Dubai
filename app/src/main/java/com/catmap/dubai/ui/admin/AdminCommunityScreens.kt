package com.catmap.dubai.ui.admin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.foundation.lazy.rememberLazyListState
import com.catmap.dubai.ui.components.PagingEffects
import com.catmap.dubai.ui.components.PagingFooter
import com.catmap.dubai.ui.components.rememberPager
import com.catmap.dubai.R
import com.catmap.dubai.data.AppStats
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.SitterProfile
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.isAdminRole
import com.catmap.dubai.data.model.roleLabel
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.Avatar
import com.catmap.dubai.ui.components.CategoryPill
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.PostPhoto
import com.catmap.dubai.ui.components.RatingStars
import com.catmap.dubai.ui.components.SkeletonRow
import com.catmap.dubai.ui.components.StatusBadge
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.BrandGradient

/** Headline numbers for admins, with a small bar chart of posts by category. */
@Composable
fun StatsPanel() {
    val container = LocalContainer.current
    var refresh by remember { mutableIntStateOf(0) }
    val stats by produceState<AppStats?>(null, refresh) { value = container.admin.stats() }
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradient).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_statistics), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh), tint = Color.White) }
            }
            val s = stats
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(s?.users, stringResource(R.string.stat_users), Modifier.weight(1f))
                StatBox(s?.posts, stringResource(R.string.stat_posts), Modifier.weight(1f))
                StatBox(s?.openLost, stringResource(R.string.stat_open_lost), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(s?.reunited, stringResource(R.string.stat_reunited), Modifier.weight(1f))
                StatBox(s?.adopted, stringResource(R.string.stat_adopted), Modifier.weight(1f))
                StatBox(s?.sitters, stringResource(R.string.stat_sitters), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox(s?.bookings, stringResource(R.string.stat_bookings), Modifier.weight(1f))
                StatBox(s?.openReports, stringResource(R.string.stat_open_reports), Modifier.weight(1f))
                StatBox(s?.hiddenPosts, stringResource(R.string.stat_hidden), Modifier.weight(1f))
            }
            if (s != null && s.posts > 0) {
                Text(stringResource(R.string.posts_by_category), style = MaterialTheme.typography.labelLarge, color = Color.White)
                val max = s.byCategory.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                PostCategory.entries.forEach { c ->
                    val count = s.byCategory[c] ?: 0
                    val grow = remember(c) { Animatable(0f) }
                    LaunchedEffect(count) { grow.animateTo(count.toFloat() / max, tween(700, easing = FastOutSlowInEasing)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(c.label), Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
                        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxWidth(grow.value.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Color.White))
                        }
                        Text(count.toString(), Modifier.width(36.dp).padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(value: Long?, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.16f)).padding(10.dp)) {
        Text(value?.toString() ?: "…", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), maxLines = 2)
    }
}

/** Every post, including hidden ones, with quick hide / restore / delete. */
@Composable
fun AdminPostsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var hiddenOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var acting by remember { mutableStateOf<Pair<Post, String>?>(null) }
    val q = query.trim().lowercase()
    val listState = rememberLazyListState()
    val pager = rememberPager<Post>(hiddenOnly, category, key = { it.id }) { container.posts.moderationQuery(hiddenOnly, category) }
    val page by pager.state.collectAsStateWithLifecycle()
    val posts = page.items.takeIf { page.loaded }
    val shown = posts?.filter {
        (category == null || it.category == category) && (!hiddenOnly || it.hidden) &&
            (q.isEmpty() || it.name.lowercase().contains(q) || it.ownerName.lowercase().contains(q) || it.keywords.any { k -> k.startsWith(q) })
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.manage_posts), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(hiddenOnly, { hiddenOnly = !hiddenOnly }, label = { Text(stringResource(R.string.hidden_only)) }) }
                    item { FilterChip(category == null, { category = null }, label = { Text(stringResource(R.string.all)) }) }
                    items(PostCategory.entries, key = { it.key }, contentType = { "category-chip" }) { c -> FilterChip(category == c.key, { category = c.key }, label = { Text(stringResource(c.label)) }) }
                }
            }
            when {
                shown == null -> items(4, contentType = { "loading" }) { SkeletonRow() }
                shown.isEmpty() && page.end -> item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
                else -> items(shown, key = { it.id }, contentType = { "AdminCommunityScreens" }) { p ->
                    AdminPostRow(p, Modifier.animateItem(), onOpen = { nav.navigate(Routes.post(p.id)) }, onEdit = { nav.navigate(Routes.editPost(p.id)) }) { action -> acting = p to action }
                }
            }
            item(contentType = "footer") { PagingFooter(page, pager::loadMore) }
        }
    }
    PagingEffects(pager, page, shown?.size ?: 0, list = listState)
    acting?.let { (post, action) ->
        TextInputDialog(
            stringResource(
                when (action) {
                    "hide" -> R.string.hide_post
                    "unhide" -> R.string.unhide_post
                    else -> R.string.delete_post
                },
            ),
            stringResource(R.string.note_optional), stringResource(R.string.confirm),
            onConfirm = { note ->
                val m = me ?: return@TextInputDialog
                runner.run(R.string.updated) {
                    when (action) {
                        "hide" -> container.posts.setHidden(post, true, m, note)
                        "unhide" -> container.posts.setHidden(post, false, m, note)
                        else -> container.posts.moderatorDelete(post, m, container.images, note)
                    }
                }
            },
            onDismiss = { acting = null },
        )
    }
}

@Composable
private fun AdminPostRow(p: Post, modifier: Modifier, onOpen: () -> Unit, onEdit: () -> Unit, onAction: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ClickableCard(onOpen, modifier) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PostPhoto(p, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(postTitle(p), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("@" + p.ownerName + " · " + Dubai.nameOf(p.neighborhood) + " · " + relativeTime(p.createdAt), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryPill(p.category, solid = false)
                    StatusBadge(p)
                    if (p.hidden) StatusPill(stringResource(R.string.hidden), Color(0xFFC62828))
                }
            }
            Box {
                TextButton(onClick = { menu = true }) { Text(stringResource(R.string.manage)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.edit)) }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text(stringResource(if (p.hidden) R.string.unhide_post else R.string.hide_post)) }, { menu = false; onAction(if (p.hidden) "unhide" else "hide") })
                    DropdownMenuItem({ Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, { menu = false; onAction("delete") })
                }
            }
        }
    }
}

/** All members, with role changes and suspension (admins only). */
@Composable
fun AdminUsersScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var suspendedOnly by rememberSaveable { mutableStateOf(false) }
    var suspending by remember { mutableStateOf<UserProfile?>(null) }
    val q = query.trim().lowercase().removePrefix("@")
    // Usernames are lower case, so a typed name is a prefix query; the rest is checked here.
    val listState = rememberLazyListState()
    val pager = rememberPager<UserProfile>(q, suspendedOnly, key = { it.uid }) { container.admin.usersQuery(q, suspendedOnly) }
    val page by pager.state.collectAsStateWithLifecycle()
    val users = page.items.takeIf { page.loaded }
    val shown = users?.filter { (!suspendedOnly || it.suspended) }
    val isAdmin = isAdminRole(me?.role)

    Scaffold(topBar = { AppTopBar(stringResource(R.string.manage_users), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { FilterChip(suspendedOnly, { suspendedOnly = !suspendedOnly }, label = { Text(stringResource(R.string.suspended_only)) }) }
            item { Text(stringResource(R.string.members_n, shown?.size ?: 0), style = MaterialTheme.typography.labelLarge) }
            when {
                shown == null -> items(5, contentType = { "loading" }) { SkeletonRow() }
                shown.isEmpty() && page.end -> item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.user_not_found)) }
                else -> items(shown, key = { it.uid }, contentType = { "AdminCommunityScreens" }) { u ->
                    var menu by remember { mutableStateOf(false) }
                    ClickableCard(onClick = { nav.navigate(Routes.user(u.uid)) }, modifier = Modifier.animateItem()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.photoId, u.username)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("@" + u.username, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(roleLabel(u.role)) + " · " + relativeTime(u.createdAt), style = MaterialTheme.typography.bodySmall)
                                if (u.suspended) StatusPill(stringResource(R.string.suspended), Color(0xFFC62828))
                            }
                            if (isAdmin && me != null && u.uid != me?.uid) {
                                Box {
                                    TextButton(onClick = { menu = true }) { Text(stringResource(R.string.manage)) }
                                    DropdownMenu(menu, { menu = false }) {
                                        listOf(Roles.USER, Roles.VOLUNTEER, Roles.MODERATOR, Roles.ADMIN).filter { it != u.role }.forEach { role ->
                                            DropdownMenuItem(
                                                { Text(stringResource(R.string.make_x, stringResource(roleLabel(role)))) },
                                                { menu = false; runner.run(R.string.updated) { container.moderation.changeRole(u, role, me ?: return@run) } },
                                            )
                                        }
                                        DropdownMenuItem(
                                            { Text(stringResource(if (u.suspended) R.string.restore_account else R.string.suspend_account), color = MaterialTheme.colorScheme.error) },
                                            {
                                                menu = false
                                                if (u.suspended) runner.run(R.string.updated) { container.admin.setSuspended(u, false, me ?: return@run, "") } else suspending = u
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(contentType = "footer") { PagingFooter(page, pager::loadMore) }
        }
    }
    PagingEffects(pager, page, shown?.size ?: 0, list = listState)
    suspending?.let { u ->
        TextInputDialog(
            stringResource(R.string.suspend_account), stringResource(R.string.reason), stringResource(R.string.suspend_account),
            required = true,
            onConfirm = { note -> runner.run(R.string.updated) { container.admin.setSuspended(u, true, me ?: return@run, note) } },
            onDismiss = { suspending = null },
        )
    }
}

/** Pet-sitter listings, with removal for inappropriate profiles. */
@Composable
fun AdminSittersScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<SitterProfile?>(null) }
    val listState = rememberLazyListState()
    val pager = rememberPager<SitterProfile>(key = { it.uid }) { container.sitting.allSittersQuery() }
    val page by pager.state.collectAsStateWithLifecycle()
    val sitters = page.items.takeIf { page.loaded }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.manage_sitters), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                sitters == null -> items(4, contentType = { "loading" }) { SkeletonRow() }
                sitters.isEmpty() -> item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.no_sitters)) }
                else -> items(sitters, key = { it.uid }, contentType = { "AdminCommunityScreens" }) { s ->
                    ClickableCard(onClick = { nav.navigate(Routes.sitter(s.uid)) }, modifier = Modifier.animateItem()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(s.photoId, s.name)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("@" + s.name, fontWeight = FontWeight.SemiBold)
                                Text(s.headline, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RatingStars(s.rating, size = 12.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(Dubai.nameOf(s.neighborhood), style = MaterialTheme.typography.labelSmall)
                                }
                                if (!s.active) StatusPill(stringResource(R.string.inactive), Color(0xFF757575))
                                if (me?.role == "admin") TextButton(enabled=!runner.busy,onClick={
                                    runner.run(R.string.saved) { container.sitting.setVerified(s,!s.verified,me ?: return@run) }
                                }) { Text(stringResource(if(s.verified) R.string.remove_verification else R.string.verify_sitter)) }
                            }
                            TextButton(onClick = { removing = s }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    removing?.let { s ->
        TextInputDialog(
            stringResource(R.string.remove_sitter), stringResource(R.string.reason), stringResource(R.string.remove),
            onConfirm = { note -> runner.run(R.string.updated) { container.sitting.moderatorRemove(s, me ?: return@run, note) } },
            onDismiss = { removing = null },
        )
    }
}
