package com.catmap.dubai.ui.posts

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.PostCard
import com.catmap.dubai.ui.components.PostGridCard
import com.catmap.dubai.ui.components.SkeletonCard
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.map.AreaCircle
import com.catmap.dubai.ui.map.MapMemory
import com.catmap.dubai.ui.map.MiniMap
import com.catmap.dubai.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ChooserItem(val icon: Int, val title: Int, val subtitle: Int, val color: Long, val route: String)

/** The sheet behind the "+" button: every way to post, in one tap. Tiles pop in one after another. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostChooserSheet(nav: NavHostController, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val main = PostCategory.entries.map { c -> ChooserItem(categoryIcon(c), c.label, categoryHint(c), c.color, Routes.newPost(c.key)) }
    val community = listOf(
        ChooserItem(CatMapIcons.CAMERA, R.string.chooser_sighting, R.string.chooser_sighting_hint, 0xFFA9531A, Routes.newSighting()),
        ChooserItem(CatMapIcons.RESCUE, R.string.report_emergency, R.string.chooser_rescue_hint, 0xFFC62828, Routes.newRescue()),
        ChooserItem(CatMapIcons.SITTER, R.string.chooser_sitter, R.string.chooser_sitter_hint, 0xFF5E35B1, Routes.SITTER_EDIT),
    )
    fun open(route: String) {
        scope.launch {
            state.hide()
            onDismiss()
            nav.navigate(route)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.post_what), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.post_what_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            (main + community).chunked(2).forEachIndexed { row, items ->
                Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEachIndexed { col, item ->
                        PopIn(row * 2 + col, Modifier.weight(1f)) {
                            ChoiceTile(
                                selected = false, title = stringResource(item.title), subtitle = stringResource(item.subtitle),
                                icon = item.icon, color = Color(item.color),
                            ) { open(item.route) }
                        }
                    }
                    if (items.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Scales and fades its content in after a short delay based on [index], for a staggered entrance. */
@Composable
fun PopIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    androidx.compose.foundation.layout.Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            scaleX = 0.85f + 0.15f * progress.value
            scaleY = 0.85f + 0.15f * progress.value
        },
    ) { content() }
}

private enum class MyPostsTab(val label: Int) { OPEN(R.string.tab_open), CLOSED(R.string.tab_closed) }

@Composable
fun MyPostsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val uid = container.session.uid ?: return
    val posts by remember(uid) { container.posts.postsBy(uid) }.collectAsStateWithLifecycle(null)
    val favorites by remember(uid) { container.favorites.ids(uid) }.collectAsStateWithLifecycle(emptySet())
    var tab by rememberSaveable { mutableStateOf(MyPostsTab.OPEN) }
    val shown = posts?.filter { (it.status == PostStatus.OPEN.key) == (tab == MyPostsTab.OPEN) }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.my_posts), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                MyPostsTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(stringResource(t.label)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    shown == null -> items(2, contentType = { "loading" }) { SkeletonCard() }
                    shown.isEmpty() -> item {
                        EmptyState(Icons.Outlined.PostAdd, stringResource(R.string.no_my_posts)) {
                            Button(onClick = { nav.navigate(Routes.newPost()) }) { Text(stringResource(R.string.post_new)) }
                        }
                    }
                    else -> items(shown, key = { it.id }, contentType = { "PostLists" }) { p ->
                        PostCard(
                            p, p.id in favorites,
                            onFavorite = { runner.run { container.favorites.set(uid, p.id, p.id !in favorites) } },
                            onClick = { nav.navigate(Routes.post(p.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val uid = container.session.uid ?: return
    val saved by remember(uid) { container.favorites.favorites(uid) }.collectAsStateWithLifecycle(null)
    val posts by remember { container.communityFeed }.collectAsStateWithLifecycle(null)
    val ids = saved?.map { it.postId }.orEmpty()
    // Saved posts are matched against the live feed; ones that were removed simply drop out.
    val shown = if (saved == null || posts == null) null else ids.mapNotNull { id -> posts!!.firstOrNull { it.id == id } }

    Scaffold(topBar = { AppTopBar(stringResource(R.string.favorites), onBack = { nav.popBackStack() }) }) { padding ->
        when {
            shown == null -> Column(Modifier.padding(padding).padding(16.dp)) { SkeletonCard() }
            shown.isEmpty() -> EmptyState(Icons.Outlined.FavoriteBorder, stringResource(R.string.no_favorites), Modifier.padding(padding))
            else -> LazyVerticalGrid(
                GridCells.Adaptive(160.dp), Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.id }, contentType = { "PostLists" }) { p ->
                    PostGridCard(
                        p, saved = true,
                        onFavorite = { runner.run { container.favorites.set(uid, p.id, false) } },
                        onClick = { nav.navigate(Routes.post(p.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** Follow an area to hear about lost animals reported nearby. */
@Composable
fun AreaAlertsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val scope = rememberCoroutineScope()
    val uid = container.session.uid ?: return
    val follow by remember(uid) { container.posts.areaFollow(uid) }.collectAsStateWithLifecycle(null)
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var radiusKm by remember { mutableFloatStateOf(3f) }
    LaunchedEffect(follow) {
        follow?.let {
            if (lat == null) {
                lat = it.lat
                lng = it.lng
                radiusKm = it.radiusKm.toFloat().coerceIn(1f, 15f)
            }
        }
    }
    val handle = nav.currentBackStackEntry?.savedStateHandle
    val pickedLat by (handle?.getStateFlow<Double?>(Routes.PICKED_LAT, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    val pickedLng by (handle?.getStateFlow<Double?>(Routes.PICKED_LNG, null) ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsStateWithLifecycle()
    LaunchedEffect(pickedLat, pickedLng) {
        if (pickedLat != null && pickedLng != null) {
            lat = pickedLat
            lng = pickedLng
            handle?.remove<Double>(Routes.PICKED_LAT)
            handle?.remove<Double>(Routes.PICKED_LNG)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) scope.launch { container.location.current()?.let { lat = it.lat; lng = it.lng } }
    }
    val saved = stringResource(R.string.alerts_saved)

    Scaffold(topBar = { AppTopBar(stringResource(R.string.area_alerts), onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.area_alerts_intro), style = MaterialTheme.typography.bodyMedium)
            val la = lat
            val ln = lng
            if (la != null && ln != null && (la != 0.0 || ln != 0.0)) {
                MiniMap(la, ln, com.catmap.dubai.ui.map.MarkerIcons.post("lost", "other"), LocalIsDark.current, height = 220.dp,
                    zoom = when { radiusKm > 8 -> 10.5; radiusKm > 4 -> 11.5; else -> 12.5 },
                    area = AreaCircle(la, ln, radiusKm * 1000.0, 0xFF7C4DFF),
                    onClick = { nav.navigate(Routes.pickLocation(la, ln)) })
                Text(Dubai.nearest(la, ln).displayName(), style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.use_my_location), maxLines = 1) }
                OutlinedButton(onClick = { nav.navigate(Routes.pickLocation(lat ?: MapMemory.lat, lng ?: MapMemory.lng)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.choose_on_map), maxLines = 1)
                }
            }
            Text(stringResource(R.string.alert_radius_km, radiusKm.toInt()), style = MaterialTheme.typography.labelLarge)
            Slider(radiusKm, { radiusKm = it.toInt().toFloat() }, valueRange = 1f..15f, steps = 13)
            val count = if (la != null && ln != null) Dubai.neighborhoods.count { com.catmap.dubai.data.distanceKm(la, ln, it.lat, it.lng) <= radiusKm }.coerceAtLeast(1) else 0
            if (count > 0) Text(stringResource(R.string.alert_areas_n, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { runner.run(saved) { container.posts.followArea(uid, la ?: return@run, ln ?: return@run, radiusKm.toDouble()) } },
                enabled = la != null && ln != null && !runner.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (follow == null) R.string.start_alerts else R.string.update_alerts)) }
            if (follow != null) {
                OutlinedButton(onClick = { runner.run(R.string.alerts_stopped) { container.posts.unfollowArea(uid) } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.stop_alerts))
                }
            }
            Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Outlined.NotificationsActive, null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (settings.allows(com.catmap.dubai.data.NotifCategory.LOST_NEARBY)) R.string.alerts_enabled_note else R.string.alerts_disabled_note),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
