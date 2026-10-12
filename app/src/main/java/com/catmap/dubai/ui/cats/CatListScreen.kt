package com.catmap.dubai.ui.cats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.HealthStatus
import com.catmap.dubai.data.model.TnrStatus
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.ClickableCard
import com.catmap.dubai.ui.components.CoatSwatch
import com.catmap.dubai.ui.components.DropdownField
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.navigation.NotificationBell
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.statusColor

private enum class Sort(val label: Int) { RECENT(R.string.sort_recent), SIGHTINGS(R.string.sort_sightings), NAME(R.string.sort_name) }

@Composable
fun CatListScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val cats by remember { container.cats.allCats() }.collectAsStateWithLifecycle(null)
    val profile by container.session.profile.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var followingOnly by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(Sort.RECENT) }
    var filters by remember { mutableStateOf(CatFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    val anywhere = stringResource(R.string.all_neighborhoods)

    val shown = remember(cats, query, followingOnly, sort, filters, profile?.followedCats) {
        val q = query.trim().lowercase()
        (cats ?: emptyList())
            .filter { filters.matches(it) }
            .filter { !followingOnly || it.id in (profile?.followedCats ?: emptyList()) }
            .filter { cat ->
                q.isEmpty() || cat.name.lowercase().contains(q) || cat.aliases.any { it.lowercase().contains(q) } ||
                    Dubai.nameOf(cat.neighborhood).lowercase().contains(q)
            }
            .let { list ->
                when (sort) {
                    Sort.RECENT -> list.sortedByDescending { it.lastSeen }
                    Sort.SIGHTINGS -> list.sortedByDescending { it.sightingCount }
                    Sort.NAME -> list.sortedBy { it.name.lowercase() }
                }
            }
    }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.community_cats), onBack = { nav.popBackStack() }, actions = { NotificationBell(nav) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.newSighting()) }) {
                Icon(Icons.Default.AddAPhoto, stringResource(R.string.report_sighting))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            // Extra bottom space so the add-sighting button never covers the last cat.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_cats)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                DropdownField(
                    label = stringResource(R.string.neighborhood),
                    options = listOf<String?>(null) + Dubai.neighborhoods.sortedBy { it.displayName() }.map { it.key },
                    selected = filters.neighborhood,
                    onSelect = { filters = filters.copy(neighborhood = it) },
                ) { key -> if (key == null) anywhere else Dubai.nameOf(key) }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        filters.activeCount > 0, { showFilters = true },
                        label = { Text(if (filters.activeCount > 0) stringResource(R.string.filters_n, filters.activeCount) else stringResource(R.string.filters)) },
                        leadingIcon = { Icon(Icons.Default.FilterList, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(followingOnly, { followingOnly = !followingOnly }, label = { Text(stringResource(R.string.following)) },
                        leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) })
                    Sort.entries.forEach { s ->
                        FilterChip(sort == s, { sort = s }, label = { Text(stringResource(s.label)) })
                    }
                }
            }
            if (cats != null && shown.isEmpty()) {
                item { EmptyState(Icons.Default.Pets, stringResource(R.string.no_cats_found)) }
            }
            items(shown, key = { it.id }) { cat ->
                CatCard(cat, following = cat.id in (profile?.followedCats ?: emptyList())) { nav.navigate(Routes.cat(cat.id)) }
            }
        }
    }
    if (showFilters) FilterSheet(filters, { filters = it }, { showFilters = false })
}

@Composable
fun CatCard(cat: Cat, following: Boolean = false, onClick: () -> Unit) {
    val coat = labeledFrom(cat.coatColor, CoatColor.UNKNOWN)
    val health = labeledFrom(cat.healthStatus, HealthStatus.UNKNOWN)
    val tnr = labeledFrom(cat.tnrStatus, TnrStatus.UNKNOWN)
    ClickableCard(onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CatThumb(cat.coverPhotoId, coat, Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)), cat.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (following) Icon(Icons.Default.Star, stringResource(R.string.following), Modifier.padding(start = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                if (cat.aliases.isNotEmpty()) {
                    Text(stringResource(R.string.aka, cat.aliases.joinToString()), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoatSwatch(coat, 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(coat.label) + " · " + Dubai.nameOf(cat.neighborhood),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    stringResource(R.string.seen_count_last, cat.sightingCount, relativeTime(cat.lastSeen)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (health != HealthStatus.UNKNOWN) StatusPill(stringResource(health.label), statusColor(health.color))
                    if (tnr != TnrStatus.UNKNOWN) StatusPill(stringResource(tnr.label), MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
