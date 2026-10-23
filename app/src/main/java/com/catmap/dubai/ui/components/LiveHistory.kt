package com.catmap.dubai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catmap.dubai.data.AppErrors
import com.catmap.dubai.data.ForegroundRefresh
import com.catmap.dubai.data.Page
import com.catmap.dubai.data.Pager
import com.catmap.dubai.data.snapshotFlow
import com.catmap.dubai.data.safeObjects
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.retryWhen

data class LiveHistory<T>(val page: Page<T>, val more: () -> Unit, val update: (String, (T) -> T) -> Unit)

/** A bounded live head with older snapshot-cursor pages, including equal timestamps. */
@Composable
inline fun <reified T: Any> rememberLiveHistory(key: Any, base: Query, size: Long = 50, noinline id: (T) -> String): LiveHistory<T> {
    val foreground by ForegroundRefresh.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snapshot by remember(key, foreground) { base.limit(size).snapshotFlow().retryWhen { error, _ -> AppErrors.awaitListenerRetry(error) } }
        .collectAsStateWithLifecycle(null)
    val boundary = rememberUpdatedState(snapshot?.documents?.lastOrNull())
    val pager = remember(key, foreground) { Pager.of(scope, size, id) { boundary.value?.let { base.startAfter(it) } } }
    val older by pager.state.collectAsStateWithLifecycle()
    val live = snapshot?.safeObjects<T>()
    val items = live?.let { head -> head + older.items.filter { old -> head.none { id(it) == id(old) } } }.orEmpty()
    val page = older.copy(items = items, loaded = snapshot != null,
        end = older.end || (older.items.isEmpty() && snapshot != null && snapshot!!.size() < size))
    return LiveHistory(page, pager::loadMore, pager::update)
}

@Composable
fun HistoryMore(history: LiveHistory<*>) = PagingFooter(history.page, history.more)
