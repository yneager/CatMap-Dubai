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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.produceState
import com.catmap.dubai.data.SearchTerms
import com.catmap.dubai.data.safeObjects
import com.catmap.dubai.data.model.Cat
import com.catmap.dubai.data.model.Post
import com.catmap.dubai.ui.components.PagingEffects
import com.catmap.dubai.ui.components.PagingFooter
import com.catmap.dubai.ui.components.rememberPager
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
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
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var includeClosed by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim().lowercase(locale)
    // Firestore is asked only once typing pauses.
    var settled by remember { mutableStateOf(q) }
    LaunchedEffect(q) { delay(350); settled = q }
    // Words for animal kinds and categories ("dog", "كلب", "lost") become filters; the other words are searched.
    val parsed = remember(settled, resources) {
        val types = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        val words = SearchTerms.words(settled).filter { w ->
            val t = AnimalType.entries.filter { resources.getString(it.label).lowercase(locale).startsWith(w) || it.key.startsWith(w) }.map { it.key }
            val c = PostCategory.entries.filter { resources.getString(it.label).lowercase(locale).startsWith(w) || it.key.startsWith(w) }.map { it.key }
            types += t
            categories += c
            t.isEmpty() && c.isEmpty()
        }
        Triple(words, types.toSet(), categories.toSet())
    }
    val (words, matchedTypes, matchedCategories) = parsed
    // The longest word is asked of Firestore (all posts, newest first); the others are checked on the phone.
    val word = words.maxByOrNull { it.length }.orEmpty()
    val chipCategories = listOfNotNull(category).ifEmpty { matchedCategories.toList() }.sorted()
    val browsing = word.isEmpty() && (type != null || category != null || matchedTypes.isNotEmpty() || matchedCategories.isNotEmpty())
    val listState = rememberLazyListState()
    val pager = rememberPager<Post>(word, browsing, chipCategories, key = { it.id }, fallback = { if (browsing) container.posts.feedQuery() else null }) {
        when {
            word.isNotEmpty() -> container.posts.searchQuery(word)
            browsing -> container.posts.feedQuery(chipCategories)
            else -> null
        }
    }
    val page by pager.state.collectAsStateWithLifecycle()
    val results = remember(page.items, words, type, category, includeClosed, matchedTypes, matchedCategories) {
        page.items.filter { p ->
            (type == null || p.animalType == type) && (category == null || p.category == category) &&
                (matchedTypes.isEmpty() || p.animalType in matchedTypes) &&
                (matchedCategories.isEmpty() || category != null || p.category in matchedCategories) &&
                (includeClosed || p.status == PostStatus.OPEN.key) &&
                words.all { w -> SearchTerms.matches(w, p.keywords) || p.name.lowercase().contains(w) }
        }
    }
    val catsWanted = word.isNotEmpty() && (type == null || type == AnimalType.CAT.key) && (category == null || category == PostCategory.STREET.key)
    val catResults by produceState(emptyList<Cat>(), word, catsWanted) {
        value = if (!catsWanted) emptyList() else runCatching { container.cats.searchQuery(word).limit(20).get().await().safeObjects<Cat>() }
            .getOrDefault(emptyList())
            .filter { c -> !c.hidden && c.mergedInto.isEmpty() && SearchTerms.words(c.name, *c.aliases.toTypedArray()).let { names -> words.all { SearchTerms.matches(it, names) } } }
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
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
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
            if (word.isNotEmpty() || browsing) item(contentType = "footer") { PagingFooter(page, pager::loadMore) }
            if (results.isEmpty() && catResults.isEmpty() && areas.isEmpty() && (page.end || (word.isEmpty() && !browsing))) {
                item { EmptyState(Icons.Outlined.SearchOff, stringResource(if (q.isEmpty() && !browsing) R.string.search_empty_hint else R.string.no_results)) }
            }
        }
    }
    PagingEffects(pager, page, results.size, list = listState)
}

@Composable
private fun ResultRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
