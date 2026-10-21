package com.catmap.dubai.ui.adoption

import com.catmap.dubai.ui.icons.CatMapIcon
import com.catmap.dubai.ui.icons.CatMapIcons
import com.catmap.dubai.ui.icons.IconLabel
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.catmap.dubai.ui.components.PagingEffects
import com.catmap.dubai.ui.components.PagingFooter
import com.catmap.dubai.ui.components.rememberPager
import com.catmap.dubai.R
import com.catmap.dubai.data.model.AdoptionRequest
import com.catmap.dubai.data.model.AdoptionStatus
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.data.model.RequestStatus
import com.catmap.dubai.data.model.labeledFrom
import com.catmap.dubai.ui.components.AppTopBar
import com.catmap.dubai.ui.components.EmptyState
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.components.PostGridCard
import com.catmap.dubai.ui.components.RemoteImage
import com.catmap.dubai.ui.components.SkeletonCard
import com.catmap.dubai.ui.components.StatusPill
import com.catmap.dubai.ui.components.TextInputDialog
import com.catmap.dubai.ui.components.relativeTime
import com.catmap.dubai.ui.components.rememberRunner
import com.catmap.dubai.ui.navigation.Routes
import com.catmap.dubai.ui.theme.BrandGradient
import kotlinx.coroutines.flow.flowOf

/** Animals looking for a home, as a grid of photo cards. */
@Composable
fun AdoptionScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val runner = rememberRunner()
    val uid = container.session.uid
    // Adoption listings only, a page at a time (index: hidden, category, createdAt).
    val pager = rememberPager<com.catmap.dubai.data.model.Post>(key = { it.id }, fallback = { container.posts.feedQuery() }) {
        container.posts.feedQuery(listOf(PostCategory.ADOPTION.key))
    }
    val page by pager.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val posts = page.items.takeIf { page.loaded }
    val favorites by remember(uid) { if (uid == null) flowOf(emptySet()) else container.favorites.ids(uid) }.collectAsStateWithLifecycle(emptySet())
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdopted by rememberSaveable { mutableStateOf(false) }
    val listings = posts?.filter {
        it.category == PostCategory.ADOPTION.key && (type == null || it.animalType == type) &&
            (showAdopted || it.adoptionStatus != AdoptionStatus.ADOPTED.key)
    }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.adoption), onBack = { nav.popBackStack() }) {
                TextButton(onClick = { nav.navigate(Routes.ADOPTION_REQUESTS) }) { Text(stringResource(R.string.requests)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.newPost(PostCategory.ADOPTION.key)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.list_for_adoption)) },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp), Modifier.padding(padding).fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BrandGradient).padding(18.dp)) {
                    Column {
                        Text(stringResource(R.string.adoption_hero_title), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.adoption_hero_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(type == null, { type = null }, label = { Text(stringResource(R.string.all)) }) }
                    items(AnimalType.entries,key={it.key},contentType={"animal-chip"}) { t -> FilterChip(type == t.key, { type = t.key }, label = { IconLabel(t.icon, stringResource(t.label)) }) }
                    item { FilterChip(showAdopted, { showAdopted = !showAdopted }, label = { Text(stringResource(R.string.show_adopted)) }) }
                }
            }
            when {
                listings == null -> items(4, contentType = { "loading" }) { SkeletonCard() }
                listings.isEmpty() && page.end -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(com.catmap.dubai.ui.icons.catMapVector(com.catmap.dubai.ui.icons.CatMapIcons.PAW), stringResource(R.string.no_adoption_listings))
                }
                else -> items(listings, key = { it.id }, contentType = { "AdoptionScreens" }) { p ->
                    PostGridCard(
                        p, p.id in favorites,
                        onFavorite = { uid?.let { u -> runner.run { container.favorites.set(u, p.id, p.id !in favorites) } } },
                        onClick = { nav.navigate(Routes.post(p.id)) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "footer") { PagingFooter(page, pager::loadMore) }
        }
    }
    PagingEffects(pager, page, listings?.size ?: 0, grid = gridState)
}

private enum class RequestTab(val label: Int) { RECEIVED(R.string.received), SENT(R.string.sent) }

/** Adoption requests: ones for my listings (approve or decline) and ones I sent. */
@Composable
fun AdoptionRequestsScreen(nav: NavHostController) {
    val container = LocalContainer.current
    val uid = container.session.uid ?: return
    var tab by rememberSaveable { mutableStateOf(RequestTab.RECEIVED) }
    val received by remember(uid) { container.adoption.received(uid) }.collectAsStateWithLifecycle(null)
    val sent by remember(uid) { container.adoption.sent(uid) }.collectAsStateWithLifecycle(null)
    var deciding by remember { mutableStateOf<Pair<AdoptionRequest, Boolean>?>(null) }
    val runner = rememberRunner()
    val list = if (tab == RequestTab.RECEIVED) received else sent

    Scaffold(topBar = { AppTopBar(stringResource(R.string.adoption_requests), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                RequestTab.entries.forEach { t -> Tab(tab == t, { tab = t }, text = { Text(stringResource(t.label)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    list == null -> items(2, contentType = { "loading" }) { SkeletonCard() }
                    list.isEmpty() -> item { EmptyState(Icons.Outlined.Inbox, stringResource(R.string.no_requests)) }
                    else -> items(list, key = { it.id }, contentType = { "AdoptionScreens" }) { r ->
                        RequestCard(
                            r, incoming = tab == RequestTab.RECEIVED,
                            onOpen = { nav.navigate(Routes.post(r.postId)) },
                            onMessage = { nav.navigate(Routes.chatWith(if (tab == RequestTab.RECEIVED) r.requesterId else r.ownerId, r.postId)) },
                            onDecide = { approve -> deciding = r to approve },
                            onWithdraw = { runner.run(R.string.updated) { container.adoption.withdraw(r) } },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
    deciding?.let { (request, approve) ->
        TextInputDialog(
            stringResource(if (approve) R.string.approve_request else R.string.decline_request),
            stringResource(R.string.note_to_requester), stringResource(if (approve) R.string.approve else R.string.reject),
            onConfirm = { note -> runner.run(R.string.updated) { container.adoption.decide(request, approve, note) } },
            onDismiss = { deciding = null },
        )
    }
}

@Composable
private fun RequestCard(
    r: AdoptionRequest, incoming: Boolean, onOpen: () -> Unit, onMessage: () -> Unit,
    onDecide: (Boolean) -> Unit, onWithdraw: () -> Unit, modifier: Modifier = Modifier,
) {
    val status = labeledFrom(r.status, RequestStatus.PENDING)
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.postPhotoId.isNotBlank()) {
                    RemoteImage(r.postPhotoId, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(r.postName.ifBlank { stringResource(R.string.adoption) }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (incoming) "@" + r.requesterName + " · " else "") + relativeTime(r.createdAt),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(stringResource(status.label), Color(status.color))
            }
            if (r.message.isNotBlank()) Text(r.message, style = MaterialTheme.typography.bodyMedium)
            if (r.home.isNotBlank()) Text(stringResource(R.string.about_home_x, r.home), style = MaterialTheme.typography.bodySmall)
            if (r.ownerNote.isNotBlank()) Text(stringResource(R.string.owner_note_x, r.ownerNote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMessage) { Text(stringResource(R.string.message)) }
                if (incoming && status == RequestStatus.PENDING) {
                    FilledTonalButton(onClick = { onDecide(true) }) { Text(stringResource(R.string.approve)) }
                    TextButton(onClick = { onDecide(false) }) { Text(stringResource(R.string.reject)) }
                }
                if (!incoming && status == RequestStatus.PENDING) TextButton(onClick = onWithdraw) { Text(stringResource(R.string.withdraw_request)) }
            }
        }
    }
}
