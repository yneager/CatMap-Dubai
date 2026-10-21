package com.catmap.dubai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.Page
import com.catmap.dubai.data.Pager
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** A [Pager] that starts loading at once and starts over whenever one of [keys] changes. */
@Composable
inline fun <reified T : Any> rememberPager(
    vararg keys: Any?,
    pageSize: Long = 25,
    noinline key: (T) -> String,
    noinline fallback: (() -> Query?)? = null,
    noinline query: () -> Query?,
): Pager<T> {
    val scope = rememberCoroutineScope()
    return remember(*keys) { Pager.of(scope, pageSize, key, fallback, query).also { it.loadMore() } }
}

/** How many items a list may load by itself to fill the screen when filters hide most of them. */
private const val AUTO_FILL_LIMIT = 150

/**
 * Loads the next page when the list scrolls near its end, and keeps loading while filters checked on the phone leave
 * fewer than [minShown] items on screen (up to [AUTO_FILL_LIMIT] loaded; after that [PagingFooter] offers more).
 */
@Composable
fun PagingEffects(pager: Pager<*>, page: Page<*>, shown: Int, list: LazyListState? = null, grid: LazyGridState? = null, minShown: Int = 12) {
    LaunchedEffect(pager, list, grid) {
        snapshotFlow {
            val info = list?.layoutInfo
            val gridInfo = grid?.layoutInfo
            when {
                info != null -> info.totalItemsCount - (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                gridInfo != null -> gridInfo.totalItemsCount - (gridInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)
                else -> Int.MAX_VALUE
            }
        }.distinctUntilChanged().filter { it in 0..6 }.collect { pager.loadMore() }
    }
    LaunchedEffect(pager, shown, page.items.size, page.loading, page.end) {
        if (page.loaded && !page.loading && !page.end && shown < minShown && page.items.size < AUTO_FILL_LIMIT) pager.loadMore()
    }
}

/** The end of a paged list: a spinner while a page loads, "Load more" when there may be more. */
@Composable
fun PagingFooter(page: Page<*>, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            page.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            page.loaded && !page.end -> TextButton(onClick = onMore) { Text(stringResource(R.string.load_more)) }
        }
    }
}
