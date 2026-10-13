package com.catmap.dubai.ui.profile

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.AppErrors
import com.catmap.dubai.data.Loaded
import com.catmap.dubai.data.loaded
import kotlinx.coroutines.CancellationException
import com.catmap.dubai.data.model.Sighting
import com.catmap.dubai.data.model.Stats
import com.catmap.dubai.data.model.UserProfile
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.roleLabel
import com.catmap.dubai.gamification.Badge
import com.catmap.dubai.gamification.Levels
import com.catmap.dubai.ui.cats.CatCard
import com.catmap.dubai.ui.cats.TimelineItem
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.Avatar
import com.catmap.dubai.ui.components.DropdownField
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatTile
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class ProfileTab(val label: Int) { POSTS(R.string.tab_posts), SIGHTINGS(R.string.tab_my_sightings), CATS(R.string.tab_my_cats), FOLLOWING(R.string.following) }

/** The signed-in user's profile (tab), or another user's public profile when [uid] is given. */
@Composable
fun ProfileScreen(nav: NavHostController, uid: String? = null) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    val isSelf = uid == null || uid == me?.uid
    val otherState by remember(uid) {
        if (uid != null && !isSelf) container.users.profileFlow(uid).loaded() else flowOf<Loaded<UserProfile>?>(null)
    }.collectAsStateWithLifecycle(null)
    val profile = if (isSelf) me else otherState?.value
    val targetUid = profile?.uid ?: ""
    val sightings by remember(targetUid) { if (targetUid.isEmpty()) flowOf(emptyList()) else container.cats.sightingsForUser(targetUid) }.collectAsStateWithLifecycle(emptyList())
    val myCats by remember(targetUid) { if (targetUid.isEmpty()) flowOf(emptyList()) else container.cats.catsContributedBy(targetUid) }.collectAsStateWithLifecycle(emptyList())
    val followed by remember(profile?.followedCats) { container.cats.catsByIds(profile?.followedCats ?: emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val posts by remember(targetUid, isSelf) {
        when {
            targetUid.isEmpty() -> flowOf(emptyList())
            isSelf -> container.posts.postsBy(targetUid)
            else -> container.posts.publicPostsBy(targetUid)
        }
    }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(ProfileTab.POSTS) }
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                if (isSelf) stringResource(R.string.tab_profile) else profile?.let { "@" + it.username } ?: "",
                onBack = if (isSelf && uid == null) null else ({ nav.popBackStack() }),
            ) {
                if (isSelf) {
                    NotificationBell(nav)
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                } else if (profile != null) {
                    TextButton(onClick = { dialog = "report" }) { Text(stringResource(R.string.report_content)) }
                }
            }
        },
    ) { padding ->
        val p = profile
        if (p == null) {
            // A deleted account (e.g. opened from an old report or notification) shows a message, not a spinner.
            if (!isSelf && otherState != null) EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.profile_not_found), Modifier.padding(padding))
            else LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val visibleSightings = if (isSelf) sightings else sightings.filter { it.status != "rejected" }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ProfileHeader(p, isSelf, nav, onVolunteer = { dialog = "volunteer" }) }
            if (isSelf) item { QuickLinks(nav) }
            item {
                if (isSelf || p.showStatsPublicly) {
                    StatsDashboard(p, visibleSightings, onBadges = { if (isSelf) nav.navigate(Routes.ACHIEVEMENTS) })
                } else {
                    Text(stringResource(R.string.stats_private), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    ProfileTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(stringResource(t.label), maxLines = 1) }) }
                }
            }
            when (tab) {
                ProfileTab.POSTS -> {
                    if (posts.isEmpty()) item { EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.no_posts_profile)) }
                    items(posts, key = { "post" + it.id }, contentType = { "ProfileScreens" }) { post ->
                        com.catmap.dubai.ui.components.PostCard(
                            post, saved = false, onFavorite = {}, onClick = { nav.navigate(Routes.post(post.id)) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
                ProfileTab.SIGHTINGS -> {
                    if (visibleSightings.isEmpty()) item { EmptyState(Icons.Outlined.CameraAlt, stringResource(R.string.no_sightings_yet)) }
                    items(visibleSightings, key = { it.id }, contentType = { "ProfileScreens" }) { s ->
                        Column {
                            Text(s.catName, Modifier.padding(start = 44.dp, top = 8.dp), fontWeight = FontWeight.SemiBold)
                            TimelineItem(s) { nav.navigate(Routes.sighting(s.id)) }
                        }
                    }
                }
                ProfileTab.CATS -> {
                    if (myCats.isEmpty()) item { EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.no_contributed_cats)) }
                    items(myCats, key = { it.id }, contentType = { "ProfileScreens" }) { cat ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { CatCard(cat) { nav.navigate(Routes.cat(cat.id)) } }
                    }
                }
                ProfileTab.FOLLOWING -> {
                    if (followed.isEmpty()) item { EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.not_following_any)) }
                    items(followed, key = { it.id }, contentType = { "ProfileScreens" }) { cat ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { CatCard(cat, following = true) { nav.navigate(Routes.cat(cat.id)) } }
                    }
                }
            }
        }
        when (dialog) {
            "volunteer" -> VolunteerDialog(p) { dialog = null }
            "report" -> ReportContentDialog("user", p.uid, p.username) { dialog = null }
        }
    }
}

private data class QuickLink(val icon: Int, val label: Int, val route: String)

/** Shortcuts to everything the person owns or follows. */
@Composable
private fun QuickLinks(nav: NavHostController) {
    val links = listOf(
        QuickLink(CatMapIcons.NOTE, R.string.my_posts, Routes.MY_POSTS),
        QuickLink(CatMapIcons.ADOPTION, R.string.favorites, Routes.FAVORITES),
        QuickLink(CatMapIcons.ADOPTION, R.string.adoption_requests, Routes.ADOPTION_REQUESTS),
        QuickLink(CatMapIcons.CALENDAR, R.string.bookings, Routes.bookings()),
        QuickLink(CatMapIcons.SITTER, R.string.sitter_profile, Routes.SITTER_EDIT),
        QuickLink(CatMapIcons.BELL, R.string.area_alerts, Routes.AREA_ALERTS),
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { l ->
                    Card(
                        onClick = { nav.navigate(l.route) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CatMapIcon(l.icon)
                            Text(stringResource(l.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(p: UserProfile, isSelf: Boolean, nav: NavHostController, onVolunteer: () -> Unit) {
    val level = Levels.of(p.points)
    val next = Levels.next(p.points)
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(p.photoId, p.displayName.ifBlank { p.username }, size = 96.dp)
        Text(p.displayName.ifBlank { p.username }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("@" + p.username, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill(stringResource(roleLabel(p.role)), MaterialTheme.colorScheme.secondary)
        if (p.bio.isNotBlank()) Text(p.bio, textAlign = TextAlign.Center)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.level_n, level.number) + " · " + stringResource(level.title), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.points_n, p.points), style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(progress = { Levels.progress(p.points) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                Text(
                    if (next != null) stringResource(R.string.points_to_next, next.minPoints - p.points, stringResource(next.title)) else stringResource(R.string.max_level),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (isSelf) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate(Routes.PROFILE_EDIT) }) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit_profile))
                }
                if (isModeratorRole(p.role)) {
                    FilledTonalButton(onClick = { nav.navigate(Routes.ADMIN) }) {
                        Icon(Icons.Default.AdminPanelSettings, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.admin_dashboard))
                    }
                }
            }
            when (p.volunteerStatus) {
                "none", "rejected" -> if (p.role == "user") {
                    TextButton(onClick = onVolunteer) {
                        Icon(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.HANDS), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (p.volunteerStatus == "rejected") R.string.apply_again_volunteer else R.string.become_volunteer))
                    }
                }
                "applied" -> Text(stringResource(R.string.volunteer_pending), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatsDashboard(p: UserProfile, sightings: List<Sighting>, onBadges: () -> Unit) {
    fun stat(key: String) = (p.stats[key] ?: 0L).toString()
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionHeader(stringResource(R.string.statistics))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stat(Stats.SIGHTINGS), stringResource(R.string.stat_sightings), Modifier.weight(1f), Icons.Outlined.CameraAlt)
            StatTile(sightings.map { it.catId }.distinct().size.toString(), stringResource(R.string.stat_cats_spotted), Modifier.weight(1f), com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW))
            StatTile(stat(Stats.APPROVED_SIGHTINGS), stringResource(R.string.stat_approved), Modifier.weight(1f), Icons.Outlined.TaskAlt)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stat(Stats.CATS_REGISTERED), stringResource(R.string.stat_registered), Modifier.weight(1f), com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW))
            StatTile(stat(Stats.PHOTOS), stringResource(R.string.stat_photos), Modifier.weight(1f), Icons.Outlined.Photo)
            StatTile(stat(Stats.RESCUE_REPORTS), stringResource(R.string.stat_rescues), Modifier.weight(1f), com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.RESCUE))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stat(Stats.CHECKINS), stringResource(R.string.stat_checkins), Modifier.weight(1f), com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.STATION))
            StatTile(p.neighborhoods.size.toString(), stringResource(R.string.stat_areas), Modifier.weight(1f), Icons.Outlined.Place)
            StatTile(p.badges.size.toString(), stringResource(R.string.stat_badges), Modifier.weight(1f).clickable(onClick = onBadges), com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.BADGE_PAW))
        }
        MonthlyChart(sightings)
        if (p.badges.isNotEmpty()) {
            SectionHeader(stringResource(R.string.badges)) { TextButton(onClick = onBadges) { Text(stringResource(R.string.see_all)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.badges.mapNotNull { Badge.fromKey(it) }.take(6).forEach { badge ->
                    val title = stringResource(badge.title)
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer).semantics { contentDescription = title },
                        contentAlignment = Alignment.Center,
                    ) { CatMapIcon(badge.icon,contentDescription=stringResource(badge.title)) }
                }
            }
        }
    }
}

/** Sightings per month for the last six months. */
@Composable
private fun MonthlyChart(sightings: List<Sighting>) {
    val months = remember(sightings) {
        val cal = Calendar.getInstance()
        (5 downTo 0).map { back ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -back) }
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            val count = sightings.count {
                cal.timeInMillis = it.seenAt
                cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == m
            }
            SimpleDateFormat("MMM", Locale.getDefault()).format(c.time) to count
        }
    }
    val max = (months.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val summary = months.joinToString { "${it.first}: ${it.second}" }
    SectionHeader(stringResource(R.string.activity_6_months))
    Row(
        Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { (label, count) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    drawRoundRect(track, cornerRadius = CornerRadius(6.dp.toPx()))
                    val h = size.height * count / max
                    drawRoundRect(bar, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(6.dp.toPx()))
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VolunteerDialog(p: UserProfile, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    var motivation by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("") }
    var area by rememberSaveable { mutableStateOf(Dubai.neighborhoods.first().key) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.become_volunteer)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.volunteer_intro), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(motivation, { motivation = it.take(500) }, label = { Text(stringResource(R.string.why_volunteer)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(experience, { experience = it.take(500) }, label = { Text(stringResource(R.string.experience)) }, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    stringResource(R.string.area_you_cover),
                    Dubai.neighborhoods.sortedBy { it.displayName() }.map { it.key },
                    area, { area = it },
                ) { Dubai.nameOf(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = motivation.isNotBlank() && !runner.busy, onClick = {
                runner.run(R.string.application_sent, onSuccess = onDismiss) {
                    container.moderation.applyAsVolunteer(p, motivation, experience, area)
                }
            }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun EditProfileScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.edit_profile), onBack = { nav.popBackStack() }) }) { padding ->
        val p = me
        if (p == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        var displayName by rememberSaveable { mutableStateOf(p.displayName) }
        var username by rememberSaveable { mutableStateOf(p.username) }
        var bio by rememberSaveable { mutableStateOf(p.bio) }
        var photoId by rememberSaveable { mutableStateOf(p.photoId) }
        var uploading by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch {
                uploading = true
                runCatching {
                    val photo = withContext(Dispatchers.IO) { ImageUtils.process(context, uri) }
                    container.images.upload(p.uid, photo)
                }.onSuccess { photoId = it }.onFailure {
                    if (it is CancellationException) throw it
                    AppErrors.report(it)
                }
                uploading = false
            }
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Avatar(photoId, displayName.ifBlank { username }, size = 110.dp)
                if (uploading) LoadingBox(Modifier.size(110.dp))
            }
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(R.string.change_photo))
            }
            OutlinedTextField(displayName, { displayName = it.take(60) }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                username, { username = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(20) },
                label = { Text(stringResource(R.string.username)) }, supportingText = { Text(stringResource(R.string.username_rules)) },
                singleLine = true, keyboardOptions = KeyboardOptions.Default, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(bio, { bio = it.take(300) }, label = { Text(stringResource(R.string.bio)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = !runner.busy && !uploading && username.length >= 3,
                onClick = {
                    runner.run(R.string.saved, onSuccess = { nav.popBackStack() }) {
                        container.users.updateProfile(p, username, displayName, bio, photoId)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun AchievementsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar(stringResource(R.string.achievements), onBack = { nav.popBackStack() }) }) { padding ->
        val p = me
        if (p == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(stringResource(R.string.contribution_counter), style = MaterialTheme.typography.labelLarge)
                        Text(stringResource(R.string.points_n, p.points), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val level = Levels.of(p.points)
                        Text(stringResource(R.string.level_n, level.number) + " · " + stringResource(level.title))
                        LinearProgressIndicator(progress = { Levels.progress(p.points) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text(stringResource(R.string.badges_earned, p.badges.size, Badge.entries.size), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            items(Badge.entries.sortedByDescending { it.key in p.badges }, key = { it.key }, contentType = { "badge" }) { badge ->
                val earned = badge.key in p.badges
                val progress = badge.progress(p).coerceAtMost(badge.threshold)
                Card(colors = CardDefaults.cardColors(if (earned) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(if (earned) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { CatMapIcon(if (earned) badge.icon else CatMapIcons.LOCK,contentDescription=stringResource(badge.title)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(badge.title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(badge.description), style = MaterialTheme.typography.bodySmall)
                            if (!earned) {
                                LinearProgressIndicator(progress = { progress.toFloat() / badge.threshold }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                                Text("$progress / ${badge.threshold}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
