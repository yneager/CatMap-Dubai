package com.catmap.dubai.ui.explore

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.ui.components.PagingEffects
import com.catmap.dubai.ui.components.PagingFooter
import com.catmap.dubai.ui.components.rememberPager
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.FeedFilters
import com.catmap.dubai.data.MapLayer
import com.catmap.dubai.data.distanceKm
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.PostCard
import com.catmap.dubai.ui.components.SkeletonCard
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.posts.PopIn
import com.catmap.dubai.ui.theme.BrandGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private data class Shortcut(val icon: Int, val label: Int, val route: String, val color: Long)

private val shortcuts = listOf(
    Shortcut(CatMapIcons.HELP, R.string.help_directory, Routes.HELP_DIRECTORY, 0xFF00696B),
    Shortcut(CatMapIcons.ADOPTION, R.string.adoption, Routes.ADOPTION, 0xFF8E24AA),
    Shortcut(CatMapIcons.SITTER, R.string.pet_sitters, Routes.SITTERS, 0xFF5E35B1),
    Shortcut(CatMapIcons.CAT, R.string.community_cats, Routes.CATS, 0xFFA9531A),
    Shortcut(CatMapIcons.RESCUE, R.string.tab_rescue, Routes.RESCUE, 0xFFC62828),
    Shortcut(CatMapIcons.STATION, R.string.tab_stations, Routes.STATIONS, 0xFF00897B),
    Shortcut(CatMapIcons.BELL, R.string.area_alerts, Routes.AREA_ALERTS, 0xFFF9A825),
)

private enum class Sort(val label: Int) { NEWEST(R.string.sort_newest), NEAREST(R.string.sort_nearest) }

/** "Nearest" loads the map cells within about 5 km of you (half the box's side, in degrees). */
private const val NEAR_ME_DEG = 0.045

/** The nearby feed: every report as a scrolling list, with the same filters as the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner()
    val uid = container.session.uid
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val filters = settings.filters
    val favorites by remember(uid) { if (uid == null) flowOf(emptySet()) else container.favorites.ids(uid) }.collectAsStateWithLifecycle(emptySet())
    val fix by container.location.last.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(Sort.NEWEST) }
    var showFilters by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun setFilters(f: FeedFilters) = scope.launch { container.settings.setFilters(f) }

    // Newest: pages of posts, the category chips asked of Firestore, the other filters checked here.
    val categories = remember(filters.layers) { filters.layers.mapNotNull { it.category?.key }.sorted() }
    val pager = rememberPager<Post>(categories, key = { it.id }, fallback = { container.posts.feedQuery() }) { container.posts.feedQuery(categories) }
    val page by pager.state.collectAsStateWithLifecycle()
    // Nearest: the map's own data around you (cells near you, then only changes), sorted by distance.
    val nearMe = sort == Sort.NEAREST && fix != null && uid != null
    val area by remember(nearMe, uid) { if (nearMe) container.map.area(uid!!) else flowOf(null) }.collectAsStateWithLifecycle(null)
    LaunchedEffect(nearMe, fix?.lat, fix?.lng) {
        val f = fix
        if (nearMe && f != null) container.map.show(f.lat - NEAR_ME_DEG, f.lng - NEAR_ME_DEG, f.lat + NEAR_ME_DEG, f.lng + NEAR_ME_DEG)
    }
    val posts = if (nearMe) area?.visiblePosts else page.items.takeIf { page.loaded }

    val shown = remember(posts, filters, fix, sort) {
        posts?.filter { filters.copy(layers = filters.layers - MapLayer.SITTING).matches(it, fix) }?.let { list ->
            val f = fix
            if (sort == Sort.NEAREST && f != null) list.sortedBy { distanceKm(f.lat, f.lng, it.lat, it.lng) } else list
        }
    }

    Box(Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                container.location.current()
                pager.refresh()
                delay(400)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Header(nav) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shortcuts.size, key = { shortcuts[it].route }, contentType = { "shortcut" }) { i ->
                        val s = shortcuts[i]
                        PopIn(i) { ShortcutCard(s) { nav.navigate(s.route) } }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nearby_reports), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        Sort.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = sort == s,
                                onClick = {
                                    sort = s
                                    if (s == Sort.NEAREST && fix == null) scope.launch { container.location.current() }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, Sort.entries.size),
                                label = { Text(stringResource(s.label), maxLines = 1) },
                            )
                        }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(filters.extraCount > 0, { showFilters = true },
                            label = { Text(if (filters.extraCount > 0) stringResource(R.string.filters_n, filters.extraCount) else stringResource(R.string.filters)) },
                            leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)) })
                    }
                    item { FilterChip(filters.layers.isEmpty() && filters.animalTypes.isEmpty(), { setFilters(filters.copy(layers = emptySet(), animalTypes = emptySet())) }, label = { Text(stringResource(R.string.all_animals)) }) }
                    items(MapLayer.entries.filter { it != MapLayer.SITTING },key={it.name},contentType={"layer-chip"}) { layer ->
                        FilterChip(layer in filters.layers, { setFilters(filters.copy(layers = filters.layers.toggle(layer))) },
                            label = { IconLabel(layer.icon, stringResource(layer.label)) })
                    }
                    item {
                        AnimatedVisibility(!filters.isEmpty, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            TextButton(onClick = { setFilters(FeedFilters()) }) { Text(stringResource(R.string.clear_filters)) }
                        }
                    }
                }
            }
            val list = shown
            when {
                list == null -> items(3, contentType = { "loading" }) { SkeletonCard(Modifier.padding(horizontal = 16.dp)) }
                list.isEmpty() && (nearMe || page.end) -> item {
                    EmptyState(Icons.Outlined.TravelExplore, stringResource(if (filters.isEmpty) R.string.no_posts_yet else R.string.no_posts_filters)) {
                        if (!filters.isEmpty) TextButton(onClick = { setFilters(FeedFilters()) }) { Text(stringResource(R.string.clear_filters)) }
                    }
                }
                else -> items(list, key = { it.id }, contentType = { "ExploreScreen" }) { p ->
                    PostCard(
                        p, p.id in favorites,
                        onFavorite = { uid?.let { u -> runner.run { container.favorites.set(u, p.id, p.id !in favorites) } } },
                        onClick = { nav.navigate(Routes.post(p.id)) },
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
            }
            if (!nearMe) item(contentType = "footer") { PagingFooter(page, pager::loadMore) }
        }
    }
    if (!nearMe) PagingEffects(pager, page, shown?.size ?: 0, list = listState)

    // Content scrolls under the transparent status bar; a scrim keeps the clock readable.
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)))
    }

    if (showFilters) FilterSheet(filters, { setFilters(it) }, { showFilters = false }, hasLocation = fix != null)
}

@Composable
private fun Header(nav: NavHostController) {
    val container = LocalContainer.current
    val me by container.session.profile.collectAsStateWithLifecycle()
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(BrandGradient)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.hello_x, me?.displayName?.ifBlank { me?.username } ?: ""),
                        style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f),
                    )
                    Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) { NotificationBell(nav) }
            }
            Surface(
                onClick = { nav.navigate(Routes.SEARCH) },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(s: Shortcut, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(s.color).copy(alpha = 0.12f)),
        modifier = Modifier.width(112.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(s.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                CatMapIcon(s.icon)
            }
            Text(stringResource(s.label), style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2)
        }
    }
}
