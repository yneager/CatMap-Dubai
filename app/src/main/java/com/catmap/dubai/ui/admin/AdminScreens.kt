package com.catmap.dubai.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.ContentReport
import com.catmap.dubai.data.model.MaintenanceIssue
import com.catmap.dubai.data.model.MaintenanceStatus
import com.catmap.dubai.data.model.MergeRequest
import com.catmap.dubai.data.model.ReportReason
import com.catmap.dubai.data.model.RescueStatus
import com.catmap.dubai.data.model.Roles
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.isAdminRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.data.model.roleLabel
import com.catmap.dubai.ui.cats.TimelineItem
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.Avatar
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.statusColor
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf

private data class DashTile(val title: Int, val icon: ImageVector, val count: Int?, val route: String)

@Composable
fun AdminDashboardScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    val isAdmin = isAdminRole(me?.role)
    val reports by remember { container.moderation.openReports() }.collectAsStateWithLifecycle(null)
    val rescues by remember { container.rescues.pending() }.collectAsStateWithLifecycle(null)
    val merges by remember { container.cats.mergeRequests() }.collectAsStateWithLifecycle(null)
    val apps by remember(isAdmin) { if (isAdmin) container.moderation.pendingApplications() else flowOf(emptyList()) }.collectAsStateWithLifecycle(null)
    val maintenance by remember { container.stations.openMaintenance() }.collectAsStateWithLifecycle(null)
    val pending by remember { container.cats.pendingSightings() }.collectAsStateWithLifecycle(null)
    val stations by remember { container.stations.stations() }.collectAsStateWithLifecycle(null)

    val tiles = buildList {
        add(DashTile(R.string.manage_posts, Icons.AutoMirrored.Outlined.Article, null, Routes.ADMIN_POSTS))
        if (isAdmin) add(DashTile(R.string.manage_users, Icons.Outlined.ManageAccounts, null, Routes.ADMIN_USERS))
        add(DashTile(R.string.manage_sitters, com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.SITTER), null, Routes.ADMIN_SITTERS))
        add(DashTile(R.string.content_reports, Icons.Outlined.Flag, reports?.size, Routes.ADMIN_REPORTS))
        add(DashTile(R.string.rescue_reports, com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE), rescues?.size, Routes.ADMIN_RESCUES))
        add(DashTile(R.string.merge_requests, Icons.AutoMirrored.Outlined.CallMerge, merges?.size, Routes.ADMIN_MERGES))
        add(DashTile(R.string.submissions, Icons.Outlined.TaskAlt, pending?.size, Routes.ADMIN_SUBMISSIONS))
        if (isAdmin) add(DashTile(R.string.volunteers, com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.HANDS), apps?.size, Routes.ADMIN_VOLUNTEERS))
        add(DashTile(R.string.maintenance_reports, Icons.Outlined.Build, maintenance?.size, Routes.ADMIN_MAINTENANCE))
        if (isAdmin) add(DashTile(R.string.feeding_stations, com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.STATION), stations?.size, Routes.STATIONS))
        add(DashTile(R.string.moderation_log, Icons.Outlined.History, null, Routes.ADMIN_LOG))
        if (isAdmin) add(DashTile(R.string.recognition_test, com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.CAT), null, Routes.ADMIN_RECOGNITION))
    }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.admin_dashboard), onBack = { nav.popBackStack() }) }) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAdmin) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { StatsPanel() }
            if (isAdmin) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { AnalyticsPanel() }
            if (isAdmin) item { androidx.compose.material3.TextButton(onClick={nav.navigate(Routes.HELP_DIRECTORY)}) {Text(stringResource(R.string.help_directory))} }
            items(tiles,key={it.route},contentType={"dashboard"}) { t ->
                Card(onClick = { nav.navigate(t.route) }, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        if (t.count != null) Text(t.count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(t.title), style = MaterialTheme.typography.bodyMedium)
                        if (t.count != null && t.count > 0 && t.route != Routes.STATIONS && t.route != Routes.ADMIN_POSTS) {
                            Text(stringResource(R.string.needs_review), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Content reports ----------------

@Composable
fun ReportsQueueScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val reports by remember { container.moderation.openReports() }.collectAsStateWithLifecycle(null)
    val me by container.session.profile.collectAsStateWithLifecycle()
    var resolving by remember { mutableStateOf<Pair<ContentReport, String>?>(null) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.content_reports), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reports?.isEmpty() == true) item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
            items(reports.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(labeledFrom(r.reason, ReportReason.OTHER).label), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusPill(targetTypeLabel(r.targetType), MaterialTheme.colorScheme.secondary)
                        }
                        Text(r.targetLabel, style = MaterialTheme.typography.bodyMedium)
                        if (r.details.isNotBlank()) Text("“${r.details}”", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.reported_by_x_time, r.reporterName, relativeTime(r.createdAt)), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            targetRoute(r)?.let { route -> TextButton(onClick = { nav.navigate(route) }) { Text(stringResource(R.string.view)) } }
                            Button(onClick = { resolving = r to "resolved" }) { Text(stringResource(R.string.take_action)) }
                            OutlinedButton(onClick = { resolving = r to "dismissed" }) { Text(stringResource(R.string.dismiss)) }
                        }
                    }
                }
            }
        }
    }
    resolving?.let { (report, status) ->
        TextInputDialog(
            stringResource(if (status == "resolved") R.string.take_action else R.string.dismiss),
            stringResource(R.string.resolution_note), stringResource(R.string.confirm),
            onConfirm = { note ->
                val m = me ?: return@TextInputDialog
                runner.run {
                    if (status == "resolved") applyAction(container, report, m, note)
                    container.moderation.closeReport(report, status, note, m)
                }
            },
            onDismiss = { resolving = null },
        )
    }
}

private fun targetRoute(r: ContentReport): String? = when (r.targetType) {
    "cat" -> Routes.cat(r.targetId)
    "sighting" -> Routes.sighting(r.targetId)
    "photo" -> Routes.photo(r.targetId)
    "rescue" -> Routes.rescue(r.targetId)
    "user" -> Routes.user(r.targetId)
    "post" -> Routes.post(r.targetId)
    "sitter" -> Routes.sitter(r.targetId)
    else -> null
}

/** "Take action" hides the reported cat, rejects the sighting or removes the photo. */
private suspend fun applyAction(container: com.catmap.dubai.AppContainer, r: ContentReport, m: UserProfile, note: String) {
    when (r.targetType) {
        "cat" -> container.cats.cat(r.targetId)?.let { container.cats.setCatHidden(it, true, m, note) }
        "sighting" -> container.cats.sightingFlow(r.targetId).firstOrNull()?.let { container.cats.reviewSighting(it, false, m) }
        "photo" -> {
            container.images.delete(r.targetId)
            container.moderation.log(m, "photo_removed", "photo", r.targetId, r.targetLabel, note)
        }
        "post" -> container.posts.post(r.targetId)?.let { if (!it.hidden) container.posts.setHidden(it, true, m, note) }
        // Suspending an account is an admin decision; moderators can still dismiss or resolve the report.
        "user" -> if (isAdminRole(m.role)) container.users.profile(r.targetId)?.let { container.admin.setSuspended(it, true, m, note) }
    }
}

// ---------------- Merge requests ----------------

@Composable
fun MergeQueueScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val requests by remember { container.cats.mergeRequests() }.collectAsStateWithLifecycle(null)
    val cats by remember { container.cats.allCats() }.collectAsStateWithLifecycle(emptyList())
    val me by container.session.profile.collectAsStateWithLifecycle()
    var deciding by remember { mutableStateOf<Pair<MergeRequest, Boolean>?>(null) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.merge_requests), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (requests?.isEmpty() == true) item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
            items(requests.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { r ->
                val primary = cats.firstOrNull { it.id == r.primaryCatId }
                val duplicate = cats.firstOrNull { it.id == r.duplicateCatId }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MergeCat(stringResource(R.string.keep), r.primaryName, primary, Modifier.weight(1f)) { nav.navigate(Routes.cat(r.primaryCatId)) }
                            MergeCat(stringResource(R.string.merge_into_keep), r.duplicateName, duplicate, Modifier.weight(1f)) { nav.navigate(Routes.cat(r.duplicateCatId)) }
                        }
                        Text(stringResource(R.string.ai_similarity_x, (r.similarity * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                        if (r.reason.isNotBlank()) Text("“${r.reason}”", style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.reported_by_x_time, r.requestedByName, relativeTime(r.createdAt)), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { deciding = r to true }, enabled = primary != null && duplicate != null) { Text(stringResource(R.string.approve_merge)) }
                            OutlinedButton(onClick = { deciding = r to false }) { Text(stringResource(R.string.reject)) }
                        }
                    }
                }
            }
        }
    }
    deciding?.let { (r, approve) ->
        TextInputDialog(
            stringResource(if (approve) R.string.approve_merge else R.string.reject),
            stringResource(R.string.note_optional), stringResource(R.string.confirm),
            onConfirm = { note ->
                val m = me ?: return@TextInputDialog
                runner.run { if (approve) container.cats.approveMerge(r, m, note) else container.cats.rejectMerge(r, m, note) }
            },
            onDismiss = { deciding = null },
        )
    }
}

@Composable
private fun MergeCat(label: String, name: String, cat: Cat?, modifier: Modifier, onClick: () -> Unit) {
    ClickableCard(onClick, modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            CatThumb(cat?.coverPhotoId ?: "", labeledFrom(cat?.coatColor, CoatColor.UNKNOWN), Modifier.size(80.dp).clip(RoundedCornerShape(10.dp)))
            Text(name, fontWeight = FontWeight.SemiBold)
            if (cat != null) Text(stringResource(R.string.n_sightings, cat.sightingCount), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------- Volunteers & roles (admins) ----------------

private enum class VolTab(val label: Int) { APPLICATIONS(R.string.applications), VOLUNTEERS(R.string.volunteers), STAFF(R.string.staff) }

@Composable
fun VolunteersScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(VolTab.APPLICATIONS) }
    val apps by remember { container.moderation.pendingApplications() }.collectAsStateWithLifecycle(null)
    val volunteers by remember { container.users.usersWithRole(Roles.VOLUNTEER) }.collectAsStateWithLifecycle(emptyList())
    val mods by remember { container.users.usersWithRole(Roles.MODERATOR) }.collectAsStateWithLifecycle(emptyList())
    val admins by remember { container.users.usersWithRole(Roles.ADMIN) }.collectAsStateWithLifecycle(emptyList())
    var search by rememberSaveable { mutableStateOf("") }
    // Follow the found member live, so a role change shows up on the search result straight away.
    var foundUid by rememberSaveable { mutableStateOf<String?>(null) }
    val found by remember(foundUid) { foundUid?.let { container.users.profileFlow(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)

    Scaffold(topBar = { AppTopBar(stringResource(R.string.volunteers), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                VolTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(stringResource(t.label)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    VolTab.APPLICATIONS -> {
                        if (apps?.isEmpty() == true) item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
                        items(apps.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { a ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("@" + a.username, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.area_x, Dubai.nameOf(a.area)), style = MaterialTheme.typography.bodySmall)
                                    Text(a.motivation)
                                    if (a.experience.isNotBlank()) Text(stringResource(R.string.experience) + ": " + a.experience, style = MaterialTheme.typography.bodySmall)
                                    Text(relativeTime(a.createdAt), style = MaterialTheme.typography.labelSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { me?.let { m -> runner.run { container.moderation.decideApplication(a, true, m) } } }) { Text(stringResource(R.string.approve)) }
                                        OutlinedButton(onClick = { me?.let { m -> runner.run { container.moderation.decideApplication(a, false, m) } } }) { Text(stringResource(R.string.reject)) }
                                    }
                                }
                            }
                        }
                    }
                    VolTab.VOLUNTEERS -> {
                        if (volunteers.isEmpty()) item { EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.HANDS), stringResource(R.string.no_volunteers)) }
                        items(volunteers, key = { it.uid }, contentType = { "AdminScreens" }) { u -> UserRoleRow(u, me, nav) }
                    }
                    VolTab.STAFF -> {
                        item {
                            SectionHeader(stringResource(R.string.find_user))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    runner.run {
                                        foundUid = null
                                        foundUid = (container.users.findByUsername(search) ?: throw com.catmap.dubai.data.AppException(R.string.user_not_found)).uid
                                    }
                                }) { Text(stringResource(R.string.search)) }
                            }
                            found?.let { UserRoleRow(it, me, nav) }
                            SectionHeader(stringResource(R.string.staff))
                        }
                        items(admins + mods, key = { it.uid }, contentType = { "AdminScreens" }) { u -> UserRoleRow(u, me, nav) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRoleRow(u: UserProfile, me: UserProfile?, nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    var menu by remember { mutableStateOf(false) }
    ClickableCard(onClick = { nav.navigate(Routes.user(u.uid)) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.photoId, u.username)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("@" + u.username, fontWeight = FontWeight.SemiBold)
                Text(stringResource(roleLabel(u.role)), style = MaterialTheme.typography.bodySmall)
            }
            if (me != null && u.uid != me.uid) {
                TextButton(onClick = { menu = true }) { Text(stringResource(R.string.change_role)) }
                androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                    listOf(Roles.USER, Roles.VOLUNTEER, Roles.MODERATOR, Roles.ADMIN).filter { it != u.role }.forEach { role ->
                        androidx.compose.material3.DropdownMenuItem(
                            { Text(stringResource(roleLabel(role))) },
                            { menu = false; runner.run { container.moderation.changeRole(u, role, me) } },
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Maintenance reports ----------------

@Composable
fun MaintenanceQueueScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val reports by remember { container.stations.openMaintenance() }.collectAsStateWithLifecycle(null)
    val me by container.session.profile.collectAsStateWithLifecycle()
    var updating by remember { mutableStateOf<Pair<com.catmap.dubai.data.model.MaintenanceReport, MaintenanceStatus>?>(null) }
    val isAdmin = isAdminRole(me?.role)

    Scaffold(topBar = { AppTopBar(stringResource(R.string.maintenance_reports), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reports?.isEmpty() == true) item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
            items(reports.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { r ->
                val st = labeledFrom(r.status, MaintenanceStatus.OPEN)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(labeledFrom(r.issue, MaintenanceIssue.OTHER).label), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            StatusPill(stringResource(st.label), statusColor(st.color))
                        }
                        TextButton(onClick = { nav.navigate(Routes.station(r.stationId)) }) { Text(r.stationName) }
                        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.reported_by_x_time, r.username, relativeTime(r.createdAt)), style = MaterialTheme.typography.labelSmall)
                        if (isAdmin) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (st == MaintenanceStatus.OPEN) OutlinedButton(onClick = { updating = r to MaintenanceStatus.IN_PROGRESS }) { Text(stringResource(R.string.mstatus_in_progress)) }
                                Button(onClick = { updating = r to MaintenanceStatus.RESOLVED }) { Text(stringResource(R.string.mark_resolved)) }
                            }
                        }
                    }
                }
            }
        }
    }
    updating?.let { (r, status) ->
        TextInputDialog(
            stringResource(status.label), stringResource(R.string.note_optional), stringResource(R.string.confirm),
            onConfirm = { note -> me?.let { m -> runner.run { container.stations.updateMaintenance(r, status.key, note, m) } } },
            onDismiss = { updating = null },
        )
    }
}

// ---------------- Community submissions ----------------

@Composable
fun SubmissionsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val pending by remember { container.cats.pendingSightings() }.collectAsStateWithLifecycle(null)
    val weekAgo = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val newCats by remember { container.cats.recentCats(weekAgo) }.collectAsStateWithLifecycle(emptyList())
    val me by container.session.profile.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.submissions), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.pending_sightings_n, pending?.size ?: 0)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.new_cats_week_n, newCats.size)) })
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                if (tab == 0) {
                    if (pending?.isEmpty() == true) item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.queue_empty)) }
                    items(pending.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { s ->
                        Column {
                            Text(s.catName + if (s.newCat) " · " + stringResource(R.string.first_sighting) else "", Modifier.padding(start = 44.dp, top = 8.dp), fontWeight = FontWeight.SemiBold)
                            TimelineItem(s) { nav.navigate(Routes.sighting(s.id)) }
                            Row(Modifier.padding(start = 44.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { me?.let { m -> runner.run { container.cats.reviewSighting(s, true, m) } } }) { Text(stringResource(R.string.approve)) }
                                OutlinedButton(onClick = { me?.let { m -> runner.run { container.cats.reviewSighting(s, false, m) } } }) { Text(stringResource(R.string.reject)) }
                            }
                        }
                    }
                } else {
                    items(newCats, key = { it.id }, contentType = { "AdminScreens" }) { cat ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                            com.catmap.dubai.ui.cats.CatCard(cat) { nav.navigate(Routes.cat(cat.id)) }
                            Text(stringResource(R.string.registered_by_x_time, cat.createdByName, dateTime(cat.createdAt)), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Moderation log ----------------

/** Localized text for a stored action key such as "merge_approved", "role_moderator" or "rescue_in_progress". */
@Composable
private fun actionLabel(action: String): String {
    val suffix = action.substringAfter('_')
    return when {
        action.startsWith("role_") -> stringResource(R.string.log_role, stringResource(roleLabel(suffix)))
        action.startsWith("rescue_") -> stringResource(R.string.log_rescue, stringResource(labeledFrom(suffix, RescueStatus.SUBMITTED).label))
        action.startsWith("maintenance_") -> stringResource(R.string.log_maintenance, stringResource(labeledFrom(suffix, MaintenanceStatus.OPEN).label))
        else -> when (action) {
            "station_created" -> R.string.log_station_created
            "station_updated" -> R.string.log_station_updated
            "station_deleted" -> R.string.log_station_deleted
            "stations_seeded" -> R.string.log_stations_seeded
            "report_resolved" -> R.string.log_report_resolved
            "report_dismissed" -> R.string.log_report_dismissed
            "volunteer_approved" -> R.string.log_volunteer_approved
            "volunteer_rejected" -> R.string.log_volunteer_rejected
            "photo_removed" -> R.string.log_photo_removed
            "merge_approved" -> R.string.log_merge_approved
            "merge_rejected" -> R.string.log_merge_rejected
            "sighting_approved" -> R.string.log_sighting_approved
            "sighting_rejected" -> R.string.log_sighting_rejected
            "sighting_deleted" -> R.string.log_sighting_deleted
            "cat_hidden" -> R.string.log_cat_hidden
            "cat_restored" -> R.string.log_cat_restored
            else -> null
        }?.let { stringResource(it) } ?: action.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

/** Localized name of a stored target type ("cat", "sighting", …) for the log and the reports queue. */
@Composable
private fun targetTypeLabel(type: String): String = when (type) {
    "cat" -> stringResource(R.string.type_cat)
    "sighting" -> stringResource(R.string.sighting)
    "photo" -> stringResource(R.string.photo)
    "rescue" -> stringResource(R.string.rescue_report)
    "station" -> stringResource(R.string.type_station)
    "user" -> stringResource(R.string.type_member)
    "post" -> stringResource(R.string.type_post)
    "sitter" -> stringResource(R.string.type_sitter)
    else -> type
}

@Composable
fun ModLogScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val actions by remember { container.moderation.actions() }.collectAsStateWithLifecycle(null)
    Scaffold(topBar = { AppTopBar(stringResource(R.string.moderation_log), onBack = { nav.popBackStack() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (actions?.isEmpty() == true) item { EmptyState(Icons.Outlined.History, stringResource(R.string.no_actions)) }
            items(actions.orEmpty(), key = { it.id }, contentType = { "AdminScreens" }) { a ->
                Column(Modifier.fillMaxWidth()) {
                    Text(actionLabel(a.action), fontWeight = FontWeight.SemiBold)
                    Text(listOf(targetTypeLabel(a.targetType), a.targetLabel).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (a.note.isNotBlank()) Text("“${a.note}”", style = MaterialTheme.typography.bodySmall)
                    Text("@" + a.moderatorName + " · " + dateTime(a.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
