package com.catmap.dubai.ui.posts

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import com.catmap.dubai.ui.components.AppTopBar
import android.Manifest
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.data.PostRepository
import com.catmap.dubai.ui.components.PagingFooter
import com.catmap.dubai.ui.components.rememberPager
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.loaded
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.AgeGroup
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CatSex
import com.catmap.dubai.data.model.ContactMethod
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.PostUpdate
import com.catmap.dubai.data.model.RequestStatus
import com.catmap.dubai.data.model.UpdateKind
import com.catmap.dubai.data.model.isModeratorRole
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.Avatar
import com.catmap.dubai.ui.components.CategoryPill
import com.catmap.dubai.ui.components.ConfirmDialog
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.FavoriteButton
import com.catmap.dubai.ui.components.LoadingBox
import com.catmap.dubai.ui.components.LocalBitmap
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.PhotoPager
import com.catmap.dubai.ui.components.PostPhoto
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.ReportContentDialog
import com.catmap.dubai.ui.components.RewardPill
import com.catmap.dubai.ui.components.SectionHeader
import com.catmap.dubai.ui.components.StatusBadge
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.dateTime
import com.catmap.dubai.ui.components.distanceText
import com.catmap.dubai.ui.components.openContact
import com.catmap.dubai.ui.components.postHeadline
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberDistanceKm
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.map.AreaCircle
import com.catmap.dubai.ui.map.MapFocus
import com.catmap.dubai.ui.map.MapMemory
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.catmap.dubai.ui.map.MarkerIcons
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.util.ImageUtils
import com.catmap.dubai.util.ProcessedPhoto
import com.catmap.dubai.util.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PostDetailScreen(nav: NavHostController, postId: String) {
    val container = LocalContainer.current
    val loaded by remember(postId) { container.posts.postFlow(postId).loaded() }.collectAsStateWithLifecycle(null)
    val post = loaded?.value
    when {
        loaded == null -> LoadingBox()
        post == null -> Scaffold(topBar = { com.catmap.dubai.ui.components.AppTopBar("", onBack = { nav.popBackStack() }) }) { padding ->
            EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.post_removed), Modifier.padding(padding))
        }
        else -> PostDetail(nav, post)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostDetail(nav: NavHostController, post: Post) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid ?: return
    val isOwner = post.ownerId == uid
    val isMod = isModeratorRole(me?.role)
    val liveUpdates by remember(post.id) { container.posts.updates(post.id) }.collectAsStateWithLifecycle(null)
    // Older updates (beyond the newest 30, which stay live) are read a page at a time when asked for.
    val oldestLive = liveUpdates?.takeIf { it.size >= PostRepository.UPDATES_WINDOW }?.lastOrNull()?.createdAt
    val olderUpdates = rememberPager<PostUpdate>(post.id, oldestLive != null, key = { it.id }) {
        oldestLive?.let { container.posts.olderUpdatesQuery(post.id, it) }
    }
    val olderPage by olderUpdates.state.collectAsStateWithLifecycle()
    val updates = liveUpdates?.let { l -> (l + olderPage.items).distinctBy { it.id } }
    val favorites by remember(uid) { container.favorites.ids(uid) }.collectAsStateWithLifecycle(emptySet())
    val myRequest by remember(post.id, uid) {
        if (post.category == PostCategory.ADOPTION.key && !isOwner) container.adoption.myRequestFor(post.id, uid) else flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val distance = rememberDistanceKm(post.lat, post.lng)
    val category = labeledFrom(post.category, PostCategory.OTHER)
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val saved = post.id in favorites
    val matchHistory = com.catmap.dubai.ui.components.rememberLiveHistory<com.catmap.dubai.data.model.PostMatch>(uid + post.id,
        container.matches.queryForPost(uid, post.id), id = { it.id })
    val availableMatches by produceState<List<com.catmap.dubai.data.model.PostMatch>?>(null, matchHistory.page.items, uid, post.id) {
        value = null
        value = container.matches.filterForPost(uid, post.id, matchHistory.page.items)
    }
    val myMatches = availableMatches.orEmpty()
    val matchPresentation = matchHistory.copy(page = matchHistory.page.copy(loading = matchHistory.page.loading || availableMatches == null))
    val creditedMatch = myMatches.firstOrNull { it.id == post.resolvedMatchId }
    val searchPost = com.catmap.dubai.ai.MatchEngine.isLost(post) || com.catmap.dubai.ai.MatchEngine.isSighting(post)

    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
            // ---- Photos with floating controls ----
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(1.05f)) {
                    if (post.photoIds.isNotEmpty()) {
                        PhotoPager(post.photoIds, Modifier.fillMaxSize()) { nav.navigate(Routes.photo(it)) }
                    } else {
                        PostPhoto(post, Modifier.fillMaxSize())
                    }
                    Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
                    AppTopBar("",onBack={nav.popBackStack()},overlay=true) {
                        RoundIcon(Icons.Default.Share, stringResource(R.string.share)) {
                            scope.launch { Share.sharePost(context, container, post) }
                        }
                        FavoriteButton(saved, { runner.run { container.favorites.set(uid, post.id, !saved) } }, onImage = true)
                        Box {
                            RoundIcon(Icons.Default.MoreVert, stringResource(R.string.more)) { menu = true }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text(stringResource(R.string.make_poster)) }, { menu = false; nav.navigate(Routes.poster(post.id)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) })
                                if (!isOwner) {
                                    DropdownMenuItem({ Text(stringResource(R.string.report_post)) }, { menu = false; dialog = "report" }, leadingIcon = { Icon(Icons.Outlined.Flag, null) })
                                }
                                if (isMod) {
                                    DropdownMenuItem(
                                        { Text(stringResource(if (post.hidden) R.string.unhide_post else R.string.hide_post)) },
                                        { menu = false; dialog = "hide" },
                                        leadingIcon = { Icon(if (post.hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) },
                                    )
                                    if (!isOwner) DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; dialog = "mod-delete" }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                                }
                            }
                        }
                    }
                }
            }

            // ---- Title card, overlapping the photo ----
            item {
                Surface(
                    Modifier.fillMaxWidth().offset(y = (-22).dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.catmap.dubai.ui.components.PostDetailHeader(post)
                        Text(
                            listOfNotNull(
                                Dubai.nameOf(post.neighborhood).takeIf { it.isNotBlank() },
                                distance?.let { distanceText(it) },
                                stringResource(R.string.posted_x, relativeTime(post.createdAt)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val animal = labeledFrom(post.animalType, AnimalType.OTHER)
                            InfoChip(animal.icon, stringResource(animal.label))
                            InfoChip(CatMapIcons.MICROCHIP,stringResource(R.string.microchip)+": "+stringResource(when(post.microchip){"yes"->R.string.yes;"no"->R.string.no;else->R.string.unknown}))
                            labeledFrom(post.sex, CatSex.UNKNOWN).takeIf { it != CatSex.UNKNOWN }?.let { InfoChip(CatMapIcons.SEX, stringResource(it.label)) }
                            labeledFrom(post.ageGroup, AgeGroup.UNKNOWN).takeIf { it != AgeGroup.UNKNOWN }?.let { InfoChip(CatMapIcons.AGE, stringResource(it.label)) }
                            if (post.color.isNotBlank()) InfoChip(CatMapIcons.COLOUR, post.color)
                            if (post.breed.isNotBlank()) InfoChip(CatMapIcons.BREED, post.breed)
                        }
                        com.catmap.dubai.ui.matches.ReunionBanner(post, creditedMatch)
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (post.description.isNotBlank()) Text(post.description, style = MaterialTheme.typography.bodyLarge)
                    if (post.features.isNotBlank()) {
                        DetailBlock(stringResource(R.string.identifying_features), post.features)
                    }
                    if (category.isSearch || category == PostCategory.NEEDS_HELP) {
                        DetailBlock(stringResource(if (category == PostCategory.LOST) R.string.last_seen else R.string.seen_at), dateTime(post.lastSeenAt.takeIf { it > 0 } ?: post.createdAt))
                    }
                    if (post.videoPath.isNotBlank() && container.videos.enabled) {
                        OutlinedButton(onClick = { dialog = "video" }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.PlayCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.play_video))
                        }
                    }
                }
            }

            // ---- Possible matches (lost report <-> sightings), for the two people involved ----
            if (searchPost) item { com.catmap.dubai.ui.matches.PostMatchesSection(nav, post, myMatches, matchPresentation) }
            // Someone who posted a sighting can link it to this lost report themselves.
            if (category == PostCategory.LOST && !isOwner && post.status == PostStatus.OPEN.key) item { LinkSightingButton(nav, post) }

            // ---- Adoption details and requests ----
            if (category == PostCategory.ADOPTION) item {
                AdoptionSection(nav, post, isOwner, myRequest, onRequest = { dialog = "adopt" })
            }

            // ---- Where ----
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(stringResource(R.string.location))
                    MiniMap(
                        post.lat, post.lng, MarkerIcons.post(post.category, post.animalType), LocalIsDark.current, height = 190.dp,
                        zoom = if (post.areaRadiusM > 600) 13.5 else 15.0,
                        area = if (post.areaRadiusM > 0) AreaCircle(post.lat, post.lng, post.areaRadiusM.toDouble(), category.color) else null,
                        onClick = { openOnMap(nav, post) },
                    )
                    if (post.approximate) {
                        Text(stringResource(R.string.post_location_approximate), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { openOnMap(nav, post) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Map, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.show_on_map), maxLines = 1)
                        }
                        OutlinedButton(onClick = { openContact(context, "geo:${post.lat},${post.lng}?q=${post.lat},${post.lng}") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Directions, null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.directions), maxLines = 1)
                        }
                    }
                }
            }

            // ---- Who posted it ----
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(post.ownerPhotoId, post.ownerName)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.posted_by), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("@" + post.ownerName, style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { nav.navigate(Routes.user(post.ownerId)) }) { Text(stringResource(R.string.view_profile)) }
                        }
                        if (!isOwner) {
                            Button(onClick = { nav.navigate(Routes.chatWith(post.ownerId, post.id)) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.AutoMirrored.Outlined.Chat, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.message_owner))
                            }
                            if (post.contactMethod == ContactMethod.PHONE.key && post.contactPhone.isNotBlank()) {
                                OutlinedButton(onClick = { openContact(context, "tel:" + post.contactPhone) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Call, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.call_x, post.contactPhone))
                                }
                            }
                            if (post.contactMethod == ContactMethod.EMAIL.key && post.contactEmail.isNotBlank()) {
                                OutlinedButton(onClick = { openContact(context, "mailto:" + post.contactEmail) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Email, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.email_x, post.contactEmail))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Owner tools ----
            if (isOwner || isMod) item {
                if (isOwner && com.catmap.dubai.data.PostStaleness.searchPost(post)) {
                    OutlinedButton(onClick = { runner.run(R.string.saved) { container.posts.stillLooking(post) } }, enabled = !runner.busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(stringResource(R.string.still_looking))
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(stringResource(if (isOwner) R.string.manage_post else R.string.moderation))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { nav.navigate(Routes.editPost(post.id)) }) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.edit))
                        }
                        if (post.status == PostStatus.OPEN.key && category != PostCategory.ADOPTION) {
                            if (category == PostCategory.LOST || category == PostCategory.FOUND) {
                                FilledTonalButton(onClick = { dialog = "reunited" }) {
                                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.mark_reunited))
                                }
                            }
                            FilledTonalButton(onClick = { runner.run(R.string.updated) { container.posts.setStatus(post, PostStatus.RESOLVED, me ?: return@run) } }) {
                                Text(stringResource(R.string.mark_resolved))
                            }
                        } else if (post.status != PostStatus.OPEN.key) {
                            FilledTonalButton(onClick = { runner.run(R.string.updated) { container.posts.setStatus(post, PostStatus.OPEN, me ?: return@run) } }) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.reopen))
                            }
                        }
                        OutlinedButton(onClick = { nav.navigate(Routes.poster(post.id)) }) {
                            Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.make_poster))
                        }
                        if (isOwner) {
                            OutlinedButton(onClick = { dialog = "delete" }) {
                                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // ---- Sightings and updates ----
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.updates_and_sightings), Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = { dialog = "update" },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = post.status == PostStatus.OPEN.key || isOwner,
                ) {
                    Icon(Icons.Outlined.AddAPhoto, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (category == PostCategory.LOST && !isOwner) R.string.i_saw_this_animal else R.string.add_update))
                }
            }
            val list = updates
            if (list != null && list.isEmpty()) item {
                Text(stringResource(R.string.no_updates_yet), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(list.orEmpty(), key = { it.id }, contentType = { "PostDetailScreen" }) { u ->
                UpdateRow(u, canDelete = u.byUid == uid || isOwner || isMod, modifier = Modifier.animateItem()) {
                    runner.run(R.string.deleted) { container.posts.deleteUpdate(post, u, container.images) }
                }
            }
            if (oldestLive != null) item(contentType = "footer") { PagingFooter(olderPage, olderUpdates::loadMore) }
            if (category.isSearch && list != null) item { com.catmap.dubai.ui.matches.ReportHistory(post, list, myMatches) }
        }
    }

    when (dialog) {
        "report" -> ReportContentDialog("post", post.id, postTitle(post)) { dialog = null }
        "update" -> AddUpdateDialog(post) { dialog = null }
        "adopt" -> AdoptionRequestDialog(post) { dialog = null }
        "video" -> VideoDialog(post.videoPath) { dialog = null }
        "reunited" -> com.catmap.dubai.ui.matches.ReuniteDialog(post, myMatches, onDismiss = { dialog = null }, onDone = { dialog = "celebrate" })
        "celebrate" -> com.catmap.dubai.ui.matches.ReunitedCelebration(post) { dialog = null }
        "delete" -> ConfirmDialog(
            stringResource(R.string.delete_post), stringResource(R.string.delete_post_body), stringResource(R.string.delete),
            onConfirm = {
                runner.run(R.string.deleted, onSuccess = { nav.popBackStack() }) {
                    container.posts.delete(post, container.images)
                }
            },
            onDismiss = { dialog = null },
        )
        "hide" -> TextInputDialog(
            stringResource(if (post.hidden) R.string.unhide_post else R.string.hide_post), stringResource(R.string.note_optional),
            stringResource(R.string.confirm), onConfirm = { note ->
                runner.run(R.string.updated) { container.posts.setHidden(post, !post.hidden, me ?: return@run, note) }
            }, onDismiss = { dialog = null },
        )
        "mod-delete" -> TextInputDialog(
            stringResource(R.string.delete_post), stringResource(R.string.reason), stringResource(R.string.delete),
            onConfirm = { note ->
                runner.run(R.string.deleted, onSuccess = { nav.popBackStack() }) { container.posts.moderatorDelete(post, me ?: return@run, container.images, note) }
            }, onDismiss = { dialog = null },
        )
    }
}

/** "I posted a sighting of this animal": links one of the person's own open sightings to this lost report. */
@Composable
private fun LinkSightingButton(nav: NavHostController, lost: Post) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val uid = container.session.uid.orEmpty()
    val mine by remember(uid) { container.posts.postsBy(uid) }.collectAsStateWithLifecycle(emptyList())
    val sightings = mine.filter { com.catmap.dubai.ai.MatchEngine.isSighting(it) && it.status == PostStatus.OPEN.key && it.animalType == lost.animalType }
    var open by remember { mutableStateOf(false) }
    if (sightings.isEmpty()) return
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.link_my_sighting)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.link_my_sighting)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.link_my_sighting_body), style = MaterialTheme.typography.bodyMedium)
                sightings.forEach { s ->
                    OutlinedButton(onClick = {
                        runner.run(R.string.match_told_owner, onSuccess = { open = false }) {
                            val id = container.matches.linkSighting(lost, s, me ?: return@run)
                            nav.navigate(Routes.match(id))
                        }
                    }, enabled = !runner.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(postTitle(s) + " · " + Dubai.nameOf(s.neighborhood), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun openOnMap(nav: NavHostController, post: Post) {
    MapMemory.lat = post.lat
    MapMemory.lng = post.lng
    MapMemory.zoom = 16.0
    MapFocus.request.value = MapFocus.Request(post.lat, post.lng, 16.0, post.id)
    nav.navigate(Routes.MAP) {
        popUpTo(nav.graph.findStartDestination().id)
        launchSingleTop = true
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White)
        }
    }
}

@Composable
private fun InfoChip(icon: Int, text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CatMapIcon(icon)
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun UpdateRow(u: PostUpdate, canDelete: Boolean, modifier: Modifier = Modifier, onDelete: () -> Unit) {
    val kind = labeledFrom(u.kind, UpdateKind.PROGRESS)
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { CatMapIcon(if (kind == UpdateKind.SIGHTING) CatMapIcons.SPOTTED else if (kind == UpdateKind.FOUND_IT) CatMapIcons.FOUND else CatMapIcons.NOTE) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(kind.label) + " · @" + u.byName, style = MaterialTheme.typography.labelLarge)
                if (u.text.isNotBlank()) Text(u.text, style = MaterialTheme.typography.bodyMedium)
                if (u.photoId.isNotBlank()) RemoteImage(u.photoId, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)))
                Text(
                    listOfNotNull(Dubai.nameOf(u.neighborhood).takeIf { it.isNotBlank() }, relativeTime(u.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.outline) }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

/** "I saw this animal" / progress update, with an optional photo and the current location. */
@Composable
private fun AddUpdateDialog(post: Post, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val runner = rememberRunner()
    val scope = rememberCoroutineScope()
    val me by container.session.profile.collectAsStateWithLifecycle()
    val isOwner = post.ownerId == container.session.uid
    var kind by rememberSaveable { mutableStateOf(if (isOwner) UpdateKind.PROGRESS else UpdateKind.SIGHTING) }
    var text by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<ProcessedPhoto?>(null) }
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch { photo = runCatching { withContext(Dispatchers.Default) { ImageUtils.process(context, uri) } }.getOrNull() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch { container.location.current()?.let { here = it.lat to it.lng } }
    }
    val thanks = stringResource(if (kind == UpdateKind.SIGHTING) R.string.sighting_thanks else R.string.update_added)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_update)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UpdateKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(stringResource(k.label), maxLines = 1) }) }
                }
                OutlinedTextField(text, { text = it.take(1000) }, label = { Text(stringResource(R.string.what_did_you_see)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                val p = photo
                if (p != null) {
                    LocalBitmap(p.analysis, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(14.dp)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (p == null) R.string.add_photo else R.string.change_photo), maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(if (here == null) R.string.add_my_location else R.string.location_added), maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !runner.busy && (text.isNotBlank() || photo != null) && me != null, onClick = {
                runner.run(thanks, onSuccess = onDismiss) {
                    val profile = me ?: return@run
                    val photoId = photo?.let { container.images.upload(profile.uid, it) }.orEmpty()
                    container.posts.addUpdate(post, kind, text, photoId, here?.first ?: 0.0, here?.second ?: 0.0, profile)
                }
            }) { Text(stringResource(R.string.send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AdoptionSection(nav: NavHostController, post: Post, isOwner: Boolean, myRequest: com.catmap.dubai.data.model.AdoptionRequest?, onRequest: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.health_info))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(stringResource(if (post.vaccinated) R.string.vaccinated else R.string.not_vaccinated), if (post.vaccinated) Color(0xFF2E7D32) else Color(0xFF757575))
            StatusPill(stringResource(if (post.neutered) R.string.neutered else R.string.not_neutered), if (post.neutered) Color(0xFF2E7D32) else Color(0xFF757575))
        }
        if (post.health.isNotBlank()) Text(post.health, style = MaterialTheme.typography.bodyMedium)
        if (isOwner) {
            Text(stringResource(R.string.adoption_status), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdoptionStatus.entries.forEach { s ->
                    FilterChip(post.adoptionStatus == s.key, { runner.run { container.posts.setAdoptionStatus(post, s) } }, label = { Text(stringResource(s.label)) })
                }
            }
            OutlinedButton(onClick = { nav.navigate(Routes.ADOPTION_REQUESTS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.view_adoption_requests))
            }
        } else {
            AnimatedVisibility(myRequest != null) {
                myRequest?.let { r ->
                    val s = labeledFrom(r.status, RequestStatus.PENDING)
                    Card(colors = CardDefaults.cardColors(Color(s.color).copy(alpha = 0.12f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.your_request_x, stringResource(s.label)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (r.ownerNote.isNotBlank()) Text(r.ownerNote, style = MaterialTheme.typography.bodySmall)
                            if (s == RequestStatus.PENDING) {
                                TextButton(onClick = { runner.run(R.string.updated) { container.adoption.withdraw(r) } }) { Text(stringResource(R.string.withdraw_request)) }
                            }
                        }
                    }
                }
            }
            if (myRequest == null && post.adoptionStatus != AdoptionStatus.ADOPTED.key) {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.request_to_adopt)) }
            }
        }
    }
}

@Composable
private fun AdoptionRequestDialog(post: Post, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val me by container.session.profile.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var home by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.request_to_adopt_x, postTitle(post))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(message, { message = it.take(1000) }, label = { Text(stringResource(R.string.adopt_message)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(home, { home = it.take(600) }, label = { Text(stringResource(R.string.adopt_home)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = message.isNotBlank() && !runner.busy && me != null, onClick = {
                runner.run(R.string.adopt_request_sent, onSuccess = onDismiss) { container.adoption.request(post, message, home, me ?: return@run) }
            }) { Text(stringResource(R.string.send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Plays a post's short video from Storage with the standard controls. */
@Composable
private fun VideoDialog(path: String, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val url by produceState<Uri?>(null, path) { value = container.videos.url(path) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        text = {
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 12f), contentAlignment = Alignment.Center) {
                val u = url
                if (u == null) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setVideoURI(u)
                                setOnPreparedListener { it.isLooping = true; start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
    )
}
