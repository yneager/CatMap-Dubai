package com.catmap.dubai.ui.map

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Satellite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.AppSettings
import com.catmap.dubai.data.distanceKm
import androidx.compose.ui.layout.onSizeChanged
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.FeedFilters
import com.catmap.dubai.data.MapLayer
import com.catmap.dubai.data.MapMode
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.HealthStatus
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.SitterProfile
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.CategoryPill
import com.catmap.dubai.ui.components.CoatSwatch
import com.catmap.dubai.ui.components.FavoriteButton
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.LocalIsDark
import com.catmap.dubai.ui.components.PostPhoto
import com.catmap.dubai.ui.components.RatingStars
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.RewardPill
import com.catmap.dubai.ui.components.StatusBadge
import com.catmap.dubai.ui.components.distanceText
import com.catmap.dubai.ui.components.postHeadline
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberDistanceKm
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.explore.FilterSheet
import com.catmap.dubai.ui.explore.icon
import com.catmap.dubai.ui.explore.label
import com.catmap.dubai.ui.explore.matches
import com.catmap.dubai.ui.explore.showsCommunityCats
import com.catmap.dubai.ui.explore.showsSitters
import com.catmap.dubai.ui.explore.toggle
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.stations.SwitchRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Selection {
    val id: String

    data class OfPost(val post: Post) : Selection { override val id get() = post.id }
    data class OfCat(val cat: Cat) : Selection { override val id get() = cat.id }
    data class OfSitter(val sitter: SitterProfile) : Selection { override val id get() = sitter.uid }
}

@Composable
fun MapScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val dark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val runner = rememberRunner()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val filters = settings.filters
    val uid = container.session.uid

    val posts by remember { container.communityFeed }.collectAsStateWithLifecycle(emptyList())
    val cats by remember { container.cats.allCats() }.collectAsStateWithLifecycle(emptyList())
    val stations by remember { container.stations.stations() }.collectAsStateWithLifecycle(emptyList())
    val sitters by remember(filters.showsSitters()) {
        if (filters.showsSitters()) container.sitting.activeSitters() else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val sightings by remember(settings.heatmap) {
        if (settings.heatmap) container.cats.recentSightings() else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val favorites by remember(uid) { if (uid == null) flowOf(emptySet()) else container.favorites.ids(uid) }.collectAsStateWithLifecycle(emptySet())
    val fix by container.location.last.collectAsStateWithLifecycle()

    var selection by remember { mutableStateOf<Selection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    var headerHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val controller = remember { MapController() }
    var locationOn by remember { mutableStateOf(container.location.hasPermission()) }

    fun setFilters(f: FeedFilters) = scope.launch { container.settings.setFilters(f) }

    // Another screen asked to show something here: wait for the map, fly there and open its card.
    val focus by MapFocus.request.collectAsStateWithLifecycle()
    LaunchedEffect(focus, posts) {
        val f = focus ?: return@LaunchedEffect
        listMode = false
        while (controller.map == null) delay(50)
        controller.flyTo(f.lat, f.lng, f.zoom)
        val id = f.postId
        if (id != null) {
            val match = posts.firstOrNull { it.id == id }
            if (match == null && posts.isEmpty()) return@LaunchedEffect // the feed hasn't arrived yet
            match?.let { selection = Selection.OfPost(it) }
        }
        MapFocus.request.value = null
    }

    val shownPosts = remember(posts, filters, fix) { posts.filter { filters.matches(it, fix) && (it.lat != 0.0 || it.lng != 0.0) } }
    val shownCats = remember(cats, filters) {
        if (filters.showsCommunityCats() && filters.animalTypes.let { it.isEmpty() || "cat" in it } && !filters.rewardOnly) {
            cats.filter { it.lat != 0.0 || it.lng != 0.0 }
        } else emptyList()
    }
    val shownSitters = remember(sitters, uid) { sitters.filter { it.lat != 0.0 || it.lng != 0.0 } }
    val features = remember(shownPosts, shownCats, shownSitters, stations, settings.showStations, filters) {
        buildList {
            shownPosts.forEach { p ->
                val category = labeledFrom(p.category, PostCategory.OTHER)
                add(
                    MapFeature(
                        p.id, PointKind.POST, p.lat, p.lng, MarkerIcons.post(p.category, p.animalType),
                        pulse = category == PostCategory.LOST || category == PostCategory.NEEDS_HELP,
                    ),
                )
            }
            shownCats.forEach { c ->
                val health = labeledFrom(c.healthStatus, HealthStatus.UNKNOWN)
                val alert = health == HealthStatus.INJURED || health == HealthStatus.NEEDS_ATTENTION
                add(MapFeature(c.id, PointKind.CAT, c.lat, c.lng, MarkerIcons.cat(labeledFrom(c.coatColor, CoatColor.UNKNOWN), alert), pulse = alert))
            }
            shownSitters.forEach { s -> add(MapFeature(s.uid, PointKind.SITTER, s.lat, s.lng, MarkerIcons.SITTER)) }
            if (settings.showStations && filters.layers.isEmpty()) {
                stations.filter { it.active }.forEach { s -> add(MapFeature(s.id, PointKind.STATION, s.lat, s.lng, MarkerIcons.STATION)) }
            }
        }
    }
    val heat = remember(sightings, shownPosts, settings.heatmap) {
        if (!settings.heatmap) emptyList() else sightings.map { it.lat to it.lng } + shownPosts.map { it.lat to it.lng }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locationOn = true
            scope.launch { container.location.current()?.let { controller.flyTo(it.lat, it.lng, 15.5) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (listMode) {
            val referenceLat=fix?.lat ?: MapMemory.lat
            val referenceLng=fix?.lng ?: MapMemory.lng
            val ordered = remember(features,referenceLat,referenceLng) { features.sortedWith(compareBy<MapFeature> { point -> distanceKm(referenceLat,referenceLng,point.lat,point.lng) }.thenBy { it.kind.name }.thenBy { it.id }) }
            val top=with(androidx.compose.ui.platform.LocalDensity.current) {headerHeight.toDp()}+8.dp
            LazyColumn(Modifier.fillMaxSize().padding(top=top), contentPadding = PaddingValues(start=16.dp,end=16.dp,bottom=32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(ordered.isEmpty()) item { com.catmap.dubai.ui.components.EmptyState(com.catmap.dubai.ui.icons.catMapVector(CatMapIcons.PAW),stringResource(R.string.no_posts_filters)) }
                items(ordered,key={ "${it.kind}:${it.id}" },contentType={it.kind}) { point ->
                    val selectedPost=shownPosts.firstOrNull {it.id==point.id}
                    val title = when(point.kind) {
                        PointKind.POST -> selectedPost?.let {com.catmap.dubai.ui.components.postTitle(it)}.orEmpty()
                        PointKind.CAT -> shownCats.firstOrNull { it.id==point.id }?.name.orEmpty()
                        PointKind.SITTER -> shownSitters.firstOrNull { it.uid==point.id }?.name.orEmpty()
                        PointKind.STATION -> stations.firstOrNull { it.id==point.id }?.name.orEmpty()
                        PointKind.RESCUE -> stringResource(R.string.tab_rescue)
                    }
                    androidx.compose.material3.OutlinedButton(onClick={ nav.navigate(when(point.kind) {
                        PointKind.POST -> Routes.post(point.id); PointKind.CAT -> Routes.cat(point.id)
                        PointKind.SITTER -> Routes.sitter(point.id); PointKind.STATION -> Routes.station(point.id); PointKind.RESCUE -> Routes.rescue(point.id)
                    }) },modifier=Modifier.fillMaxWidth()) {
                        CatMapIcon(when(point.kind) { PointKind.POST -> CatMapIcons.animal(labeledFrom(selectedPost?.animalType,com.catmap.dubai.data.model.AnimalType.OTHER)); PointKind.CAT -> CatMapIcons.CAT; PointKind.SITTER -> CatMapIcons.SITTER; PointKind.STATION -> CatMapIcons.STATION; PointKind.RESCUE -> CatMapIcons.RESCUE })
                        Text(title,Modifier.weight(1f).padding(start=8.dp))
                        Text(com.catmap.dubai.ui.components.distanceText(distanceKm(referenceLat,referenceLng,point.lat,point.lng)))
                    }
                }
            }
        } else {
        CommunityMap(
            Modifier.fillMaxSize(),
            features = features,
            mode = settings.mapMode,
            dark = dark,
            satellite = settings.satellite,
            controller = controller,
            selectedId = selection?.id,
            heat = heat,
            showHeat = settings.heatmap,
            showUserLocation = locationOn,
            onFeatureClick = { f ->
                when (f.kind) {
                    PointKind.POST -> posts.firstOrNull { it.id == f.id }?.let { selection = Selection.OfPost(it) }
                    PointKind.CAT -> cats.firstOrNull { it.id == f.id }?.let { selection = Selection.OfCat(it) }
                    PointKind.SITTER -> sitters.firstOrNull { it.uid == f.id }?.let { selection = Selection.OfSitter(it) }
                    PointKind.STATION -> nav.navigate(Routes.station(f.id))
                    PointKind.RESCUE -> nav.navigate(Routes.rescue(f.id))
                }
            },
            onMapClick = { selection = null; searching = false },
        )
        }

        // ---- Search, chips ----
        Column(Modifier.fillMaxWidth().onSizeChanged {headerHeight=it.height}.statusBarsPadding().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                Modifier.padding(horizontal = 12.dp),
                shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp, tonalElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = query,
                        onValueChange = { query = it; searching = true },
                        placeholder = { Text(stringResource(R.string.search_map), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    NotificationBell(nav)
                }
            }
            AnimatedVisibility(searching && query.isNotBlank(), enter = fadeIn() + slideInVertically { -it / 4 }, exit = fadeOut()) {
                SearchResults(query, posts, onArea = { lat, lng ->
                    searching = false
                    controller.flyTo(lat, lng, 14.5)
                }, onPost = { p ->
                    searching = false
                    selection = Selection.OfPost(p)
                    controller.flyTo(p.lat, p.lng, 16.0)
                })
            }
            LayerChips(filters, ::setFilters, onMore = { showFilters = true })
            // A solid pill like the search bar and chips, so the toggle stays readable over any map tile.
            Surface(
                onClick = { listMode = !listMode },
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 40.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (listMode) Icons.Outlined.Map else Icons.AutoMirrored.Outlined.ViewList, null,
                        Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (listMode) R.string.show_map_view else R.string.show_list_view), style = MaterialTheme.typography.labelLarge)
                }
            }
            if(listMode && fix==null) Text(stringResource(R.string.distance_from_map_center),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.labelSmall)
        }

        // ---- Right-hand controls ----
        if (!listMode) {
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapModeToggle(settings.mapMode) { mode -> scope.launch { container.settings.setMapMode(mode) } }
            if (MapStyles.satelliteAvailable) {
                RoundControl(Icons.Outlined.Satellite, stringResource(R.string.satellite), active = settings.satellite) {
                    scope.launch { container.settings.setSatellite(!settings.satellite) }
                }
            }
            RoundControl(Icons.Default.Layers, stringResource(R.string.map_layers)) { showFilters = true }
            RoundControl(Icons.Default.MyLocation, stringResource(R.string.my_location)) {
                val f = fix
                if (container.location.hasPermission() && f != null) {
                    locationOn = true
                    controller.flyTo(f.lat, f.lng, 15.5)
                    scope.launch { container.location.current() }
                } else {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }

        // ---- Bottom: count and preview ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Centred, so the count never covers the map attribution button in the corner.
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shadowElevation = 2.dp) {
                Text(
                    stringResource(R.string.on_map_n, features.size),
                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            AnimatedContent(
                selection,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn()) togetherWith
                        (slideOutVertically { it } + fadeOut())
                },
                contentKey = { it?.id },
                label = "preview",
            ) { sel ->
                when (sel) {
                    is Selection.OfPost -> PostPreview(
                        sel.post, saved = sel.post.id in favorites,
                        onFavorite = { uid?.let { u -> runner.run { container.favorites.set(u, sel.post.id, sel.post.id !in favorites) } } },
                        onOpen = { nav.navigate(Routes.post(sel.post.id)) },
                        onClose = { selection = null },
                    )
                    is Selection.OfCat -> CatPreview(sel.cat, onOpen = { nav.navigate(Routes.cat(sel.cat.id)) }, onClose = { selection = null })
                    is Selection.OfSitter -> SitterPreview(sel.sitter, onOpen = { nav.navigate(Routes.sitter(sel.sitter.uid)) }, onClose = { selection = null })
                    null -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }
        }
    }

    if (showFilters) {
        FilterSheet(
            filters, { setFilters(it) }, { showFilters = false }, hasLocation = fix != null,
            extra = {
                com.catmap.dubai.ui.components.SectionHeader(stringResource(R.string.map_layers))
                SwitchRow(stringResource(R.string.feeding_stations), settings.showStations) { scope.launch { container.settings.setShowStations(it) } }
                SwitchRow(stringResource(R.string.heatmap), settings.heatmap) { scope.launch { container.settings.setHeatmap(it) } }
                if (MapStyles.satelliteAvailable) {
                    SwitchRow(stringResource(R.string.satellite), settings.satellite) { scope.launch { container.settings.setSatellite(it) } }
                }
            },
        )
    }
}

/** "All" plus one chip per layer; several can be on together. A clear chip appears when any filter is set. */
@Composable
private fun LayerChips(filters: FeedFilters, onChange: (FeedFilters) -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapChip(selected = filters.layers.isEmpty() && filters.animalTypes.isEmpty(), text = stringResource(R.string.all_animals), artwork = CatMapIcons.PAW) { onChange(filters.copy(layers = emptySet(), animalTypes = emptySet())) }
        MapLayer.entries.forEach { layer ->
            MapChip(layer in filters.layers, stringResource(layer.label), artwork = CatMapIcons.layer(layer)) { onChange(filters.copy(layers = filters.layers.toggle(layer))) }
        }
        val extra = filters.extraCount
        MapChip(extra > 0, if (extra > 0) stringResource(R.string.filters_n, extra) else stringResource(R.string.filters), icon = true) { onMore() }
        AnimatedVisibility(!filters.isEmpty, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            TextButton(onClick = { onChange(FeedFilters()) }) { Text(stringResource(R.string.clear_filters)) }
        }
    }
}

@Composable
private fun MapChip(selected: Boolean, text: String, icon: Boolean = false, artwork: Int? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        leadingIcon = if (icon) ({ Icon(Icons.Default.Tune, null, Modifier.size(FilterChipDefaults.IconSize)) }) else artwork?.let { id -> { CatMapIcon(id) } },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        elevation = FilterChipDefaults.filterChipElevation(elevation = 3.dp),
    )
}

/** Classic ↔ Modern switch with a sliding highlight; the camera and filters stay where they are. */
@Composable
private fun MapModeToggle(mode: MapMode, onChange: (MapMode) -> Unit) {
    val offset by animateDpAsState(if (mode == MapMode.CLASSIC) 0.dp else 44.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "mode")
    Surface(shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.padding(4.dp)) {
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, offset.roundToPx()) }.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Column {
                ModeIcon(Icons.Outlined.Map, stringResource(R.string.map_classic), mode == MapMode.CLASSIC) { onChange(MapMode.CLASSIC) }
                ModeIcon(Icons.Outlined.AutoAwesome, stringResource(R.string.map_modern), mode == MapMode.MODERN) { onChange(MapMode.MODERN) }
            }
        }
    }
}

@Composable
private fun ModeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 6.dp,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Neighbourhoods and posts matching the search text. */
@Composable
private fun SearchResults(query: String, posts: List<Post>, onArea: (Double, Double) -> Unit, onPost: (Post) -> Unit) {
    val q = query.trim().lowercase()
    val areas = remember(q) { Dubai.search(q).take(4) }
    val matching = remember(q, posts) {
        posts.filter { p -> p.keywords.any { it.startsWith(q) } || p.name.lowercase().contains(q) }.take(5)
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(6.dp)) {
        LazyColumn(Modifier.heightIn(max = 320.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(areas, key = { "a" + it.key }, contentType = { "MapScreen" }) { hood ->
                Row(
                    Modifier.fillMaxWidth().clickable { onArea(hood.lat, hood.lng) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CatMapIcon(CatMapIcons.PIN, Modifier.width(28.dp))
                    Text(hood.displayName(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            items(matching, key = { "p" + it.id }, contentType = { "MapScreen" }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPost(p) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PostPhoto(p, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(postTitle(p), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Dubai.nameOf(p.neighborhood), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CategoryPill(p.category, solid = false)
                }
            }
            if (areas.isEmpty() && matching.isEmpty()) {
                item { Text(stringResource(R.string.no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun PreviewCard(onOpen: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun PostPreview(post: Post, saved: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
    val distance = rememberDistanceKm(post.lat, post.lng)
    PreviewCard(onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PostPhoto(post, Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryPill(post.category)
                    if (post.reward > 0) RewardPill(post.reward)
                }
                Text(postTitle(post), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (post.name.isNotBlank()) Text(postHeadline(post), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    listOfNotNull(
                        Dubai.nameOf(post.neighborhood),
                        relativeTime(post.lastSeenAt.takeIf { it > 0 } ?: post.createdAt),
                        distance?.let { distanceText(it) },
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(post)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                FavoriteButton(saved, onFavorite)
            }
        }
        FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.view_details))
        }
    }
}

@Composable
private fun CatPreview(cat: Cat, onOpen: () -> Unit, onClose: () -> Unit) {
    val coat = labeledFrom(cat.coatColor, CoatColor.UNKNOWN)
    val distance = rememberDistanceKm(cat.lat, cat.lng)
    PreviewCard(onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CatThumb(cat.coverPhotoId, coat, Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)), cat.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CategoryPill(PostCategory.STREET.key)
                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoatSwatch(coat, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(coat.label), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    listOfNotNull(Dubai.nameOf(cat.neighborhood), stringResource(R.string.last_seen_x, relativeTime(cat.lastSeen)), distance?.let { distanceText(it) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun SitterPreview(sitter: SitterProfile, onOpen: () -> Unit, onClose: () -> Unit) {
    val distance = rememberDistanceKm(sitter.lat, sitter.lng)
    PreviewCard(onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sitter.photoId.isNotBlank()) {
                RemoteImage(sitter.photoId, Modifier.size(72.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                    CatMapIcon(CatMapIcons.SITTER)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(sitter.name, style = MaterialTheme.typography.titleMedium)
                if (sitter.headline.isNotBlank()) Text(sitter.headline, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(sitter.rating)
                    Spacer(Modifier.width(6.dp))
                    Text("(${sitter.ratingCount})", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    listOfNotNull(Dubai.nameOf(sitter.neighborhood), distance?.let { distanceText(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
        }
    }
}
