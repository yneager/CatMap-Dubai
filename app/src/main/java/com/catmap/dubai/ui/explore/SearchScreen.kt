package com.catmap.dubai.ui.explore

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
import com.catmap.dubai.ui.components.AppTopBar
import androidx.compose.foundation.layout.imePadding
import com.catmap.dubai.ui.components.currentLocale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.catmap.dubai.R
import com.catmap.dubai.data.Dubai
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.CoatColor
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.PostStatus
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.CatThumb
import com.catmap.dubai.ui.components.CategoryPill
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.PostPhoto
import com.catmap.dubai.ui.components.StatusBadge
import com.catmap.dubai.ui.components.postTitle
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.map.MapFocus
import com.catmap.dubai.ui.navigation.Routes

/** Search posts and community cats by name, animal, category, colour, breed or area. */
@Composable
fun SearchScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val resources = LocalResources.current
    val locale = currentLocale()
    val posts by remember { container.communityFeed }.collectAsStateWithLifecycle(emptyList())
    val cats by remember { container.cats.allCats() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var includeClosed by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim().lowercase(locale)
    // Words for animal kinds and categories match in either language, e.g. "dog" or "كلب".
    val matchedTypes = remember(q, resources) {
        if (q.isEmpty()) emptySet() else AnimalType.entries.filter { resources.getString(it.label).lowercase(locale).contains(q) || it.key.contains(q) }.map { it.key }.toSet()
    }
    val matchedCategories = remember(q, resources) {
        if (q.isEmpty()) emptySet() else PostCategory.entries.filter { resources.getString(it.label).lowercase(locale).contains(q) || it.key.contains(q) }.map { it.key }.toSet()
    }
    val results = remember(q, posts, type, category, includeClosed, matchedTypes, matchedCategories) {
        posts.filter { p ->
            (type == null || p.animalType == type) && (category == null || p.category == category) &&
                (includeClosed || p.status == PostStatus.OPEN.key) &&
                (q.isEmpty() || p.animalType in matchedTypes || p.category in matchedCategories ||
                    p.keywords.any { it.startsWith(q) } || p.name.lowercase().contains(q) ||
                    Dubai.byKey(p.neighborhood).let { h -> h.nameEn.lowercase().contains(q) || h.nameAr.contains(q) })
        }.take(100)
    }
    val catResults = remember(q, cats, type, category) {
        if (q.isEmpty() || (type != null && type != AnimalType.CAT.key) || (category != null && category != PostCategory.STREET.key)) emptyList()
        else cats.filter { it.name.lowercase().contains(q) || it.aliases.any { a -> a.lowercase().contains(q) } || Dubai.nameOf(it.neighborhood).lowercase().contains(q) }.take(20)
    }
    val areas = remember(q) { if (q.length < 2) emptyList() else Dubai.search(q).take(3) }

    Scaffold(
        topBar = {
            AppTopBar("",onBack={nav.popBackStack()},titleContent={
                TextField(
                    query, { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AnimalType.entries,key={it.key},contentType={"animal-chip"}) { t ->
                        FilterChip(type == t.key, { type = if (type == t.key) null else t.key }, label = { IconLabel(t.icon, stringResource(t.label)) })
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PostCategory.entries, key = { it.key }, contentType = { "category-chip" }) { c ->
                        FilterChip(category == c.key, { category = if (category == c.key) null else c.key }, label = { Text(stringResource(c.label)) })
                    }
                    item { FilterChip(includeClosed, { includeClosed = !includeClosed }, label = { Text(stringResource(R.string.include_closed)) }) }
                }
            }
            items(areas, key = { "a" + it.key }, contentType = { "SearchScreen" }) { hood ->
                ResultRow(onClick = {
                    MapFocus.request.value = MapFocus.Request(hood.lat, hood.lng, 14.5)
                    nav.navigate(Routes.MAP) { popUpTo(Routes.MAP); launchSingleTop = true }
                }) {
                    CatMapIcon(CatMapIcons.PIN, Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hood.displayName(), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.show_on_map), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(results, key = { "p" + it.id }, contentType = { "SearchScreen" }) { p ->
                ResultRow(onClick = { nav.navigate(Routes.post(p.id)) }, modifier = Modifier.animateItem()) {
                    PostPhoto(p, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(postTitle(p), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Dubai.nameOf(p.neighborhood) + " · " + relativeTime(p.createdAt),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatusBadge(p)
                    }
                    CategoryPill(p.category, solid = false)
                }
            }
            items(catResults, key = { "c" + it.id }, contentType = { "SearchScreen" }) { c ->
                ResultRow(onClick = { nav.navigate(Routes.cat(c.id)) }) {
                    CatThumb(c.coverPhotoId, labeledFrom(c.coatColor, CoatColor.UNKNOWN), Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), c.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        Text(Dubai.nameOf(c.neighborhood), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CategoryPill(PostCategory.STREET.key, solid = false)
                }
            }
            if (results.isEmpty() && catResults.isEmpty() && areas.isEmpty()) {
                item { EmptyState(Icons.Outlined.SearchOff, stringResource(if (q.isEmpty()) R.string.search_empty_hint else R.string.no_results)) }
            }
        }
    }
}

@Composable
private fun ResultRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
